package mx.sih.servicio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.ImportarMateriasDTO;
import mx.sih.modelo.dto.MateriaCrearDTO;
import mx.sih.modelo.dto.MateriaDTO;
import mx.sih.modelo.dto.MateriaDetalleDTO;
import mx.sih.modelo.dto.ResultadoImportacionMateriasDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Materia;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.MateriaRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MateriaServicio {

    private final MateriaRepositorio materiaRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final HorarioRepositorio horarioRepositorio;

    public MateriaServicio(MateriaRepositorio materiaRepositorio,
                           SemestreRepositorio semestreRepositorio,
                           TurnoRepositorio turnoRepositorio,
                           AsignacionRepositorio asignacionRepositorio,
                           HorarioRepositorio horarioRepositorio) {
        this.materiaRepositorio = materiaRepositorio;
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

    public Page<MateriaDTO> listarMaterias(Pageable pageable, String busqueda,
                                            Long semestreId, Long turnoId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        Page<Materia> pagina = materiaRepositorio.buscarPorEscuelaYFiltros(
                escuelaId, busquedaNormalizada, semestreId, turnoId, pageable);
        return pagina.map(this::toDTO);
    }

    public MateriaDetalleDTO obtenerMateria(Long id) {
        Long escuelaId = getEscuelaId();
        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));
        return toDetalleDTO(materia);
    }

    @Transactional
    public MateriaDTO crearMateria(MateriaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        // 🔥 Coherencia: turno y semestre deben coincidir
        validarCoherenciaSemestreTurno(semestre, turno);

        // 🔥 Unicidad: clave por (semestre, turno)
        if (materiaRepositorio.existsByClaveAndSemestreIdAndTurnoId(
                dto.getClave(), semestre.getSemestreId(), turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "MATERIA_CLAVE_DUPLICADA",
                    "Ya existe una materia con la clave '" + dto.getClave() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Materia materia = new Materia();
        materia.setNombre(dto.getNombre());
        materia.setClave(dto.getClave());
        materia.setDescripcion(dto.getDescripcion());
        materia.setCreditos(dto.getCreditos());
        materia.setColorHex(dto.getColorHex());
        materia.setHorasSemana(dto.getHorasSemana());
        materia.setEscuela(escuela);
        materia.setSemestre(semestre);
        materia.setTurno(turno);   // 🔥
        materia.setActivo(true);

        Materia guardada = materiaRepositorio.save(materia);
        return toDTO(guardada);
    }

    @Transactional
    public MateriaDTO actualizarMateria(Long id, MateriaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));

        validarCoherenciaSemestreTurno(semestre, turno);

        if (materiaRepositorio.existsByClaveAndSemestreIdAndTurnoIdAndIdNot(
                dto.getClave(), semestre.getSemestreId(),
                turno.getTurnoId(), id)) {
            throw new NegocioExcepcion(
                    "MATERIA_CLAVE_DUPLICADA",
                    "Ya existe otra materia con la clave '" + dto.getClave() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        materia.setNombre(dto.getNombre());
        materia.setClave(dto.getClave());
        materia.setDescripcion(dto.getDescripcion());
        materia.setCreditos(dto.getCreditos());
        materia.setColorHex(dto.getColorHex());
        materia.setHorasSemana(dto.getHorasSemana());
        materia.setSemestre(semestre);
        materia.setTurno(turno);   // 🔥
        // Nota: NO tocamos activo aquí. Si quieres respetar el form, añade:
        // if (dto.getActivo() != null) materia.setActivo(dto.getActivo());

        Materia actualizada = materiaRepositorio.save(materia);
        return toDTO(actualizada);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));
        materia.setActivo(activo);
        materiaRepositorio.save(materia);
    }

    @Transactional
    public void eliminarMateria(Long id) {
        Long escuelaId = getEscuelaId();

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));

        // 1. Verificar horarios generados
        long horarios = horarioRepositorio.countByMateriaId(id);
        if (horarios > 0) {
            throw new NegocioExcepcion(
                    "MATERIA_EN_USO",
                    "No se puede eliminar la materia '" + materia.getNombre() +
                    "' porque está siendo usada en " + horarios +
                    " bloque(s) de horario. Elimina primero los horarios " +
                    "o desactiva la materia en lugar de eliminarla."
            );
        }

        // 2. Verificar asignaciones
        long asignaciones = asignacionRepositorio.countByMateriaId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "MATERIA_EN_USO",
                    "No se puede eliminar la materia '" + materia.getNombre() +
                    "' porque está siendo usada en " + asignaciones +
                    " asignación(es) activa(s). Elimina primero las asignaciones " +
                    "o desactiva la materia en lugar de eliminarla."
            );
        }

        // 3. Sin dependencias: eliminar
        materiaRepositorio.delete(materia);
    }

    // ============================================================
    // IMPORTACION DESDE OTRO SEMESTRE
    // ============================================================

    /**
     * Trae las materias de OTRO semestre de la misma escuela al semestre de destino.
     *
     * SOLO la tabla de materias: nombre, clave, descripcion, creditos, color, horas y estado. No se
     * copia ninguna otra tabla (asignaciones y horarios son de cada semestre y no se tocan).
     *
     * El detalle que manda aqui, igual que en especialidades y maestros: sih.materias.semestre_id y
     * sih.materias.turno_id son NOT NULL y el turno tiene que ser del MISMO semestre que la materia
     * (ver validarCoherenciaSemestreTurno). Cada materia que se trae necesita encontrar SU turno en
     * el semestre de destino, y se busca por NOMBRE: el turno_id es de otro semestre.
     *
     * Decisiones:
     *
     *  1. Se traen TODAS las materias del semestre de origen.
     *  2. Si el destino NO tiene un turno con ese nombre, la materia se SALTA y se informa. Nunca se
     *     inventa un turno ni se apunta a uno de otro semestre.
     *  3. Si el destino YA tiene esa CLAVE en ese turno, se SALTA y se informa. La clave es lo que
     *     identifica a una materia (la unicidad de la tabla es escuela+semestre+turno+clave), no el
     *     nombre: dos materias pueden llamarse distinto con la misma clave o al reves.
     *  4. El estado (activo/inactivo) se copia tal cual, para no revivir materias que estaban dadas
     *     de baja en el semestre de origen.
     *  5. Todo va en UNA transaccion: si algo falla a mitad, no queda un semestre copiado a medias.
     */
    @Transactional
    public ResultadoImportacionMateriasDTO importarMaterias(ImportarMateriasDTO dto) {
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
        // las materias del turno del ORIGEN que se llame igual.
        Turno turnoDestino = resolverTurnoDeLaImportacion(dto.getTurnoId(), destino, escuelaId);
        String nombreTurno = turnoDestino.getNombre().trim().toUpperCase(Locale.ROOT);

        List<Materia> fuente = materiaRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, origen.getSemestreId())
                .stream()
                .filter(m -> m.getTurno() != null
                        && m.getTurno().getNombre() != null
                        && m.getTurno().getNombre().trim().toUpperCase(Locale.ROOT).equals(nombreTurno))
                .toList();
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + turnoDestino.getNombre()
                    + "' del semestre '" + origen.getNombre()
                    + "' no tiene materias que traer");
        }

        int copiadas = 0;
        List<String> omitidos = new ArrayList<>();

        for (Materia original : fuente) {
            String etiqueta = original.getNombre() + " (" + original.getClave() + ")";

            if (materiaRepositorio.existsByClaveAndSemestreIdAndTurnoId(
                    original.getClave(),
                    destino.getSemestreId(),
                    turnoDestino.getTurnoId())) {
                omitidos.add(etiqueta + " (la clave ya existia en '" + turnoDestino.getNombre() + "')");
                continue;
            }

            Materia copia = new Materia();
            copia.setNombre(original.getNombre());
            copia.setClave(original.getClave());
            copia.setDescripcion(original.getDescripcion());
            copia.setCreditos(original.getCreditos());
            copia.setColorHex(original.getColorHex());
            copia.setHorasSemana(original.getHorasSemana());
            copia.setActivo(original.getActivo());

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);
            copia.setEscuela(escuela);
            copia.setSemestre(destino);
            copia.setTurno(turnoDestino);

            materiaRepositorio.save(copia);
            copiadas++;
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se trajeron ").append(copiadas)
                .append(copiadas == 1 ? " materia" : " materias")
                .append(" del turno '").append(turnoDestino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (!omitidos.isEmpty()) {
            mensaje.append(" Se saltaron ").append(omitidos.size()).append(": ")
                    .append(String.join("; ", omitidos)).append(".");
        }

        return new ResultadoImportacionMateriasDTO(copiadas, omitidos, mensaje.toString());
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /**
     * Resuelve el turno de destino de una importacion desde otro semestre.
     *
     * El turno lo elige el usuario en la pantalla y TIENE que ser del semestre de destino: sin esta
     * comprobacion se podria importar hacia un turno de otro semestre y romper la coherencia
     * semestre-turno que valida crearMateria.
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

    private MateriaDTO toDTO(Materia materia) {
        MateriaDTO dto = new MateriaDTO();
        dto.setId(materia.getMateriaId());
        dto.setNombre(materia.getNombre());
        dto.setClave(materia.getClave());
        dto.setDescripcion(materia.getDescripcion());
        dto.setCreditos(materia.getCreditos());
        dto.setHorasSemana(materia.getHorasSemana());
        dto.setColorHex(materia.getColorHex());
        dto.setActivo(materia.getActivo());

        if (materia.getSemestre() != null) {
            dto.setSemestreId(materia.getSemestre().getSemestreId());
            dto.setSemestreNombre(materia.getSemestre().getNombre());
        }
        if (materia.getTurno() != null) {
            dto.setTurnoId(materia.getTurno().getTurnoId());
            dto.setTurnoNombre(materia.getTurno().getNombre());
        }
        return dto;
    }

    private MateriaDetalleDTO toDetalleDTO(Materia materia) {
        MateriaDetalleDTO dto = new MateriaDetalleDTO();
        dto.setId(materia.getMateriaId());
        dto.setNombre(materia.getNombre());
        dto.setClave(materia.getClave());
        dto.setDescripcion(materia.getDescripcion());
        dto.setCreditos(materia.getCreditos());
        dto.setColorHex(materia.getColorHex());
        dto.setHorasSemana(materia.getHorasSemana());
        dto.setActivo(materia.getActivo());

        if (materia.getSemestre() != null) {
            dto.setSemestreId(materia.getSemestre().getSemestreId());
            dto.setSemestreNombre(materia.getSemestre().getNombre());
        }
        if (materia.getTurno() != null) {
            dto.setTurnoId(materia.getTurno().getTurnoId());
            dto.setTurnoNombre(materia.getTurno().getNombre());
        }
        return dto;
    }
}