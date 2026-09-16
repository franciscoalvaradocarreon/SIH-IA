package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.AulaCrearDTO;
import mx.sih.modelo.dto.AulaDTO;
import mx.sih.modelo.dto.AulaDetalleDTO;
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
    // HELPERS
    // ============================================================

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