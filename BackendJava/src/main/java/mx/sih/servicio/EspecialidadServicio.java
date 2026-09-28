package mx.sih.servicio;

import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.EspecialidadCrearDTO;
import mx.sih.modelo.dto.EspecialidadDTO;
import mx.sih.modelo.dto.ImportarEspecialidadesDTO;
import mx.sih.modelo.dto.ResultadoImportacionEspecialidadesDTO;
import mx.sih.modelo.entidad.Especialidad;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.EspecialidadRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import static mx.sih.seguridad.contexto.EscuelaContexto.getEscuelaId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EspecialidadServicio {

    private final EspecialidadRepositorio especialidadRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;

    public EspecialidadServicio(EspecialidadRepositorio especialidadRepositorio,
                                SemestreRepositorio semestreRepositorio,
                                TurnoRepositorio turnoRepositorio) {
        this.especialidadRepositorio = especialidadRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
    }

    public Page<EspecialidadDTO> listarEspecialidades(Pageable pageable,
                                                      String busqueda,
                                                      Long semestreId,
                                                      Long turnoId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        Page<Especialidad> pagina = especialidadRepositorio.buscarPorEscuelaYFiltros(
                escuelaId, busquedaNormalizada, semestreId, turnoId, pageable);

        return pagina.map(this::toDTO);
    }

    public EspecialidadDTO obtenerEspecialidad(Long id) {
        Long escuelaId = getEscuelaId();
        Especialidad especialidad = especialidadRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Especialidad no encontrada con ID: " + id));
        return toDTO(especialidad);
    }

    @Transactional
    public EspecialidadDTO crearEspecialidad(EspecialidadCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = resolverSemestre(dto.getSemestreId(), escuelaId);
        Turno turno = resolverTurno(dto.getTurnoId(), escuelaId);

        // 🔥 VALIDACIÓN DE COHERENCIA (opción B)
        // El turno debe pertenecer al mismo semestre que la especialidad.
        validarCoherenciaSemestreTurno(semestre, turno);

        // 🔥 VALIDACIÓN DE UNICIDAD (refleja constraint unique del DDL)
        if (especialidadRepositorio.existsBySemestreTurnoNombre(
                semestre.getSemestreId(),
                turno.getTurnoId(),
                dto.getNombre())) {
            throw new NegocioExcepcion(
                    "Ya existe una especialidad con el nombre '" + dto.getNombre() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'");
        }

        Especialidad especialidad = new Especialidad();
        especialidad.setNombre(dto.getNombre());
        especialidad.setSemestre(semestre);
        especialidad.setTurno(turno);
        Especialidad guardada = especialidadRepositorio.save(especialidad);

        return toDTO(guardada);
    }

    @Transactional
    public EspecialidadDTO actualizarEspecialidad(Long id, EspecialidadCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Especialidad especialidad = especialidadRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Especialidad no encontrada con ID: " + id));

        Semestre semestre = resolverSemestre(dto.getSemestreId(), escuelaId);
        Turno turno = resolverTurno(dto.getTurnoId(), escuelaId);

        validarCoherenciaSemestreTurno(semestre, turno);

        // Validar duplicado excluyendo el propio registro
        if (especialidadRepositorio.existsBySemestreTurnoNombreAndIdNot(
                semestre.getSemestreId(),
                turno.getTurnoId(),
                dto.getNombre(),
                id)) {
            throw new NegocioExcepcion(
                    "Ya existe otra especialidad con el nombre '" + dto.getNombre() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'");
        }

        especialidad.setNombre(dto.getNombre());
        especialidad.setSemestre(semestre);
        especialidad.setTurno(turno);

        Especialidad actualizada = especialidadRepositorio.save(especialidad);
        return toDTO(actualizada);
    }

    @Transactional
    public void eliminarEspecialidad(Long id) {
        Long escuelaId = getEscuelaId();

        Especialidad especialidad = especialidadRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Especialidad no encontrada con ID: " + id));

        try {
            especialidadRepositorio.delete(especialidad);
            especialidadRepositorio.flush();
        } catch (DataIntegrityViolationException e) {
            System.err.println("❌ Error de integridad: " +
                    e.getMostSpecificCause().getMessage());
            throw MensajeErrorUtil.crearExcepcionDependencia("la especialidad", e);
        }
    }

    /**
     * Trae las especialidades de OTRO semestre de la misma escuela al semestre de destino.
     *
     * SOLO la tabla de especialidades: el nombre y el turno al que pertenece. No se copia ninguna
     * otra tabla (los grupos, los horarios y las asignaciones son otra cosa y no se tocan).
     *
     * El detalle que manda aqui: sih.especialidad.turno_id es NOT NULL y el turno TIENE que ser del
     * mismo semestre que la especialidad (ver validarCoherenciaSemestreTurno). O sea que cada
     * especialidad que se trae necesita encontrar SU turno en el semestre de destino, y se busca por
     * NOMBRE: es el unico dato que identifica un turno entre semestres, porque el turno_id es de
     * otro semestre y no sirve.
     *
     * Decisiones:
     *
     *  1. Se traen TODAS las especialidades del semestre de origen: en la pantalla se elige el
     *     semestre, no especialidad por especialidad.
     *  2. Si el destino NO tiene un turno con ese nombre, la especialidad se SALTA y se informa.
     *     Nunca se inventa un turno ni se apunta a un turno de otro semestre: eso romperia la
     *     coherencia semestre-turno (y el listado por semestre mostraria basura).
     *  3. Si el destino YA tiene esa especialidad en ese turno, se SALTA y se informa. No se
     *     sobrescribe ni se duplica: se pidio "traer lo que falta", no "reemplazar lo que hay".
     *  4. Todo va en UNA transaccion: si algo falla a mitad, no queda un semestre copiado a medias.
     */
    @Transactional
    public ResultadoImportacionEspecialidadesDTO importarEspecialidades(ImportarEspecialidadesDTO dto) {
        Long escuelaId = getEscuelaId();

        if (dto.getSemestreOrigenId().equals(dto.getSemestreDestinoId())) {
            throw new NegocioExcepcion("El semestre de origen y el de destino son el mismo");
        }

        Semestre origen = resolverSemestre(dto.getSemestreOrigenId(), escuelaId);
        Semestre destino = resolverSemestre(dto.getSemestreDestinoId(), escuelaId);

        // El TURNO que el usuario tiene seleccionado en la pantalla manda el alcance: solo se traen
        // las especialidades del turno del ORIGEN que se llame igual.
        Turno turnoDestino = resolverTurnoDeLaImportacion(dto.getTurnoId(), destino, escuelaId);
        String nombreTurno = turnoDestino.getNombre().trim().toUpperCase(Locale.ROOT);

        List<Especialidad> fuente = especialidadRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, origen.getSemestreId())
                .stream()
                .filter(e -> e.getTurno() != null
                        && e.getTurno().getNombre() != null
                        && e.getTurno().getNombre().trim().toUpperCase(Locale.ROOT).equals(nombreTurno))
                .toList();
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + turnoDestino.getNombre()
                    + "' del semestre '" + origen.getNombre()
                    + "' no tiene especialidades que traer");
        }

        int copiadas = 0;
        List<String> omitidos = new ArrayList<>();

        for (Especialidad original : fuente) {
            String nombre = original.getNombre();

            if (especialidadRepositorio.existsBySemestreTurnoNombre(
                    destino.getSemestreId(), turnoDestino.getTurnoId(), nombre)) {
                omitidos.add(nombre + " (ya existia en '" + turnoDestino.getNombre() + "')");
                continue;
            }

            Especialidad copia = new Especialidad();
            copia.setNombre(nombre);
            copia.setSemestre(destino);
            copia.setTurno(turnoDestino);
            especialidadRepositorio.save(copia);
            copiadas++;
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se trajeron ").append(copiadas)
                .append(copiadas == 1 ? " especialidad" : " especialidades")
                .append(" del turno '").append(turnoDestino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (!omitidos.isEmpty()) {
            mensaje.append(" Se saltaron ").append(omitidos.size()).append(": ")
                    .append(String.join("; ", omitidos)).append(".");
        }

        return new ResultadoImportacionEspecialidadesDTO(copiadas, omitidos, mensaje.toString());
    }

    // ============================================================
    // HELPERS PRIVADOS
    // ============================================================

    private Semestre resolverSemestre(Long semestreId, Long escuelaId) {
        return semestreRepositorio
                .findByIdAndEscuelaId(semestreId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Semestre no encontrado o no pertenece a esta escuela"));
    }

    private Turno resolverTurno(Long turnoId, Long escuelaId) {
        return turnoRepositorio
                .findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Turno no encontrado o no pertenece a esta escuela"));
    }

    /**
     * Valida que el semestre del turno coincida con el semestre
     * al que se quiere asignar la especialidad.
     *
     * Sin esta validación, un cliente malicioso o un bug podrían crear
     * especialidades de un semestre apuntando a turnos de otro.
     */
    private void validarCoherenciaSemestreTurno(Semestre semestre, Turno turno) {
        if (turno.getSemestre() == null) {
            throw new NegocioExcepcion(
                    "El turno seleccionado no tiene semestre asignado");
        }
        if (!turno.getSemestre().getSemestreId().equals(semestre.getSemestreId())) {
            throw new NegocioExcepcion(
                    "El turno '" + turno.getNombre() +
                    "' pertenece al semestre '" + turno.getSemestre().getNombre() +
                    "', no a '" + semestre.getNombre() + "'");
        }
    }

    /**
     * Resuelve el turno de destino de una importacion desde otro semestre.
     *
     * El turno lo elige el usuario en la pantalla y TIENE que ser del semestre de destino: sin esta
     * comprobacion se podria importar hacia un turno de otro semestre y romper la coherencia
     * semestre-turno que valida crearEspecialidad.
     */
    private Turno resolverTurnoDeLaImportacion(Long turnoId, Semestre destino, Long escuelaId) {
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Turno no encontrado o no pertenece a esta escuela"));
        if (turno.getSemestre() == null
                || !turno.getSemestre().getSemestreId().equals(destino.getSemestreId())) {
            throw new NegocioExcepcion("El turno '" + turno.getNombre()
                    + "' no pertenece al semestre '" + destino.getNombre() + "'");
        }
        return turno;
    }

    private EspecialidadDTO toDTO(Especialidad especialidad) {
        EspecialidadDTO dto = new EspecialidadDTO();
        dto.setId(especialidad.getEspecialidadId());
        dto.setNombre(especialidad.getNombre());

        if (especialidad.getSemestre() != null) {
            dto.setSemestreId(especialidad.getSemestre().getSemestreId());
            dto.setSemestreNombre(especialidad.getSemestre().getNombre());
        }
        if (especialidad.getTurno() != null) {
            dto.setTurnoId(especialidad.getTurno().getTurnoId());
            dto.setTurnoNombre(especialidad.getTurno().getNombre());
        }
        return dto;
    }
}