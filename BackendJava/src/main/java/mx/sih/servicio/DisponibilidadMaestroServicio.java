// mx.sih.servicio.DisponibilidadMaestroServicio.java
package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.DisponibilidadMaestroCrearDTO;
import mx.sih.modelo.dto.DisponibilidadMaestroDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DisponibilidadMaestroServicio {

    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;
    private final MaestroRepositorio maestroRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final SemestreRepositorio semestreRepositorio;

    public DisponibilidadMaestroServicio(DisponibilidadMaestroRepositorio disponibilidadRepositorio,
                                         MaestroRepositorio maestroRepositorio,
                                         TurnoHorarioRepositorio turnoHorarioRepositorio,
                                         SemestreRepositorio semestreRepositorio) {
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.maestroRepositorio = maestroRepositorio;
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

    // Listar disponibilidades paginadas
    public Page<DisponibilidadMaestroDTO> listarDisponibilidades(Pageable pageable, String busqueda, Long semestreId) {
        Long escuelaId = getEscuelaId();
        
        // Si no viene semestreId, usar el activo
        if (semestreId == null) {
            Semestre semestre = semestreRepositorio.findSemestreActual(escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestre.getSemestreId();
        }
        
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<DisponibilidadMaestro> pagina = disponibilidadRepositorio
                .buscarPorEscuelaYMaestroYSemestre(escuelaId, semestreId, busquedaNormalizada, pageable);
        return pagina.map(this::toDTO);
    }

    // Obtener disponibilidad por ID
    public DisponibilidadMaestroDTO obtenerDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!disponibilidad.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }

        return toDTO(disponibilidad);
    }

    // Obtener disponibilidad por maestro
    public List<DisponibilidadMaestroDTO> obtenerDisponibilidadPorMaestro(Long maestroId) {
        Long escuelaId = getEscuelaId();
        List<DisponibilidadMaestro> lista = disponibilidadRepositorio
                .findByMaestroId(maestroId, escuelaId);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Crear o actualizar disponibilidad
    @Transactional
    public DisponibilidadMaestroDTO guardarDisponibilidad(DisponibilidadMaestroCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado o no pertenece a la escuela"));
        
        // VERIFICAR QUE EL MAESTRO EXISTA
        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(dto.getMaestroId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Maestro no encontrado con ID: " + dto.getMaestroId()));

        // VERIFICAR QUE EL TURNO_HORARIO EXISTA Y PERTENEZCA A LA ESCUELA ACTIVA
         TurnoHorario turnoHorario = turnoHorarioRepositorio
            .findByIdAndEscuelaId(dto.getTurnoHorarioId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Horario no encontrado con ID: " + dto.getTurnoHorarioId()));

        // Validación 1: el bloque horario debe pertenecer al semestre indicado
        if (!turnoHorario.getSemestre().getSemestreId().equals(dto.getSemestreId())) {
            throw new NegocioExcepcion(
                    "El bloque horario no pertenece al semestre seleccionado");
        }
        
        if (maestro.getTurno() == null || turnoHorario.getTurno() == null) {
            throw new NegocioExcepcion(
                    "No se puede validar el turno del maestro o del bloque horario");
        }
        if (!turnoHorario.getTurno().getTurnoId().equals(maestro.getTurno().getTurnoId())) {
            throw new NegocioExcepcion(
                    "El bloque horario pertenece al turno '" + turnoHorario.getTurno().getNombre() +
                    "', pero el maestro '" + maestro.getNombreCompleto() +
                    "' pertenece al turno '" + maestro.getTurno().getNombre() +
                    "'. Asigna únicamente bloques del turno del maestro.");
        }
        
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        // Buscar si ya existe
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio
                .findByMaestroIdAndTurnoHorarioId(dto.getMaestroId(), dto.getTurnoHorarioId(), escuelaId)
                .orElse(new DisponibilidadMaestro());

        disponibilidad.setEscuela(escuela);
        disponibilidad.setMaestro(maestro);
        disponibilidad.setTurnoHorario(turnoHorario);
        disponibilidad.setDisponible(dto.getDisponible());
        disponibilidad.setSemestre(semestre);

        DisponibilidadMaestro guardado = disponibilidadRepositorio.save(disponibilidad);
        return toDTO(guardado);
    }

    // Eliminar disponibilidad
    @Transactional
    public void eliminarDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!disponibilidad.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }

        disponibilidadRepositorio.delete(disponibilidad);
    }

    // Eliminar todas las disponibilidades de un maestro
    @Transactional
    public void eliminarDisponibilidadPorMaestro(Long maestroId) {
        Long escuelaId = getEscuelaId();
        List<DisponibilidadMaestro> lista = disponibilidadRepositorio
                .findByMaestroId(maestroId, escuelaId);
        disponibilidadRepositorio.deleteAll(lista);
    }

    private DisponibilidadMaestroDTO toDTO(DisponibilidadMaestro disponibilidad) {
        TurnoHorario th = disponibilidad.getTurnoHorario();
        DisponibilidadMaestroDTO dto = new DisponibilidadMaestroDTO();
        dto.setId(disponibilidad.getDisponibilidadMaestroId());
        dto.setMaestroId(disponibilidad.getMaestro().getMaestroId());
        dto.setMaestroNombre(disponibilidad.getMaestro().getNombreCompleto());
        dto.setTurnoHorarioId(th.getId());
        dto.setDiaSemana(th.getDiaSemana());
        dto.setHoraInicio(th.getHoraInicio().toString());
        dto.setHoraFin(th.getHoraFin().toString());
        dto.setDisponible(disponibilidad.getDisponible());
        if (disponibilidad.getSemestre() != null) {
            dto.setSemestreId(disponibilidad.getSemestre().getSemestreId());
            dto.setSemestreNombre(disponibilidad.getSemestre().getNombre());
        }
        return dto;
    }
}