package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.AsignacionCrearDTO;
import mx.sih.modelo.dto.AsignacionDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsignacionServicio {

    private final AsignacionRepositorio asignacionRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final MateriaRepositorio materiaRepositorio;
    private final MaestroRepositorio maestroRepositorio;
    private final AulaRepositorio aulaRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final HorarioRepositorio horarioRepositorio;


    public AsignacionServicio(AsignacionRepositorio asignacionRepositorio,
                              GrupoRepositorio grupoRepositorio,
                              MateriaRepositorio materiaRepositorio,
                              MaestroRepositorio maestroRepositorio,
                              AulaRepositorio aulaRepositorio,
                              SemestreRepositorio semestreRepositorio,
                              TurnoRepositorio turnoRepositorio,
                              HorarioRepositorio horarioRepositorio) {
        this.asignacionRepositorio = asignacionRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.materiaRepositorio = materiaRepositorio;
        this.maestroRepositorio = maestroRepositorio;
        this.aulaRepositorio = aulaRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.horarioRepositorio = horarioRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<AsignacionDTO> listarAsignaciones(Pageable pageable,
                                                   String busqueda,
                                                   Long grupoId,
                                                   Long especialidadId,
                                                   Long turnoId,
                                                   Long maestroId,
                                                   Long semestreId) {
        Long escuelaId = getEscuelaId();

        if (semestreId == null) {
            Semestre semestre = semestreRepositorio.findSemestreActual(escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestre.getSemestreId();
        }

        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        Long grupoIdFiltro = (grupoId != null && grupoId > 0) ? grupoId : null;
        Long especialidadIdFiltro = (especialidadId != null && especialidadId > 0) ? especialidadId : null;
        Long turnoIdFiltro = (turnoId != null && turnoId > 0) ? turnoId : null;
        Long maestroIdFiltro = (maestroId != null && maestroId > 0) ? maestroId : null;

        Page<Asignacion> pagina = asignacionRepositorio.buscarPorEscuelaYSemestreYFiltros(
                escuelaId,
                semestreId,
                grupoIdFiltro,
                especialidadIdFiltro,
                turnoIdFiltro,
                maestroIdFiltro,
                busquedaNormalizada,
                pageable);

        return pagina.map(this::toDTO);
    }

    public AsignacionDTO obtenerAsignacion(Long id) {
        Long escuelaId = getEscuelaId();
        Asignacion asignacion = asignacionRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Asignación no encontrada con ID: " + id));
        return toDTO(asignacion);
    }

    @Transactional
    public AsignacionDTO crearAsignacion(AsignacionCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado o no pertenece a la escuela"));

        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(dto.getGrupoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + dto.getGrupoId()));

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(dto.getMateriaId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Materia no encontrada con ID: " + dto.getMateriaId()));

        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(dto.getMaestroId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Maestro no encontrado con ID: " + dto.getMaestroId()));

        Aula aula = aulaRepositorio.findByIdAndEscuelaId(dto.getAulaId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Aula no encontrada con ID: " + dto.getAulaId()));

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
        .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));
        
        // Validar coherencia: el turno debe coincidir con el del grupo
        if (grupo.getTurno() != null &&
            !grupo.getTurno().getTurnoId().equals(turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "El turno seleccionado no coincide con el turno del grupo '" +
                    grupo.getNombre() + "' (" + grupo.getTurno().getNombre() + ")");
        }
        
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Asignacion asignacion = new Asignacion();
        asignacion.setEscuela(escuela);
        asignacion.setGrupo(grupo);
        asignacion.setMateria(materia);
        asignacion.setMaestro(maestro);
        asignacion.setAula(aula);
        asignacion.setHoras(dto.getHoras());
        asignacion.setColorHex(dto.getColorHex() != null ? dto.getColorHex() : materia.getColorHex());
        asignacion.setDistribucion(dto.getDistribucion());
        asignacion.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        asignacion.setSemestre(semestre);
        asignacion.setTurno(turno);

        Asignacion guardado = asignacionRepositorio.save(asignacion);
        return toDTO(guardado);
    }

    @Transactional
    public AsignacionDTO actualizarAsignacion(Long id, AsignacionCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Asignacion asignacion = asignacionRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Asignación no encontrada con ID: " + id));

        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(dto.getGrupoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + dto.getGrupoId()));

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(dto.getMateriaId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Materia no encontrada con ID: " + dto.getMateriaId()));

        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(dto.getMaestroId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Maestro no encontrado con ID: " + dto.getMaestroId()));

        Aula aula = aulaRepositorio.findByIdAndEscuelaId(dto.getAulaId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Aula no encontrada con ID: " + dto.getAulaId()));

        // Igual que en crearAsignacion: cargar turno y validar coherencia
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        if (grupo.getTurno() != null &&
            !grupo.getTurno().getTurnoId().equals(turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "El turno seleccionado no coincide con el turno del grupo '" +
                    grupo.getNombre() + "' (" + grupo.getTurno().getNombre() + ")");
        }
        
        asignacion.setGrupo(grupo);
        asignacion.setMateria(materia);
        asignacion.setMaestro(maestro);
        asignacion.setAula(aula);
        asignacion.setHoras(dto.getHoras());
        asignacion.setColorHex(dto.getColorHex() != null ? dto.getColorHex() : materia.getColorHex());
        asignacion.setDistribucion(dto.getDistribucion());
        if (dto.getActivo() != null) {
            asignacion.setActivo(dto.getActivo());
        }
        asignacion.setTurno(turno);

        Asignacion actualizado = asignacionRepositorio.save(asignacion);
        return toDTO(actualizado);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Asignacion asignacion = asignacionRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Asignación no encontrada con ID: " + id));
        asignacion.setActivo(activo);
        asignacionRepositorio.save(asignacion);
    }

    @Transactional
    public void eliminarAsignacion(Long id) {
        Long escuelaId = getEscuelaId();
        Asignacion asignacion = asignacionRepositorio
                .findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Asignación no encontrada con ID: " + id));

        // Pre-verificación: si tiene horarios generados, mensaje claro en lugar
        // de dejar que PostgreSQL lance DataIntegrityViolationException (409 genérico).
        long horarios = horarioRepositorio.countByAsignacionId(id);
        if (horarios > 0) {
            throw new NegocioExcepcion(
                    "ASIGNACION_EN_USO",
                    "No se puede eliminar la asignación de '" + asignacion.getMateria().getNombre() +
                    "' en el grupo '" + asignacion.getGrupo().getNombre() +
                    "' porque tiene " + horarios +
                    " bloque(s) de horario generado(s). " +
                    "Regenera o elimina primero el horario del grupo, o desactiva la asignación."
            );
        }

        asignacionRepositorio.delete(asignacion);
    }

    // ============================================================
    // CONVERSIÓN A DTO
    // ============================================================
    private AsignacionDTO toDTO(Asignacion asignacion) {
        AsignacionDTO dto = new AsignacionDTO();
        dto.setId(asignacion.getAsignacionId());

        // Grupo
        if (asignacion.getGrupo() != null) {
            dto.setGrupoId(asignacion.getGrupo().getGrupoId());
            dto.setGrupoNombre(asignacion.getGrupo().getNombre());

            // 🔥 Turno del grupo
            if (asignacion.getGrupo().getTurno() != null) {
                dto.setTurnoId(asignacion.getGrupo().getTurno().getTurnoId());
                dto.setTurnoNombre(asignacion.getGrupo().getTurno().getNombre());
            }
        }

        // Materia
        if (asignacion.getMateria() != null) {
            dto.setMateriaId(asignacion.getMateria().getMateriaId());
            dto.setMateriaNombre(asignacion.getMateria().getNombre());
            dto.setMateriaClave(asignacion.getMateria().getClave());
        }

        // Maestro
        if (asignacion.getMaestro() != null) {
            dto.setMaestroId(asignacion.getMaestro().getMaestroId());
            dto.setMaestroNombre(asignacion.getMaestro().getNombreCompleto());
            dto.setMaestroApellidos(asignacion.getMaestro().getApellidos());
        }

        // Aula
        if (asignacion.getAula() != null) {
            dto.setAulaId(asignacion.getAula().getAulaId());
            dto.setAulaNombre(asignacion.getAula().getNombre());
        }

        // Otros campos
        dto.setHoras(asignacion.getHoras());
        dto.setColorHex(asignacion.getColorHex());
        dto.setDistribucion(asignacion.getDistribucion());
        dto.setActivo(asignacion.getActivo());

        // Semestre
        if (asignacion.getSemestre() != null) {
            dto.setSemestreId(asignacion.getSemestre().getSemestreId());
            dto.setSemestreNombre(asignacion.getSemestre().getNombre());
        }

        return dto;
    }
}