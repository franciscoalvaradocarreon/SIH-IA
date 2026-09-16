package mx.sih.servicio;

import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mx.sih.modelo.solver.AsignacionHorario;
import mx.sih.modelo.solver.ClaseNoAsignadaDTO;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.HorarioSolucionDTO;
import mx.sih.modelo.dto.HorarioSolucionMasivaDTO;
import mx.sih.modelo.solver.HorarioSolution;
import mx.sih.modelo.solver.HorarioSolverService;

@Service
public class HorarioServicio {

    private final AsignacionRepositorio asignacionRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final HorarioRepositorio horarioRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio;
    private final HorarioSolverService solverService;

    public HorarioServicio(AsignacionRepositorio asignacionRepositorio,
                           TurnoHorarioRepositorio turnoHorarioRepositorio,
                           HorarioRepositorio horarioRepositorio,
                           GrupoRepositorio grupoRepositorio,
                           SemestreRepositorio semestreRepositorio,
                           DisponibilidadMaestroRepositorio disponibilidadRepositorio,
                           DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio,
                           HorarioSolverService solverService) {
        this.asignacionRepositorio = asignacionRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.horarioRepositorio = horarioRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.disponibilidadGrupoRepositorio = disponibilidadGrupoRepositorio;
        this.solverService = solverService;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    private Semestre getSemestreActivo(Long escuelaId) {
        return semestreRepositorio
                .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
    }

    private Semestre resolverSemestre(Long escuelaId, Long semestreIdParam) {
        if (semestreIdParam != null) {
            return semestreRepositorio.findByIdAndEscuelaId(semestreIdParam, escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion(
                            "Semestre no encontrado con ID: " + semestreIdParam));
        }
        return getSemestreActivo(escuelaId);
    }
    
    // ============================================================
    // GENERAR HORARIO PARA UN GRUPO
    // ============================================================
    @Transactional
    public HorarioSolucionDTO generarHorario(Long grupoId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();

        System.out.println("🚀 Generando horario para grupo " + grupoId + " semestre " + semestreId);

        // 1. Verificar grupo
        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(grupoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado"));

        // 2. Obtener asignaciones del grupo en el semestre
        List<Asignacion> asignaciones = asignacionRepositorio
                .findByGrupoIdAndSemestreId(grupoId, semestreId);

        if (asignaciones.isEmpty()) {
            System.out.println("⚠️ Grupo " + grupoId + " sin asignaciones en semestre " + semestreId);
            HorarioSolucionDTO dto = new HorarioSolucionDTO();
            dto.setGrupoId(grupoId);
            dto.setGrupoNombre(grupo.getNombre());
            dto.setTotalClasesAsignadas(0);
            dto.setSemestreId(semestreId);
            dto.setSemestreNombre(semestre.getNombre());
            dto.setFechaGeneracion(java.time.LocalDateTime.now());
            return dto;
        }

        // 3. Obtener bloques horarios del turno en el semestre
        Long turnoId = grupo.getTurno().getTurnoId();
        List<TurnoHorario> bloques = turnoHorarioRepositorio
                .findClasesByTurnoIdAndSemestreId(turnoId, semestreId);

        if (bloques.isEmpty()) {
            throw new NegocioExcepcion("El turno no tiene bloques horarios en este semestre.");
        }

        // 4. Obtener disponibilidades de TODOS los maestros del grupo (solo disponible=true)
        List<Long> maestroIds = asignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .collect(Collectors.toList());

        List<DisponibilidadMaestro> disponibilidades = new ArrayList<>();
        for (Long maestroId : maestroIds) {
            disponibilidades.addAll(
                disponibilidadRepositorio.findDisponiblesByMaestroIdAndSemestreId(
                        maestroId, escuelaId, semestreId)
            );
        }

        System.out.println("   Maestros: " + maestroIds.size());
        System.out.println("   Disponibilidades: " + disponibilidades.size());

        // Cargar disponibilidad del grupo
        List<DisponibilidadGrupo> disponibilidadesGrupo =
                disponibilidadGrupoRepositorio.findDisponiblesByGrupoIdAndSemestreId(
                        grupoId, escuelaId, semestreId);

        if (disponibilidadesGrupo.isEmpty()) {
            throw new NegocioExcepcion(
                "El grupo '" + grupo.getNombre() + "' no tiene bloques configurados. " +
                "Configura la disponibilidad del grupo antes de generar el horario."
            );
        }

        System.out.println("   Bloques del grupo: " + disponibilidadesGrupo.size());

        // 5. Construir las entidades de planificación (una por cada hora)
        List<AsignacionHorario> planificaciones = new ArrayList<>();
        for (Asignacion asignacion : asignaciones) {
            for (int i = 1; i <= asignacion.getHoras(); i++) {
                AsignacionHorario ah = new AsignacionHorario(
                        asignacion.getAsignacionId(),
                        asignacion.getGrupo().getGrupoId(),
                        asignacion.getGrupo().getNombre(),
                        asignacion.getMateria().getMateriaId(),
                        asignacion.getMateria().getNombre(),
                        asignacion.getMateria().getClave(),
                        asignacion.getMaestro().getMaestroId(),
                        asignacion.getMaestro().getNombreCompleto(),
                        asignacion.getAula().getAulaId(),
                        asignacion.getAula().getNombre(),
                        asignacion.getColorHex(),
                        asignacion.getDistribucion(),
                        i,
                        asignacion.getGrupo().getTurno().getTurnoId()
                );
                planificaciones.add(ah);
            }
        }

        System.out.println("   Instancias a planificar: " + planificaciones.size());

        // 6. Construir la solución
        HorarioSolution problem = new HorarioSolution(
                bloques,
                disponibilidades,
                disponibilidadesGrupo,
                planificaciones
        );
        problem.setGrupoId(grupoId);
        problem.setSemestreId(semestreId);

        // 7. Ejecutar el solver
        HorarioSolution solution = solverService.resolver(problem);

        System.out.println("✅ Score final: " + solution.getScore());

        // 8. Eliminar horarios previos del grupo en el semestre
        horarioRepositorio.deleteByGrupoIdAndVersionAndSemestreId(grupoId, 1, escuelaId, semestreId);

        // 9. Guardar los nuevos horarios
        int guardados = 0;
        List<ClaseNoAsignadaDTO> noAsignadas = new ArrayList<>();

        for (AsignacionHorario ah : solution.getAsignaciones()) {
            Asignacion asignacionOriginal = asignaciones.stream()
                .filter(a -> a.getAsignacionId().equals(ah.getAsignacionId()))
                .findFirst()
                .orElse(null);

            if (asignacionOriginal == null) continue;
            
            if (ah.getBloqueHorario() == null) {
                // 🔥 Esta hora no se pudo asignar
                ClaseNoAsignadaDTO noAsignada = new ClaseNoAsignadaDTO();
                noAsignada.setAsignacionId(asignacionOriginal.getAsignacionId());
                noAsignada.setGrupoId(asignacionOriginal.getGrupo().getGrupoId());
                noAsignada.setGrupoNombre(asignacionOriginal.getGrupo().getNombre());
                noAsignada.setMateriaId(asignacionOriginal.getMateria().getMateriaId());
                noAsignada.setMateriaNombre(asignacionOriginal.getMateria().getNombre());
                noAsignada.setMateriaClave(asignacionOriginal.getMateria().getClave());
                noAsignada.setMaestroId(asignacionOriginal.getMaestro().getMaestroId());
                noAsignada.setMaestroNombre(asignacionOriginal.getMaestro().getNombreCompleto());
                noAsignada.setAulaId(asignacionOriginal.getAula().getAulaId());
                noAsignada.setAulaNombre(asignacionOriginal.getAula().getNombre());
                noAsignada.setMotivo("Sin bloque compatible disponible");
                noAsignadas.add(noAsignada);
                continue;
            }

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);

            Horario horario = new Horario();
            horario.setEscuela(escuela);
            horario.setGrupo(asignacionOriginal.getGrupo());
            horario.setAsignacion(asignacionOriginal);
            horario.setTurnoHorario(ah.getBloqueHorario());
            horario.setAula(asignacionOriginal.getAula());
            horario.setVersion(1);
            horario.setSemestre(semestre);

            horarioRepositorio.save(horario);
            guardados++;
        }

        System.out.println("✅ Horarios guardados: " + guardados);

        // 10. Retornar DTO con la solución
        HorarioSolucionDTO dto = new HorarioSolucionDTO();
        dto.setGrupoId(grupoId);
        dto.setScore(solution.getScore());
        dto.setFechaGeneracion(solution.getFechaGeneracion());
        dto.setTotalClasesAsignadas(guardados);
        dto.setTotalClasesNoAsignadas(noAsignadas.size());
        dto.setClasesNoAsignadas(noAsignadas);
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        return dto;
    }

    public List<HorarioDTO> obtenerHorarioGrupo(Long grupoId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByGrupoIdAndSemestreId(
                grupoId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<HorarioDTO> obtenerHorarioMaestro(Long maestroId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByMaestroIdAndSemestreId(
                maestroId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<HorarioDTO> obtenerHorarioAula(Long aulaId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByAulaIdAndSemestreId(
                aulaId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }


    // ============================================================
    // OBTENER TODOS LOS HORARIOS DE LA ESCUELA
    // ============================================================
    public List<HorarioDTO> obtenerTodosLosHorarios(Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByEscuelaIdAndSemestreId(
                escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ============================================================
    // GENERACIÓN MASIVA
    // ============================================================
    @Transactional
    public HorarioSolucionMasivaDTO generarTodos(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);  
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;


        // 1. Todos los grupos activos
        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);
        
        if (grupos.isEmpty()) {
            throw new NegocioExcepcion(
                    turnoFiltro != null
                            ? "No hay grupos activos en el turno seleccionado"
                            : "No hay grupos activos en el semestre");
        }
        // 2. Todas las asignaciones de esos grupos
        List<Asignacion> todasAsignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            todasAsignaciones.addAll(
                asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId)
            );
        }

        // 3. Todos los bloques de todos los turnos usados
        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());
        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long turnoId : turnoIds) {
            bloques.addAll(
                turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId)
            );
        }

        // 4. Todas las disponibilidades de maestros involucrados
        List<Long> maestroIds = todasAsignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .collect(Collectors.toList());
        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        for (Long m : maestroIds) {
            dispMaestros.addAll(
                disponibilidadRepositorio.findDisponiblesByMaestroIdAndSemestreId(
                    m, escuelaId, semestreId)
            );
        }

        // 5. Disponibilidades de grupos
        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        for (Grupo g : grupos) {
            dispGrupos.addAll(
                disponibilidadGrupoRepositorio.findDisponiblesByGrupoIdAndSemestreId(
                    g.getGrupoId(), escuelaId, semestreId)
            );
        }

        // === DIAGNÓSTICO ===
        System.out.println("=== DIAGNÓSTICO GENERACIÓN MASIVA ===");
        System.out.println("Grupos: " + grupos.size());
        System.out.println("Asignaciones: " + todasAsignaciones.size());
        System.out.println("Bloques (total): " + bloques.size());
        System.out.println("Disp. maestros: " + dispMaestros.size());
        System.out.println("Disp. grupos: " + dispGrupos.size());

        // Por grupo
        for (Grupo g : grupos) {
            int horasNecesarias = todasAsignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(g.getGrupoId()))
                    .mapToInt(Asignacion::getHoras)
                    .sum();
            long bloquesDisponibles = dispGrupos.stream()
                    .filter(d -> d.getGrupo().getGrupoId().equals(g.getGrupoId()))
                    .count();

            System.out.println("  " + g.getNombre() +
                    " | turno=" + g.getTurno().getNombre() +
                    " | horas=" + horasNecesarias +
                    " | bloques=" + bloquesDisponibles +
                    " | " + (horasNecesarias <= bloquesDisponibles ? "✅" : "❌"));
        }

        // Por maestro
        for (Long m : maestroIds) {
            long disp = dispMaestros.stream()
                    .filter(d -> d.getMaestro().getMaestroId().equals(m))
                    .count();
            if (disp == 0) {
                System.out.println("  ⚠️ Maestro " + m + " SIN disponibilidad");
            }
        }

        // Filtrado de bloques
        Set<Long> bloquesPermitidos = dispGrupos.stream()
                .map(d -> d.getTurnoHorario().getId())
                .collect(Collectors.toSet());

        List<TurnoHorario> bloquesFiltrados = bloques.stream()
                .filter(b -> bloquesPermitidos.contains(b.getId()))
                .collect(Collectors.toList());

        System.out.println("   Bloques totales: " + bloques.size());
        System.out.println("   Bloques tras filtrar: " + bloquesFiltrados.size());

        // 🔥 PRE-VALIDACIÓN DE FACTIBILIDAD
        System.out.println("=== PRE-VALIDACIÓN DE FACTIBILIDAD ===");
        List<String> problemas = analizarFactibilidad(
                grupos, todasAsignaciones, dispGrupos, dispMaestros);

        if (!problemas.isEmpty()) {
            System.out.println("⚠️ Se encontraron " + problemas.size() + " problemas:");
            problemas.forEach(p -> System.out.println("   " + p));

            List<String> criticos = problemas.stream()
                    .filter(p -> p.startsWith("❌"))
                    .collect(Collectors.toList());

            if (!criticos.isEmpty()) {
                throw new NegocioExcepcion(
                        "No se puede generar el horario por los siguientes problemas:\n" +
                        String.join("\n", criticos)
                );
            }
        } else {
            System.out.println("   ✅ Sin problemas detectados");
        }

        // 6. Todas las instancias de planificación
        List<AsignacionHorario> planificaciones = new ArrayList<>();
        for (Asignacion asignacion : todasAsignaciones) {
            for (int i = 1; i <= asignacion.getHoras(); i++) {
                AsignacionHorario ah = new AsignacionHorario(
                        asignacion.getAsignacionId(),
                        asignacion.getGrupo().getGrupoId(),
                        asignacion.getGrupo().getNombre(),
                        asignacion.getMateria().getMateriaId(),
                        asignacion.getMateria().getNombre(),
                        asignacion.getMateria().getClave(),
                        asignacion.getMaestro().getMaestroId(),
                        asignacion.getMaestro().getNombreCompleto(),
                        asignacion.getAula().getAulaId(),
                        asignacion.getAula().getNombre(),
                        asignacion.getColorHex(),
                        asignacion.getDistribucion(),
                        i,
                        asignacion.getGrupo().getTurno().getTurnoId()
                );
                planificaciones.add(ah);
            }
        }

        System.out.println("   Total asignaciones: " + todasAsignaciones.size());
        System.out.println("   Total instancias a planificar: " + planificaciones.size());

        // 7. Construir la solución
        HorarioSolution problem = new HorarioSolution(
                bloquesFiltrados, dispMaestros, dispGrupos, planificaciones);
        problem.setSemestreId(semestreId);

        // 8. Resolver
        long inicio = System.currentTimeMillis();
        HorarioSolution solution = solverService.resolver(problem);
        long tiempoMs = System.currentTimeMillis() - inicio;

        System.out.println("✅ Score final: " + solution.getScore());

        // 9. Borrar horarios previos de TODOS los grupos del semestre
        for (Grupo g : grupos) {
            horarioRepositorio.deleteByGrupoIdAndVersionAndSemestreId(
                g.getGrupoId(), 1, escuelaId, semestreId);
        }

        // 10. Guardar los nuevos horarios
        HorarioSolucionMasivaDTO dto = new HorarioSolucionMasivaDTO();
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        dto.setFechaGeneracion(java.time.LocalDateTime.now());
        dto.setScore(solution.getScore());
        dto.setTotalGrupos(grupos.size());
        dto.setTotalAsignaciones(todasAsignaciones.size());
        dto.setTiempoMs(tiempoMs);
        dto.setTiempoSegundos(tiempoMs / 1000);

        int totalGuardados = 0;
        int gruposConHorario = 0;
        int gruposSinAsignaciones = 0;
        int gruposSinDisponibilidad = 0;

        Map<Long, Asignacion> mapaAsignaciones = todasAsignaciones.stream()
                .collect(Collectors.toMap(Asignacion::getAsignacionId, a -> a));

        Map<Long, Integer> clasesPorGrupo = new HashMap<>();

        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() == null) continue;

            Asignacion original = mapaAsignaciones.get(ah.getAsignacionId());
            if (original == null) continue;

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);

            Horario horario = new Horario();
            horario.setEscuela(escuela);
            horario.setGrupo(original.getGrupo());
            horario.setAsignacion(original);
            horario.setTurnoHorario(ah.getBloqueHorario());
            horario.setAula(original.getAula());
            horario.setVersion(1);
            horario.setSemestre(semestre);

            horarioRepositorio.save(horario);
            totalGuardados++;

            clasesPorGrupo.merge(original.getGrupo().getGrupoId(), 1, Integer::sum);
        }

        System.out.println("✅ Horarios guardados: " + totalGuardados);

        // 11. Armar el detalle por grupo
        for (Grupo g : grupos) {
            int clases = clasesPorGrupo.getOrDefault(g.getGrupoId(), 0);

            HorarioSolucionMasivaDTO.DetalleGrupoDTO detalle =
                    new HorarioSolucionMasivaDTO.DetalleGrupoDTO();
            detalle.setGrupoId(g.getGrupoId());
            detalle.setGrupoNombre(g.getNombre());
            detalle.setGrado(g.getGrado());
            detalle.setTurno(g.getTurno() != null ? g.getTurno().getNombre() : null);
            detalle.setClasesAsignadas(clases);

            boolean tieneAsignaciones = todasAsignaciones.stream()
                    .anyMatch(a -> a.getGrupo().getGrupoId().equals(g.getGrupoId()));

            if (!tieneAsignaciones) {
                detalle.setEstado("SIN_ASIGNACIONES");
                detalle.setMensaje("⚠️ El grupo no tiene asignaciones en este semestre");
                gruposSinAsignaciones++;
            } else if (clases == 0) {
                detalle.setEstado("SIN_DISPONIBILIDAD");
                detalle.setMensaje("⚠️ No se pudo asignar ninguna clase (revisa disponibilidad)");
                gruposSinDisponibilidad++;
            } else {
                detalle.setEstado("OK");
                detalle.setMensaje("✅ " + clases + " clases generadas");
                gruposConHorario++;
            }

            dto.getDetalles().add(detalle);
        }

        dto.setTotalClasesAsignadas(totalGuardados);
        dto.setGruposConHorario(gruposConHorario);
        dto.setGruposSinAsignaciones(gruposSinAsignaciones);
        dto.setGruposSinDisponibilidad(gruposSinDisponibilidad);

        if (turnoFiltro != null) {
            Turno turno = grupos.get(0).getTurno(); // todos los grupos son del mismo turno
            dto.setTurnoId(turno.getTurnoId());
            dto.setTurnoNombre(turno.getNombre());
        }
        
        return dto;
    }

    // ============================================================
    // PRE-VALIDACIÓN PÚBLICA
    // ============================================================
    /**
     * Pre-validación pública: carga los datos y analiza factibilidad.
     * Llamado desde el controlador (GET /api/horarios/validar).
     */
    public List<String> validarFactibilidad(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        // 1. Grupos activos
        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);
        
        // 2. Asignaciones
        List<Asignacion> asignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            asignaciones.addAll(
                asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId)
            );
        }

        // 3. Disponibilidades de grupos
        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        for (Grupo g : grupos) {
            dispGrupos.addAll(
                disponibilidadGrupoRepositorio.findDisponiblesByGrupoIdAndSemestreId(
                    g.getGrupoId(), escuelaId, semestreId)
            );
        }

        // 4. Disponibilidades de maestros
        List<Long> maestroIds = asignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .collect(Collectors.toList());

        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        for (Long m : maestroIds) {
            dispMaestros.addAll(
                disponibilidadRepositorio.findDisponiblesByMaestroIdAndSemestreId(
                    m, escuelaId, semestreId)
            );
        }

        // 5. Analizar
        return analizarFactibilidad(grupos, asignaciones, dispGrupos, dispMaestros);
    }

    // ============================================================
    // PRE-VALIDACIÓN PRIVADA (LÓGICA)
    // ============================================================
    /**
     * Pre-validación: detecta si algún grupo tiene asignaciones
     * que no se pueden cubrir por falta de disponibilidad de sus maestros.
     *
     * @return Lista de problemas encontrados (vacía si todo está OK)
     */
    private List<String> analizarFactibilidad(
            List<Grupo> grupos,
            List<Asignacion> todasAsignaciones,
            List<DisponibilidadGrupo> dispGrupos,
            List<DisponibilidadMaestro> dispMaestros) {

        List<String> problemas = new ArrayList<>();

        // Índice: grupoId -> set de turnoHorarioId disponibles
        Map<Long, Set<Long>> bloquesDisponiblesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            bloquesDisponiblesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }

        // Índice: "maestroId-bloqueId" -> disponible
        Set<String> maestroBloqueDisponible = new HashSet<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            maestroBloqueDisponible.add(
                    dm.getMaestro().getMaestroId() + "-" + dm.getTurnoHorario().getId()
            );
        }

        // Recorrer cada grupo
        for (Grupo grupo : grupos) {

            // 🔥 1. Primero obtener asignaciones del grupo
            List<Asignacion> asignacionesGrupo = todasAsignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(grupo.getGrupoId()))
                    .collect(Collectors.toList());

            // 🔥 2. Si no tiene asignaciones, no hay nada que validar
            if (asignacionesGrupo.isEmpty()) {
                continue;
            }

            // 🔥 3. Ahora sí, verificar que tenga bloques configurados
            Set<Long> bloquesGrupo = bloquesDisponiblesPorGrupo.getOrDefault(
                    grupo.getGrupoId(), Collections.emptySet());

            if (bloquesGrupo.isEmpty()) {
                problemas.add("❌ Grupo " + grupo.getNombre() +
                        ": tiene asignaciones pero no tiene bloques configurados en disponibilidad_grupo");
                continue;
            }

            // 4. Verificar cada asignación
            for (Asignacion a : asignacionesGrupo) {
                Long maestroId = a.getMaestro().getMaestroId();
                int horasNecesarias = a.getHoras();

                long bloquesCompatibles = bloquesGrupo.stream()
                        .filter(bloqueId ->
                                maestroBloqueDisponible.contains(maestroId + "-" + bloqueId))
                        .count();

                if (bloquesCompatibles == 0) {
                    problemas.add(String.format(
                            "❌ Grupo %s · Materia %s · Maestro %s: 0 bloques compatibles " +
                            "(el maestro NO está disponible en ningún bloque del grupo)",
                            grupo.getNombre(),
                            a.getMateria().getNombre(),
                            a.getMaestro().getNombreCompleto()
                    ));
                } else if (bloquesCompatibles < horasNecesarias) {
                    problemas.add(String.format(
                            "⚠️ Grupo %s · Materia %s · Maestro %s: " +
                            "%d bloques compatibles pero se necesitan %d horas",
                            grupo.getNombre(),
                            a.getMateria().getNombre(),
                            a.getMaestro().getNombreCompleto(),
                            bloquesCompatibles,
                            horasNecesarias
                    ));
                }
            }
        }

        
        // ============================================================
        // 🔥 SEGUNDA VALIDACIÓN: horas de clase del maestro vs bloques disponibles
        // ============================================================
        Map<Long, Integer> horasAsignadasPorMaestro = new HashMap<>();
        for (Asignacion a : todasAsignaciones) {
            Long maestroId = a.getMaestro().getMaestroId();
            horasAsignadasPorMaestro.merge(maestroId, a.getHoras(), Integer::sum);
        }

        // Contar bloques disponibles por maestro (en el semestre)
        Map<Long, Integer> bloquesDisponiblesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            Long maestroId = dm.getMaestro().getMaestroId();
            bloquesDisponiblesPorMaestro.merge(maestroId, 1, Integer::sum);
        }

        // Comparar
        for (Map.Entry<Long, Integer> entry : horasAsignadasPorMaestro.entrySet()) {
            Long maestroId = entry.getKey();
            int horasAsignadas = entry.getValue();
            int bloquesDisponibles = bloquesDisponiblesPorMaestro.getOrDefault(maestroId, 0);

            if (bloquesDisponibles == 0) {
                // Ya está cubierto por la validación anterior (maestro sin disponibilidad)
                continue;
            }

            if (bloquesDisponibles < horasAsignadas) {
                // Buscar el nombre del maestro
                String nombreMaestro = todasAsignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(maestroId))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst()
                        .orElse("Maestro ID " + maestroId);

                problemas.add(String.format(
                        "❌ Maestro %s: tiene %d horas de clase asignadas pero solo %d bloques disponibles " +
                        "(faltan %d bloques para cubrir sus clases)",
                        nombreMaestro,
                        horasAsignadas,
                        bloquesDisponibles,
                        horasAsignadas - bloquesDisponibles
                ));
            }
        }
        
        
        return problemas;
    }
    
    
    // ============================================================
    // CONVERSIÓN
    // ============================================================
    private HorarioDTO toDTO(Horario h) {
        HorarioDTO dto = new HorarioDTO();
        dto.setId(h.getHorarioId());
        dto.setGrupoId(h.getGrupo().getGrupoId());
        dto.setGrupoNombre(h.getGrupo().getNombre());
        dto.setAsignacionId(h.getAsignacion().getAsignacionId());
        dto.setMateriaNombre(h.getAsignacion().getMateria().getNombre());
        dto.setMateriaClave(h.getAsignacion().getMateria().getClave());
        dto.setMaestroNombre(h.getAsignacion().getMaestro().getTituloNombreCompleto());
        dto.setTurnoHorarioId(h.getTurnoHorario().getId());
        dto.setDiaSemana(h.getTurnoHorario().getDiaSemana());
        dto.setHoraInicio(h.getTurnoHorario().getHoraInicio().toString());
        dto.setHoraFin(h.getTurnoHorario().getHoraFin().toString());
        dto.setAulaId(h.getAula().getAulaId());
        dto.setAulaNombre(h.getAula().getNombre());
        dto.setColorHex(h.getAsignacion().getColorHex());
        dto.setVersion(h.getVersion());
        if (h.getSemestre() != null) {
            dto.setSemestreId(h.getSemestre().getSemestreId());
            dto.setSemestreNombre(h.getSemestre().getNombre());
        }
        return dto;
    }
}