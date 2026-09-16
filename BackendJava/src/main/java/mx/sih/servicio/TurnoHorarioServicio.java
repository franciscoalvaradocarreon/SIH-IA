package mx.sih.servicio;

import java.time.LocalTime;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.TurnoHorarioCrearDTO;
import mx.sih.modelo.dto.TurnoHorarioDTO;
import mx.sih.modelo.entidad.Turno;
import mx.sih.modelo.entidad.TurnoHorario;
import mx.sih.repositorio.TurnoHorarioRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.DisponibilidadGrupoRepositorio;
import mx.sih.repositorio.DisponibilidadMaestroRepositorio;
import mx.sih.repositorio.SemestreRepositorio;

@Service
public class TurnoHorarioServicio {

    private static final Logger logger = LoggerFactory.getLogger(TurnoHorarioServicio.class);

    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadMaestroRepositorio;
    private final HorarioRepositorio horarioRepositorio;


    public TurnoHorarioServicio(TurnoHorarioRepositorio turnoHorarioRepositorio,
                                TurnoRepositorio turnoRepositorio,
                                SemestreRepositorio semestreRepositorio,
                                DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio,
                                DisponibilidadMaestroRepositorio disponibilidadMaestroRepositorio,
                                HorarioRepositorio horarioRepositorio) {
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.disponibilidadGrupoRepositorio = disponibilidadGrupoRepositorio;
        this.disponibilidadMaestroRepositorio = disponibilidadMaestroRepositorio;
        this.horarioRepositorio = horarioRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public List<TurnoHorarioDTO> listarHorariosPorTurno(Long turnoId, Long semestreId) {
        Long escuelaId = getEscuelaId();

        // Verificar que el turno existe y pertenece a la escuela
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        // Si no se proporciona semestreId, usar el semestre activo
        if (semestreId == null) {
            Semestre semestreActivo = semestreRepositorio.findSemestreActual(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestreActivo.getSemestreId();
        }

        List<TurnoHorario> horarios = turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId);
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Obtener todos los horarios de un turno (sin filtro de semestre)
     */
    public List<TurnoHorarioDTO> listarHorariosPorTurno(Long turnoId) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        List<TurnoHorario> horarios = turnoHorarioRepositorio.findByTurnoId(turnoId);
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Listar solo clases (sin filtro de activo)
    public List<TurnoHorarioDTO> listarClasesPorTurno(Long turnoId) {
        logger.info("📋 Listando clases para turno ID: {}", turnoId);

        // Antes: turnoRepositorio.findById(turnoId) → permitía leer los bloques
        // horarios de un turno de OTRA escuela con solo incrementar el id.
        Long escuelaId = getEscuelaId();
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + turnoId));

        if (!turno.getActivo()) {
            throw new NegocioExcepcion("El turno no está activo");
        }

        // Obtener TODAS las clases (sin filtro de activo)
        List<TurnoHorario> clases = turnoHorarioRepositorio.findClasesByTurnoId(turnoId);
        return clases.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Listar solo descansos (sin filtro de activo)
    public List<TurnoHorarioDTO> listarDescansosPorTurno(Long turnoId) {
        logger.info("📋 Listando descansos para turno ID: {}", turnoId);

        // Antes: turnoRepositorio.findById(turnoId) → misma fuga entre escuelas.
        Long escuelaId = getEscuelaId();
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + turnoId));

        if (!turno.getActivo()) {
            throw new NegocioExcepcion("El turno no está activo");
        }

        // Obtener TODOS los descansos (sin filtro de activo)
        List<TurnoHorario> descansos = turnoHorarioRepositorio.findDescansosByTurnoId(turnoId);
        return descansos.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Contar horarios (sin filtro de activo)
    public long contarHorarios(Long turnoId) {
        // Antes no se comprobaba nada: devolvía el conteo de un turno ajeno.
        Long escuelaId = getEscuelaId();
        turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + turnoId));

        List<TurnoHorario> horarios = turnoHorarioRepositorio.findByTurnoId(turnoId);
        return horarios.size();
    }

    // Verificar si tiene horarios
    public boolean tieneHorarios(Long turnoId) {
        return contarHorarios(turnoId) > 0;
    }

    @Transactional
    public TurnoHorarioDTO crearHorario(Long turnoId, TurnoHorarioCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        // Convertir horas
        LocalTime horaInicio = dto.getHoraInicio();
        LocalTime horaFin = dto.getHoraFin();

        // Verificar que horaInicio sea menor que horaFin
        if (horaInicio.isAfter(horaFin) || horaInicio.equals(horaFin)) {
            throw new NegocioExcepcion("La hora de inicio debe ser menor que la hora de fin");
        }

        // Validar solapamiento con otros bloques del mismo turno y día.
        // Antes SOLO se validaba al ACTUALIZAR: al crear se podían generar bloques
        // solapados (07:00-08:00 y 07:30-08:30) que luego rompían los horarios.
        List<TurnoHorario> existentes = turnoHorarioRepositorio
                .findByTurnoIdAndDiaSemana(turno.getTurnoId(), dto.getDiaSemana());

        for (TurnoHorario existente : existentes) {
            if (seSolapan(existente.getHoraInicio(), existente.getHoraFin(), horaInicio, horaFin)) {
                throw new NegocioExcepcion(
                    String.format("El horario se solapa con otro de %s a %s",
                        existente.getHoraInicio(), existente.getHoraFin())
                );
            }
        }

        TurnoHorario horario = new TurnoHorario();
        horario.setTurno(turno);
        horario.setDiaSemana(dto.getDiaSemana());
        horario.setHoraInicio(horaInicio);
        horario.setHoraFin(horaFin);
        horario.setDescanso(dto.getDescanso() != null ? dto.getDescanso() : false);
        horario.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        horario.setSemestre(semestre);

        TurnoHorario guardado = turnoHorarioRepositorio.save(horario);
        return toDTO(guardado);
    }


    // ACTUALIZAR HORARIO - Sin campo activo
    @Transactional
    public TurnoHorarioDTO actualizarHorario(Long id, TurnoHorarioCrearDTO dto) {
        logger.info("✏️ Actualizando horario ID: {}", id);

        Long escuelaId = getEscuelaId();

        // Buscar el horario por ID y escuela
        TurnoHorario horario = turnoHorarioRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> {
                    logger.warn("❌ Horario no encontrado: {}", id);
                    return new NegocioExcepcion("Horario no encontrado con ID: " + id);
                });

        // Validar que el turno asociado esté activo
        Turno turno = horario.getTurno();
        if (!turno.getActivo()) {
            throw new NegocioExcepcion("No se puede editar un horario de un turno inactivo");
        }

        // Validar que hora_inicio < hora_fin
        if (dto.getHoraInicio().isAfter(dto.getHoraFin()) || dto.getHoraInicio().equals(dto.getHoraFin())) {
            throw new NegocioExcepcion("La hora de inicio debe ser anterior a la hora de fin");
        }

        // Validar solapamiento con otros horarios (excluyendo el actual)
        List<TurnoHorario> existentes = turnoHorarioRepositorio
                .findByTurnoIdAndDiaSemana(turno.getTurnoId(), dto.getDiaSemana());

        for (TurnoHorario existente : existentes) {
            if (existente.getId().equals(id)) continue; // Saltar el actual

            if (seSolapan(existente.getHoraInicio(), existente.getHoraFin(),
                         dto.getHoraInicio(), dto.getHoraFin())) {
                throw new NegocioExcepcion(
                    String.format("El horario se solapa con otro de %s a %s",
                        existente.getHoraInicio(), existente.getHoraFin())
                );
            }
        }

        horario.setDiaSemana(dto.getDiaSemana());
        horario.setHoraInicio(dto.getHoraInicio());
        horario.setHoraFin(dto.getHoraFin());
        horario.setDescanso(dto.getDescanso() != null ? dto.getDescanso() : false);
        horario.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);

        TurnoHorario actualizado = turnoHorarioRepositorio.save(horario);
        logger.info("✅ Horario actualizado ID: {}", actualizado.getId());

        return toDTO(actualizado);
    }

    // ELIMINAR HORARIO - Eliminación física (DELETE)
    @Transactional
    public void eliminarHorario(Long id) {
        Long escuelaId = getEscuelaId();

        TurnoHorario horario = turnoHorarioRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Horario no encontrado con ID: " + id));

        if (!horario.getTurno().getActivo()) {
            throw new NegocioExcepcion("No se puede eliminar un horario de un turno inactivo");
        }

        // Verificar dependencias
        boolean tieneDispGrupo = disponibilidadGrupoRepositorio.existsByTurnoHorarioId(id);
        boolean tieneDispMaestro = disponibilidadMaestroRepositorio.existsByTurnoHorarioId(id);
        boolean tieneHorarios = horarioRepositorio.existsByTurnoHorarioId(id);

        if (tieneDispGrupo || tieneDispMaestro || tieneHorarios) {
            StringBuilder deps = new StringBuilder();
            if (tieneHorarios) deps.append("horarios de grupos");
            if (tieneDispGrupo) {
                if (deps.length() > 0) deps.append(", ");
                deps.append("disponibilidad de grupos");
            }
            if (tieneDispMaestro) {
                if (deps.length() > 0) deps.append(", ");
                deps.append("disponibilidad de maestros");
            }

            throw new NegocioExcepcion(
                "No se puede eliminar el bloque porque está siendo usado en: " + deps + ". " +
                "Elimina primero esas dependencias."
            );
        }

        turnoHorarioRepositorio.delete(horario);
    }

    /**
    * Listar horarios de un turno filtrando por día y semestre
    */
    public List<TurnoHorarioDTO> findByTurnoIdAndSemestreId(Long turnoId, Long semestreId) {
        Long escuelaId = getEscuelaId();

        // Antes: turnoRepositorio.findById(turnoId) → fuga entre escuelas.
        turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        if (semestreId == null) {
            Semestre semestreActivo = semestreRepositorio.findSemestreActual(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestreActivo.getSemestreId();
        }

        List<TurnoHorario> horarios = turnoHorarioRepositorio.findByTurnoIdAndDiaSemanaAndSemestreId(
            turnoId,  semestreId
        );

        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }



    // MÉTODO PRIVADO - Verificar solapamiento
    private boolean seSolapan(LocalTime inicio1, LocalTime fin1, LocalTime inicio2, LocalTime fin2) {
        return !(fin1.isBefore(inicio2) || inicio1.isAfter(fin2) ||
                 fin1.equals(inicio2) || inicio1.equals(fin2));
    }

    // MÉTODO DE CONVERSIÓN
    private TurnoHorarioDTO toDTO(TurnoHorario horario) {
        String[] dias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
        String diaNombre = horario.getDiaSemana() >= 1 && horario.getDiaSemana() <= 5
            ? dias[horario.getDiaSemana() - 1]
            : "Día " + horario.getDiaSemana();

    return TurnoHorarioDTO.builder()
        .id(horario.getId())
        .turnoId(horario.getTurno().getTurnoId())
        .turnoNombre(horario.getTurno().getNombre())
        .diaSemana(horario.getDiaSemana())
        .diaNombre(diaNombre)
        .horaInicio(horario.getHoraInicio().toString())
        .horaFin(horario.getHoraFin().toString())
        .descanso(horario.getDescanso())
        .orden(horario.getOrden())
        .semestreId(horario.getSemestre() != null ? horario.getSemestre().getSemestreId() : null)
        .semestreNombre(horario.getSemestre() != null ? horario.getSemestre().getNombre() : null)
        .build();
    }
}
