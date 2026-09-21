package mx.sih.servicio;

import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.TurnoCrearDTO;
import mx.sih.modelo.dto.TurnoDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.AulaRepositorio;
import mx.sih.repositorio.EscuelaRepositorio;
import mx.sih.repositorio.EspecialidadRepositorio;
import mx.sih.repositorio.GrupoRepositorio;
import mx.sih.repositorio.MaestroRepositorio;
import mx.sih.repositorio.MateriaRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoHorarioRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurnoServicio {

    private final TurnoRepositorio turnoRepositorio;
    private final EscuelaRepositorio escuelaRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final MaestroRepositorio maestroRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final MateriaRepositorio materiaRepositorio;
    private final EspecialidadRepositorio especialidadRepositorio;
    private final AulaRepositorio aulaRepositorio;

    public TurnoServicio(TurnoRepositorio turnoRepositorio,
                         EscuelaRepositorio escuelaRepositorio,
                         SemestreRepositorio semestreRepositorio,
                         TurnoHorarioRepositorio turnoHorarioRepositorio,
                         GrupoRepositorio grupoRepositorio,
                         MaestroRepositorio maestroRepositorio,
                         AsignacionRepositorio asignacionRepositorio,
                         MateriaRepositorio materiaRepositorio,
                         EspecialidadRepositorio especialidadRepositorio,
                         AulaRepositorio aulaRepositorio) {
        this.turnoRepositorio = turnoRepositorio;
        this.escuelaRepositorio = escuelaRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.maestroRepositorio = maestroRepositorio;
        this.asignacionRepositorio = asignacionRepositorio;
        this.materiaRepositorio = materiaRepositorio;
        this.especialidadRepositorio = especialidadRepositorio;
        this.aulaRepositorio = aulaRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<TurnoDTO> listarTurnos(Pageable pageable, String busqueda, Long semestreId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<Turno> pagina = turnoRepositorio.findByEscuelaIdAndSemestreIdAndBusqueda(
                escuelaId, semestreId, busquedaNormalizada, pageable);
        return pagina.map(this::toDTO);
    }

    public TurnoDTO obtenerTurno(Long id) {
        Long escuelaId = getEscuelaId();
        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));
        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }
        return toDTO(turno);
    }

    @Transactional
    public TurnoDTO crearTurno(TurnoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Escuela escuela = escuelaRepositorio.findById(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada"));

        Semestre semestre = null;
        if (dto.getSemestreId() != null) {
            semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));
        }

        // Nombre ÚNICO por escuela. Antes solo se validaba al ACTUALIZAR, así que se
        // podían crear dos "MATUTINO" en la misma escuela: el nombre es la referencia
        // con la que el usuario identifica el turno y con la que se generan horarios.
        // Además se normaliza a mayúsculas igual que en actualizarTurno (antes se
        // guardaba "matutino" o "MATUTINO" según el formulario, y la comparación
        // ignoreCase de la actualización no evitaba duplicados con distinto formato).
        String nombreNormalizado = dto.getNombre().trim().toUpperCase();
        if (turnoRepositorio.existsByEscuelaIdAndSemestreIdAndNombreIgnoreCase(
                escuelaId, dto.getSemestreId(), nombreNormalizado)) {
            throw new NegocioExcepcion("TURNO_NOMBRE_DUPLICADO",
                    "Ya existe un turno con el nombre '" + nombreNormalizado + "' en esta escuela para ese semestre");
        }

        Turno turno = new Turno();
        turno.setEscuela(escuela);
        turno.setNombre(nombreNormalizado);
        turno.setDescripcion(dto.getDescripcion());
        turno.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        turno.setSemestre(semestre);

        Turno guardado = turnoRepositorio.save(turno);
        return toDTO(guardado);
    }

    @Transactional
    public TurnoDTO actualizarTurno(Long id, TurnoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        if (!turno.getNombre().equalsIgnoreCase(dto.getNombre()) &&
            turnoRepositorio.existsByEscuelaIdAndNombreIgnoreCaseAndIdNot(escuelaId, dto.getNombre(), id)) {
            throw new NegocioExcepcion("Ya existe otro turno con el nombre: " + dto.getNombre() + " en esta escuela");
        }

        turno.setNombre(dto.getNombre().trim().toUpperCase());
        turno.setDescripcion(dto.getDescripcion());

        // El DTO trae semestreId y activo, pero la actualización los ignoraba: el
        // usuario cambiaba el semestre en el formulario y no se guardaba (parecía
        // que sí, porque la lista se recargaba). Se exige > 0 para no interpretar
        // "sin selección" (0) como un cambio, y se valida que el semestre sea de
        // la escuela activa.
        if (dto.getSemestreId() != null && dto.getSemestreId() > 0) {
            Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));
            turno.setSemestre(semestre);
        }
        if (dto.getActivo() != null) {
            turno.setActivo(dto.getActivo());
        }

        Turno actualizado = turnoRepositorio.save(turno);
        return toDTO(actualizado);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        turno.setActivo(activo);
        turnoRepositorio.save(turno);
    }

    /**
     * Eliminar un turno.
     *
     * Un turno está referenciado por múltiples entidades:
     *   · turno_horario (bloques horarios del turno)
     *   · grupos (grupos asignados al turno)
     *   · maestros (maestros asignados al turno)
     *   · asignaciones (asignaciones que usan el turno)
     *   · materias (materias del turno)
     *   · especialidades (especialidades del turno)
     *   · aulas (aulas del turno)
     *
     * Se pre-verifican las 7 y se devuelve un mensaje claro indicando cuál
     * hay que resolver primero. Si por alguna razón una dependencia no
     * prevista llega a PostgreSQL, MensajeErrorUtil extrae la tabla del
     * error y genera un mensaje consistente.
     */
    @Transactional
    public void eliminarTurno(Long id) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        // 1. Bloques horarios
        long bloques = turnoHorarioRepositorio.countByTurnoId(id);
        if (bloques > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + bloques +
                    " bloque(s) horario(s) configurado(s). Elimínalos primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 2. Grupos
        long grupos = grupoRepositorio.countByTurnoId(id);
        if (grupos > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + grupos +
                    " grupo(s) asignado(s). Elimínalos primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 3. Maestros
        long maestros = maestroRepositorio.countByTurnoId(id);
        if (maestros > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + maestros +
                    " maestro(s) asignado(s). Elimínalos primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 4. Asignaciones
        long asignaciones = asignacionRepositorio.countByTurnoId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + asignaciones +
                    " asignación(es). Elimínalas primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 5. Materias
        long materias = materiaRepositorio.countByTurnoId(id);
        if (materias > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + materias +
                    " materia(s) asignada(s). Elimínalas primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 6. Especialidades
        long especialidades = especialidadRepositorio.countByTurnoId(id);
        if (especialidades > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + especialidades +
                    " especialidad(es) asignada(s). Elimínalas primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // 7. Aulas
        long aulas = aulaRepositorio.countByTurnoId(id);
        if (aulas > 0) {
            throw new NegocioExcepcion(
                    "TURNO_EN_USO",
                    "No se puede eliminar el turno '" + turno.getNombre() +
                    "' porque tiene " + aulas +
                    " aula(s) asignada(s). Elimínalas primero " +
                    "o desactiva el turno en lugar de eliminarlo."
            );
        }

        // Red de seguridad: si algo se escapa (dependencia no prevista),
        // PostgreSQL lo rechaza y MensajeErrorUtil construye el mensaje.
        try {
            turnoRepositorio.delete(turno);
            turnoRepositorio.flush();
        } catch (DataIntegrityViolationException e) {
            throw MensajeErrorUtil.crearExcepcionDependencia("el turno", e);
        }
    }

    private TurnoDTO toDTO(Turno turno) {
        TurnoDTO dto = new TurnoDTO();
        dto.setId(turno.getTurnoId());
        dto.setEscuelaId(turno.getEscuela().getEscuelaId());
        dto.setNombre(turno.getNombre());
        dto.setDescripcion(turno.getDescripcion());
        dto.setActivo(turno.getActivo());

        if (turno.getSemestre() != null) {
            dto.setSemestreId(turno.getSemestre().getSemestreId());
            dto.setSemestreNombre(turno.getSemestre().getNombre());
        }

        return dto;
    }
}