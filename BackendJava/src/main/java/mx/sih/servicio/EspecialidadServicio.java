package mx.sih.servicio;

import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.EspecialidadCrearDTO;
import mx.sih.modelo.dto.EspecialidadDTO;
import mx.sih.modelo.entidad.Especialidad;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.EspecialidadRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import static mx.sih.seguridad.contexto.EscuelaContexto.getEscuelaId;
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