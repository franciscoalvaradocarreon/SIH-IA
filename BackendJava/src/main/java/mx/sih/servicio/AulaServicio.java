package mx.sih.servicio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.AulaCrearDTO;
import mx.sih.modelo.dto.AulaDTO;
import mx.sih.modelo.dto.AulaDetalleDTO;
import mx.sih.modelo.dto.ImportarAulasDTO;
import mx.sih.modelo.dto.ResultadoImportacionAulasDTO;
import mx.sih.modelo.entidad.Aula;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.AulaRepositorio;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AulaServicio {

    private final AulaRepositorio aulaRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final HorarioRepositorio horarioRepositorio;

    public AulaServicio(AulaRepositorio aulaRepositorio,
                        SemestreRepositorio semestreRepositorio,
                        TurnoRepositorio turnoRepositorio,
                        AsignacionRepositorio asignacionRepositorio,
                        HorarioRepositorio horarioRepositorio) {
        this.aulaRepositorio = aulaRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.asignacionRepositorio = asignacionRepositorio;
        this.horarioRepositorio = horarioRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<AulaDTO> listarAulas(Pageable pageable, String busqueda,
                                      Long semestreId, Long turnoId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        Page<Aula> pagina = aulaRepositorio.buscarPorEscuelaYFiltros(
                escuelaId, busquedaNormalizada, semestreId, turnoId, pageable);
        return pagina.map(this::toDTO);
    }

    public AulaDetalleDTO obtenerAula(Long id) {
        Long escuelaId = getEscuelaId();
        Aula aula = aulaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Aula no encontrada con ID: " + id));
        return toDetalleDTO(aula);
    }

    @Transactional
    public AulaDTO crearAula(AulaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        validarCoherenciaSemestreTurno(semestre, turno);

        if (aulaRepositorio.existsByNombreAndSemestreIdAndTurnoId(
                dto.getNombre(), semestre.getSemestreId(), turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "AULA_NOMBRE_DUPLICADO",
                    "Ya existe un aula con el nombre '" + dto.getNombre() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Aula aula = new Aula();
        aula.setEscuela(escuela);
        aula.setNombre(dto.getNombre().toUpperCase());
        aula.setEdificio(dto.getEdificio() != null ? dto.getEdificio().toUpperCase() : null);
        aula.setPiso(dto.getPiso() != null ? dto.getPiso().toUpperCase() : null);
        aula.setDescripcion(dto.getDescripcion());
        aula.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        aula.setTaller(dto.getTaller() != null ? dto.getTaller() : false);
        aula.setSemestre(semestre);
        aula.setTurno(turno);   // 🔥

        Aula guardada = aulaRepositorio.save(aula);
        return toDTO(guardada);
    }

    @Transactional
    public AulaDTO actualizarAula(Long id, AulaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        Aula aula = aulaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Aula no encontrada con ID: " + id));

        validarCoherenciaSemestreTurno(semestre, turno);

        if (aulaRepositorio.existsByNombreAndSemestreIdAndTurnoIdAndIdNot(
                dto.getNombre(), semestre.getSemestreId(),
                turno.getTurnoId(), id)) {
            throw new NegocioExcepcion(
                    "AULA_NOMBRE_DUPLICADO",
                    "Ya existe otra aula con el nombre '" + dto.getNombre() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        aula.setNombre(dto.getNombre().toUpperCase());
        aula.setEdificio(dto.getEdificio() != null ? dto.getEdificio().toUpperCase() : null);
        aula.setPiso(dto.getPiso() != null ? dto.getPiso().toUpperCase() : null);
        aula.setDescripcion(dto.getDescripcion());
        if (dto.getActivo() != null) {
            aula.setActivo(dto.getActivo());
        }
        aula.setTaller(dto.getTaller() != null ? dto.getTaller() : false);
        aula.setSemestre(semestre);
        aula.setTurno(turno);   // 🔥

        Aula actualizada = aulaRepositorio.save(aula);
        return toDTO(actualizada);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Aula aula = aulaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Aula no encontrada con ID: " + id));
        aula.setActivo(activo);
        aulaRepositorio.save(aula);
    }

    @Transactional
    public void eliminarAula(Long id) {
        Long escuelaId = getEscuelaId();

        Aula aula = aulaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Aula no encontrada con ID: " + id));

        // 1. Verificar horarios generados (dependencia más fuerte)
        long horarios = horarioRepositorio.countByAulaId(id);
        if (horarios > 0) {
            throw new NegocioExcepcion(
                    "AULA_EN_USO",
                    "No se puede eliminar el aula '" + aula.getNombre() +
                    "' porque está siendo usada en " + horarios +
                    " bloque(s) de horario. Elimina primero los horarios " +
                    "o desactiva el aula en lugar de eliminarla."
            );
        }

        // 2. Verificar asignaciones (materias asignadas al aula)
        long asignaciones = asignacionRepositorio.countByAulaId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "AULA_EN_USO",
                    "No se puede eliminar el aula '" + aula.getNombre() +
                    "' porque tiene " + asignaciones +
                    " asignación(es) activa(s) en materias. Elimina primero las " +
                    "asignaciones o desactiva el aula en lugar de eliminarla."
            );
        }

        // 3. Sin dependencias: eliminar
        aulaRepositorio.delete(aula);
    }

    // ============================================================
    // IMPORTACION DESDE OTRO SEMESTRE
    // ============================================================

    /**
     * Trae las aulas de OTRO semestre de la misma escuela al semestre de destino.
     *
     * SOLO la tabla de aulas: nombre, edificio, piso, descripcion, estado y si es taller. No se copia
     * ninguna otra tabla (la disponibilidad, los horarios y las asignaciones son de cada semestre y
     * no se tocan).
     *
     * El detalle que manda aqui, igual que en los demas catalogos: sih.aulas.semestre_id y
     * sih.aulas.turno_id son NOT NULL y el turno tiene que ser del MISMO semestre que el aula (ver
     * validarCoherenciaSemestreTurno). Cada aula que se trae necesita encontrar SU turno en el
     * semestre de destino, y se busca por NOMBRE: el turno_id es de otro semestre.
     *
     * Decisiones:
     *
     *  1. Se traen TODAS las aulas del semestre de origen, de todos sus turnos.
     *  2. Si el destino NO tiene un turno con ese nombre, el aula se SALTA y se informa. Nunca se
     *     inventa un turno ni se apunta a uno de otro semestre.
     *  3. Si el destino YA tiene un aula con ese NOMBRE en ese turno, se SALTA y se informa. El
     *     nombre es lo que identifica al aula: el indice unico es (escuela, semestre, turno, nombre),
     *     la misma comprobacion que usa crearAula.
     *  4. El estado (activo/inactivo) y la marca de taller se copian tal cual.
     *  5. Todo va en UNA transaccion: si algo falla a mitad, no queda un semestre copiado a medias.
     */
    @Transactional
    public ResultadoImportacionAulasDTO importarAulas(ImportarAulasDTO dto) {
        Long escuelaId = getEscuelaId();

        if (dto.getSemestreOrigenId().equals(dto.getSemestreDestinoId())) {
            throw new NegocioExcepcion("El semestre de origen y el de destino son el mismo");
        }

        Semestre origen = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreOrigenId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No se encontro el semestre de origen"));
        Semestre destino = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreDestinoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No se encontro el semestre de destino"));

        // El TURNO que el usuario tiene seleccionado en la pantalla manda el alcance: solo se traen
        // las aulas del turno del ORIGEN que se llame igual.
        Turno turnoDestino = resolverTurnoDeLaImportacion(dto.getTurnoId(), destino, escuelaId);
        String nombreTurno = turnoDestino.getNombre().trim().toUpperCase(Locale.ROOT);

        List<Aula> fuente = aulaRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, origen.getSemestreId())
                .stream()
                .filter(a -> a.getTurno() != null
                        && a.getTurno().getNombre() != null
                        && a.getTurno().getNombre().trim().toUpperCase(Locale.ROOT).equals(nombreTurno))
                .toList();
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + turnoDestino.getNombre()
                    + "' del semestre '" + origen.getNombre()
                    + "' no tiene aulas que traer");
        }

        int copiadas = 0;
        List<String> omitidos = new ArrayList<>();

        for (Aula original : fuente) {
            String nombre = original.getNombre();

            if (aulaRepositorio.existsByNombreAndSemestreIdAndTurnoId(
                    nombre, destino.getSemestreId(), turnoDestino.getTurnoId())) {
                omitidos.add(nombre + " (ya existia en '" + turnoDestino.getNombre() + "')");
                continue;
            }

            Aula copia = new Aula();
            copia.setNombre(nombre);
            copia.setEdificio(original.getEdificio());
            copia.setPiso(original.getPiso());
            copia.setDescripcion(original.getDescripcion());
            copia.setActivo(original.getActivo());
            copia.setTaller(original.getTaller());

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);
            copia.setEscuela(escuela);
            copia.setSemestre(destino);
            copia.setTurno(turnoDestino);

            aulaRepositorio.save(copia);
            copiadas++;
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se trajeron ").append(copiadas)
                .append(copiadas == 1 ? " aula" : " aulas")
                .append(" del turno '").append(turnoDestino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (!omitidos.isEmpty()) {
            mensaje.append(" Se saltaron ").append(omitidos.size()).append(": ")
                    .append(String.join("; ", omitidos)).append(".");
        }

        return new ResultadoImportacionAulasDTO(copiadas, omitidos, mensaje.toString());
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /**
     * Resuelve el turno de destino de una importacion desde otro semestre.
     *
     * El turno lo elige el usuario en la pantalla y TIENE que ser del semestre de destino: sin esta
     * comprobacion se podria importar hacia un turno de otro semestre y romper la coherencia
     * semestre-turno que valida crearAula.
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

    private AulaDTO toDTO(Aula aula) {
        AulaDTO dto = new AulaDTO();
        dto.setId(aula.getAulaId());
        dto.setNombre(aula.getNombre());
        dto.setEdificio(aula.getEdificio());
        dto.setPiso(aula.getPiso());
        dto.setDescripcion(aula.getDescripcion());
        dto.setActivo(aula.getActivo());
        dto.setTaller(aula.getTaller());
        if (aula.getSemestre() != null) {
            dto.setSemestreId(aula.getSemestre().getSemestreId());
            dto.setSemestreNombre(aula.getSemestre().getNombre());
        }
        if (aula.getTurno() != null) {
            dto.setTurnoId(aula.getTurno().getTurnoId());
            dto.setTurnoNombre(aula.getTurno().getNombre());
        }
        return dto;
    }

    private AulaDetalleDTO toDetalleDTO(Aula aula) {
        AulaDetalleDTO dto = new AulaDetalleDTO();
        dto.setId(aula.getAulaId());
        dto.setNombre(aula.getNombre());
        dto.setEdificio(aula.getEdificio());
        dto.setPiso(aula.getPiso());
        dto.setDescripcion(aula.getDescripcion());
        dto.setActivo(aula.getActivo());
        dto.setTaller(aula.getTaller());
        if (aula.getSemestre() != null) {
            dto.setSemestreId(aula.getSemestre().getSemestreId());
            dto.setSemestreNombre(aula.getSemestre().getNombre());
        }
        if (aula.getTurno() != null) {
            dto.setTurnoId(aula.getTurno().getTurnoId());
            dto.setTurnoNombre(aula.getTurno().getNombre());
        }
        return dto;
    }
}