package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.DisponibilidadGrupoCrearDTO;
import mx.sih.modelo.dto.DisponibilidadGrupoDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DisponibilidadGrupoServicio {

    private final DisponibilidadGrupoRepositorio disponibilidadRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final SemestreRepositorio semestreRepositorio;

    public DisponibilidadGrupoServicio(DisponibilidadGrupoRepositorio disponibilidadRepositorio,
                                        GrupoRepositorio grupoRepositorio,
                                        TurnoHorarioRepositorio turnoHorarioRepositorio,
                                        SemestreRepositorio semestreRepositorio) {
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.semestreRepositorio = semestreRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    /**
     * 🔥 Obtener disponibilidad por grupo y semestre
     */
    public List<DisponibilidadGrupoDTO> obtenerDisponibilidadPorGrupo(Long grupoId, Long semestreId) {
        Long escuelaId = getEscuelaId();

        if (semestreId == null) {
            Semestre semestre = semestreRepositorio
                    .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestre.getSemestreId();
        }

        List<DisponibilidadGrupo> lista = disponibilidadRepositorio
                .findByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 🔥 Obtener por ID
     */
    public DisponibilidadGrupoDTO obtenerDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadGrupo d = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!d.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }
        return toDTO(d);
    }

    /**
     * 🔥 Crear o actualizar disponibilidad
     */
    @Transactional
    public DisponibilidadGrupoDTO guardarDisponibilidad(DisponibilidadGrupoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        // Validar semestre
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        // Validar grupo
        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(dto.getGrupoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + dto.getGrupoId()));

        // Validar turno_horario
        TurnoHorario turnoHorario = turnoHorarioRepositorio.findByIdAndEscuelaId(dto.getTurnoHorarioId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Horario no encontrado con ID: " + dto.getTurnoHorarioId()));

        // Validar que el turno_horario pertenezca al turno del grupo
        if (!turnoHorario.getTurno().getTurnoId().equals(grupo.getTurno().getTurnoId())) {
            throw new NegocioExcepcion("El bloque horario no pertenece al turno del grupo");
        }

        // Validar semestre del turno_horario
        if (!turnoHorario.getSemestre().getSemestreId().equals(dto.getSemestreId())) {
            throw new NegocioExcepcion("El bloque horario no pertenece al semestre seleccionado");
        }
        
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        // Buscar si ya existe
        DisponibilidadGrupo disponibilidad = disponibilidadRepositorio
                .findByGrupoIdAndTurnoHorarioIdAndSemestreId(
                        dto.getGrupoId(), dto.getTurnoHorarioId(), escuelaId, dto.getSemestreId())
                .orElse(new DisponibilidadGrupo());

        disponibilidad.setEscuela(escuela);
        disponibilidad.setGrupo(grupo);
        disponibilidad.setTurnoHorario(turnoHorario);
        disponibilidad.setSemestre(semestre);
        disponibilidad.setDisponible(dto.getDisponible());

        DisponibilidadGrupo guardado = disponibilidadRepositorio.save(disponibilidad);
        return toDTO(guardado);
    }

    /**
     * 🔥 Eliminar por ID
     */
    @Transactional
    public void eliminarDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadGrupo d = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!d.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }
        disponibilidadRepositorio.delete(d);
    }

    /**
     * 🔥 Eliminar todas las disponibilidades de un grupo en un semestre
     */
    @Transactional
    public void eliminarDisponibilidadPorGrupo(Long grupoId, Long semestreId) {
        Long escuelaId = getEscuelaId();
        disponibilidadRepositorio.deleteByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
    }

    
    /**
    * Cuenta los bloques disponibles configurados de un grupo en un semestre.
    */
   public long contarBloquesDisponibles(Long grupoId, Long semestreId) {
       Long escuelaId = getEscuelaId();

       if (semestreId == null) {
           Semestre semestre = semestreRepositorio
                   .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                   .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
           semestreId = semestre.getSemestreId();
       }

       return disponibilidadRepositorio
               .countDisponiblesByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
   }
    
    
    // ================== CONVERSIÓN ==================

    private DisponibilidadGrupoDTO toDTO(DisponibilidadGrupo d) {
        TurnoHorario th = d.getTurnoHorario();
        DisponibilidadGrupoDTO dto = new DisponibilidadGrupoDTO();
        dto.setId(d.getDisponibilidadGrupoId());
        dto.setGrupoId(d.getGrupo().getGrupoId());
        dto.setGrupoNombre(d.getGrupo().getNombre());
        dto.setTurnoHorarioId(th.getId());
        dto.setDiaSemana(th.getDiaSemana());
        dto.setHoraInicio(th.getHoraInicio().toString());
        dto.setHoraFin(th.getHoraFin().toString());
        dto.setDisponible(d.getDisponible());
        if (d.getSemestre() != null) {
            dto.setSemestreId(d.getSemestre().getSemestreId());
            dto.setSemestreNombre(d.getSemestre().getNombre());
        }
        return dto;
    }
}