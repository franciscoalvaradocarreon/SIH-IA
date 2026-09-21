package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.modelo.entidad.*;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.HorarioSolucionDTO;
import mx.sih.modelo.dto.HorarioSolucionMasivaDTO;
import mx.sih.modelo.dto.ValidacionDTO;
import mx.sih.modelo.dto.ResultadoValidacionDTO;
import mx.sih.modelo.dto.CambioManualDTO;
import mx.sih.modelo.dto.SolicitudManualDTO;
import mx.sih.modelo.dto.ResultadoManualDTO;
import mx.sih.modelo.solver.AsignacionHorario;
import mx.sih.modelo.solver.ClaseNoAsignadaDTO;
import mx.sih.modelo.solver.HorarioSolution;
import mx.sih.modelo.solver.HorarioSolverService;
import mx.sih.modelo.solver.OcupacionExterna;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mx.sih.modelo.dto.AnalisisCuelloBotellaDTO;

@Service
public class HorarioServicio {

    private static final Logger logger = LoggerFactory.getLogger(HorarioServicio.class);

    private final AsignacionRepositorio asignacionRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final HorarioRepositorio horarioRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio;
    private final HorarioSolverService solverService;
    private final AulaRepositorio aulaRepositorio;

    public HorarioServicio(AsignacionRepositorio asignacionRepositorio,
                           TurnoHorarioRepositorio turnoHorarioRepositorio,
                           HorarioRepositorio horarioRepositorio,
                           GrupoRepositorio grupoRepositorio,
                           SemestreRepositorio semestreRepositorio,
                           DisponibilidadMaestroRepositorio disponibilidadRepositorio,
                           DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio,
                           HorarioSolverService solverService,
                           AulaRepositorio aulaRepositorio) {
        this.asignacionRepositorio = asignacionRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.horarioRepositorio = horarioRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.disponibilidadGrupoRepositorio = disponibilidadGrupoRepositorio;
        this.solverService = solverService;
        this.aulaRepositorio = aulaRepositorio;
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
    // CONSTRUCCIÓN DE PLANIFICACIONES
    // ============================================================

    /**
     * Construye las instancias de {@link AsignacionHorario} con su rango de
     * bloques válidos precalculado.
     *
     * <h2>Fail-first ordering</h2>
     * Las entidades se ordenan con la estrategia <b>fail-first</b>:
     * <ol>
     *   <li>Primero las asignaciones de maestros con <b>mayor presión</b>
     *       (horas_asignadas / bloques_disponibles más cercano a 1.0). Esos
     *       maestros tienen pocas alternativas y deben colocarse cuando el
     *       tablero está casi vacío.</li>
     *   <li>Dentro del mismo maestro, las asignaciones con <b>menos bloques
     *       válidos</b> van primero (MRV clásico).</li>
     *   <li>Desempate estable por asignacionId + numeroHora para que la
     *       búsqueda sea reproducible.</li>
     * </ol>
     * Esto reduce drásticamente el score tras la fase de Construction Heuristic
     * cuando el problema es factible pero está apretado.
     */
    private List<AsignacionHorario> construirPlanificaciones(
            List<Asignacion> asignaciones,
            List<TurnoHorario> bloquesTurno,
            List<DisponibilidadGrupo> dispGrupos,
            List<DisponibilidadMaestro> dispMaestros) {

        Map<Long, Set<Long>> bloquesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            if (Boolean.FALSE.equals(dg.getDisponible())) continue;
            bloquesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }

        Map<Long, Set<Long>> bloquesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            if (Boolean.FALSE.equals(dm.getDisponible())) continue;
            bloquesPorMaestro
                    .computeIfAbsent(dm.getMaestro().getMaestroId(), k -> new HashSet<>())
                    .add(dm.getTurnoHorario().getId());
        }

        List<AsignacionHorario> planificaciones = new ArrayList<>();
        List<String> sinBloquesValidos = new ArrayList<>();

        for (Asignacion asignacion : asignaciones) {
            Long grupoId = asignacion.getGrupo().getGrupoId();
            Long maestroId = asignacion.getMaestro().getMaestroId();
            Long turnoId = asignacion.getGrupo().getTurno().getTurnoId();

            Set<Long> dispGrupo = bloquesPorGrupo.getOrDefault(grupoId, Set.of());
            Set<Long> dispMaestro = bloquesPorMaestro.getOrDefault(maestroId, Set.of());

            // Una entidad por SESIÓN del patrón ("2,1,1" son tres), no una por hora.
            List<Integer> duraciones = duracionesDe(asignacion);
            int numeroSesion = 0;
            for (Integer duracion : duraciones) {
                numeroSesion++;
                List<TurnoHorario> inicios = new ArrayList<>();
                java.util.Map<Long, List<TurnoHorario>> ventanas = ventanasDe(
                        bloquesTurno, turnoId, duracion, dispGrupo, dispMaestro, inicios);
                if (ventanas.isEmpty()) {
                    sinBloquesValidos.add(String.format(
                            "Grupo %s · %s · Maestro %s · sesión de %d h",
                            asignacion.getGrupo().getNombre(),
                            asignacion.getMateria().getNombre(),
                            asignacion.getMaestro().getNombreCompleto(),
                            duracion));
                    continue;
                }
                AsignacionHorario ah = new AsignacionHorario(
                        asignacion.getAsignacionId(),
                        grupoId,
                        asignacion.getGrupo().getNombre(),
                        asignacion.getMateria().getMateriaId(),
                        asignacion.getMateria().getNombre(),
                        asignacion.getMateria().getClave(),
                        maestroId,
                        asignacion.getMaestro().getNombreCompleto(),
                        asignacion.getAula().getAulaId(),
                        asignacion.getAula().getNombre(),
                        Boolean.TRUE.equals(asignacion.getAula().getTaller()),
                        asignacion.getColorHex(),
                        asignacion.getDistribucion(),
                        numeroSesion,
                        duracion,
                        turnoId
                );
                ah.setBloquesValidos(inicios);
                ah.setVentana(ventanas);
                planificaciones.add(ah);
            }
        }

        if (!sinBloquesValidos.isEmpty()) {
            // Ya no se aborta: son sesiones concretas sin ventana. Quedan pendientes y la pantalla
            // las muestra en la tabla de no colocadas.
            logger.warn("⚠️ {} sesión(es) sin ventana compatible (quedarán pendientes):\n  • {}",
                    sinBloquesValidos.size(), String.join("\n  • ", sinBloquesValidos));
        }

        // ─────────────────────────────────────────────────────────
        // 🔥 FAIL-FIRST: calcular presión por maestro
        // presión = horas_asignadas / bloques_disponibles
        //   - 0.0 → sin carga
        //   - 1.0 → al borde (necesita exactamente todos sus bloques)
        //   - >1.0 → infactible
        // Cuanto más alta la presión, más urgente colocar al maestro.
        // ─────────────────────────────────────────────────────────
        Map<Long, Integer> horasPorMaestro = new HashMap<>();
        for (Asignacion a : asignaciones) {
            horasPorMaestro.merge(a.getMaestro().getMaestroId(), a.getHoras(), Integer::sum);
        }

        Map<Long, Integer> bloquesDisponiblesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            if (Boolean.FALSE.equals(dm.getDisponible())) continue;
            bloquesDisponiblesPorMaestro.merge(dm.getMaestro().getMaestroId(), 1, Integer::sum);
        }

        Map<Long, Double> presionPorMaestro = new HashMap<>();
        for (Long maestroId : horasPorMaestro.keySet()) {
            int horas = horasPorMaestro.getOrDefault(maestroId, 0);
            int bloques = bloquesDisponiblesPorMaestro.getOrDefault(maestroId, 0);
            double presion = bloques > 0
                    ? (double) horas / bloques
                    : Double.MAX_VALUE;
            presionPorMaestro.put(maestroId, presion);
        }

        // 🔥 Ordenación fail-first:
        //  1. Maestros con mayor presión primero (desc por presión)
        //  2. Dentro del mismo maestro, menos bloques válidos primero (asc)
        //  3. Desempate estable (asignacionId + numeroHora)
        // Ordenacion por ETAPAS (lo mas restringido primero), en el orden acordado:
        //  1. Clases en AULA DE TALLER: hay pocas (los T. COMP) y suelen pedir sesiones largas,
        //     asi que son las que antes se quedan sin hueco.
        //  2. Clases con alguna sesion de 2+ horas seguidas: una sesion doble solo cabe donde hay
        //     dos bloques contiguos libres del grupo, del maestro y del aula a la vez.
        //  3. Maestros apretados (menos de 4 bloques libres sobre sus horas): si su clase no entra
        //     en sus pocos huecos, el problema se vuelve infactible.
        //  4. Presion del maestro (horas/bloques) desc: el resto del fail-first que ya existia.
        //  5. Menos bloques validos primero y desempate estable.
        planificaciones.sort(Comparator
                .comparingInt((AsignacionHorario ah) -> Boolean.TRUE.equals(ah.getTaller()) ? 0 : 1)
                .thenComparingInt(ah -> (ah.getDuracion() != null && ah.getDuracion() >= 2) ? 0 : 1)
                .thenComparingInt(ah -> maestroApretado(ah.getMaestroId(),
                        horasPorMaestro, bloquesDisponiblesPorMaestro) ? 0 : 1)
                .thenComparingDouble(ah -> -presionPorMaestro.getOrDefault(ah.getMaestroId(), 0.0))
                .thenComparingInt(ah -> ah.getBloquesValidos().size())
                .thenComparing(AsignacionHorario::getAsignacionId)
                .thenComparing(AsignacionHorario::getNumeroHora));

        // 🔥 Log del top 5 de maestros con mayor presión
        List<Map.Entry<Long, Double>> top5Presion = presionPorMaestro.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .toList();

        if (!top5Presion.isEmpty()) {
            logger.info("═══ Top 5 maestros por presión (fail-first) ═══");
            top5Presion.forEach(e -> {
                String nombre = asignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(e.getKey()))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst().orElse("ID " + e.getKey());
                int horas = horasPorMaestro.getOrDefault(e.getKey(), 0);
                int bloques = bloquesDisponiblesPorMaestro.getOrDefault(e.getKey(), 0);

                String icono;
                if (e.getValue() >= 0.95) icono = "🔴";
                else if (e.getValue() >= 0.85) icono = "🟠";
                else if (e.getValue() >= 0.70) icono = "🟡";
                else icono = "🟢";

                logger.info("  {} {} → presión {} ({}h / {} bloques, margen {})",
                        icono, nombre,
                        String.format("%.2f", e.getValue()),
                        horas, bloques, bloques - horas);
            });
        }

        if (!planificaciones.isEmpty()) {
            int min = planificaciones.stream()
                    .mapToInt(a -> a.getBloquesValidos().size()).min().orElse(0);
            int max = planificaciones.stream()
                    .mapToInt(a -> a.getBloquesValidos().size()).max().orElse(0);
            double avg = planificaciones.stream()
                    .mapToInt(a -> a.getBloquesValidos().size()).average().orElse(0);

            logger.info("Planificaciones construidas: {} entidades · " +
                            "bloques válidos por entidad: min={} max={} avg={}",
                    planificaciones.size(), min, max, String.format("%.1f", avg));

            if (min == 1) {
                logger.warn("⚠️ Hay entidades con SOLO 1 bloque válido. " +
                        "Cualquier conflicto con otra entidad hará el problema infactible.");
            }
        }

        return planificaciones;
    }

    // ============================================================
    // GENERAR HORARIO PARA UN GRUPO
    // ============================================================
    @Transactional
    public HorarioSolucionDTO generarHorario(Long grupoId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();

        logger.info("Generando horario para grupo {} en semestre {}", grupoId, semestreId);

        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(grupoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado"));

        List<Asignacion> asignaciones = asignacionRepositorio
                .findByGrupoIdAndSemestreId(grupoId, semestreId);

        if (asignaciones.isEmpty()) {
            logger.warn("Grupo {} sin asignaciones en semestre {}", grupoId, semestreId);
            HorarioSolucionDTO dto = new HorarioSolucionDTO();
            dto.setGrupoId(grupoId);
            dto.setGrupoNombre(grupo.getNombre());
            dto.setTotalClasesAsignadas(0);
            dto.setSemestreId(semestreId);
            dto.setSemestreNombre(semestre.getNombre());
            dto.setFechaGeneracion(java.time.LocalDateTime.now());
            return dto;
        }

        Long turnoId = grupo.getTurno().getTurnoId();
        List<TurnoHorario> bloques = turnoHorarioRepositorio
                .findClasesByTurnoIdAndSemestreId(turnoId, semestreId);

        if (bloques.isEmpty()) {
            throw new NegocioExcepcion("El turno no tiene bloques horarios en este semestre.");
        }

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

        List<DisponibilidadGrupo> disponibilidadesGrupo =
                disponibilidadGrupoRepositorio.findDisponiblesByGrupoIdAndSemestreId(
                        grupoId, escuelaId, semestreId);

        if (disponibilidadesGrupo.isEmpty()) {
            throw new NegocioExcepcion(
                    "El grupo '" + grupo.getNombre() + "' no tiene bloques configurados. " +
                            "Configura la disponibilidad del grupo antes de generar el horario."
            );
        }

        List<AsignacionHorario> planificaciones = construirPlanificaciones(
                asignaciones, bloques, disponibilidadesGrupo, disponibilidades);

        HorarioSolution problem = new HorarioSolution(
                bloques, disponibilidades, disponibilidadesGrupo, planificaciones);
        problem.setGrupoId(grupoId);
        problem.setSemestreId(semestreId);

        // 🔥 OCUPACIONES DE OTROS GRUPOS: regenerar un grupo construye el problema solo con SUS
        // asignaciones, así que el solver no veía que ese maestro o ese aula ya están ocupados por
        // otro grupo; terminaba en 0hard y la base de datos rechazaba el guardado con
        // no_solape_maestro_bloque (rollback y error al usuario). Con esto lo ve y lo evita.
        Set<Long> bloquesDelTurno = bloques.stream()
                .map(TurnoHorario::getId)
                .collect(Collectors.toSet());
        List<OcupacionExterna> ocupacionesExternas = horarioRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, semestreId).stream()
                .filter(h -> h.getGrupo() != null && !grupoId.equals(h.getGrupo().getGrupoId()))
                .filter(h -> h.getVersion() == null || h.getVersion() == 1)
                .filter(h -> h.getTurnoHorario() != null
                        && bloquesDelTurno.contains(h.getTurnoHorario().getId()))
                .map(h -> new OcupacionExterna(
                        h.getMaestroId(),
                        h.getAula() != null ? h.getAula().getAulaId() : null,
                        h.getTurnoHorario().getId()))
                .collect(Collectors.toList());
        problem.setOcupacionesExternas(ocupacionesExternas);
        logger.info("Grupo {} → ocupaciones de otros grupos consideradas: {}",
                grupo.getNombre(), ocupacionesExternas.size());

        // Perfil "un grupo": límite corto (app.solver.grupo.*)
        HorarioSolution solution = solverService.resolverGrupo(problem);
        HardMediumSoftScore score = (HardMediumSoftScore) solution.getScore();

        logger.info("Grupo {} → score final {}", grupo.getNombre(), score);

        if (score != null && score.hardScore() < 0) {
            List<ClaseNoAsignadaDTO> noAsignadas = extraerNoAsignadas(solution, asignaciones);

            logger.error("🚫 Solver devolvió solución INFACTIBLE para grupo {} " +
                            "({}hard/{}medium/{}soft). No se persiste nada.",
                    grupo.getNombre(),
                    score.hardScore(), score.mediumScore(), score.softScore());

            try {
                Map<String, String> violaciones = solverService.analizarViolaciones(solution);
                logger.error("=== VIOLACIONES HARD DETECTADAS (grupo {}) ===", grupo.getNombre());
                violaciones.forEach((constraint, detalle) ->
                        logger.error("  • {} → {}", constraint, detalle));
            } catch (Exception ex) {
                logger.warn("No se pudo generar el desglose de violaciones", ex);
            }

            throw new NegocioExcepcion(
                    "horario_infactible",
                    "No se pudo generar un horario sin solapamientos para el grupo '" +
                            grupo.getNombre() + "'. " +
                            (noAsignadas.isEmpty()
                                    ? "Revisa la disponibilidad de maestros y bloques del grupo."
                                    : "Hay " + noAsignadas.size() + " clase(s) que no caben. " +
                                    "Revisa la disponibilidad o reduce horas asignadas.")
            );
        }

        horarioRepositorio.deleteByGrupoIdAndVersionAndSemestreId(grupoId, 1, escuelaId, semestreId);

        PersistenciaResultado resultado = persistirAsignaciones(
                solution, asignaciones, escuelaId, semestre, grupoId);

        logger.info("Grupo {} → {} clases guardadas, {} sin asignar",
                grupo.getNombre(), resultado.guardados, resultado.noAsignadas.size());

        HorarioSolucionDTO dto = new HorarioSolucionDTO();
        dto.setGrupoId(grupoId);
        dto.setGrupoNombre(grupo.getNombre());
        dto.setScore(score);
        dto.setFechaGeneracion(solution.getFechaGeneracion());
        dto.setTotalClasesAsignadas(resultado.guardados);
        dto.setTotalClasesNoAsignadas(resultado.noAsignadas.size());
        dto.setClasesNoAsignadas(resultado.noAsignadas);
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        return dto;
    }

    // ============================================================
    // Persistencia
    // ============================================================

    private record PersistenciaResultado(int guardados, List<ClaseNoAsignadaDTO> noAsignadas) {}

    private PersistenciaResultado persistirAsignaciones(
            HorarioSolution solution,
            List<Asignacion> asignacionesOriginales,
            Long escuelaId,
            Semestre semestre,
            Long grupoId) {

        Map<Long, Asignacion> mapaAsignaciones = asignacionesOriginales.stream()
                .collect(Collectors.toMap(Asignacion::getAsignacionId, a -> a));

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        List<Horario> filas = new ArrayList<>();
        List<ClaseNoAsignadaDTO> noAsignadas = new ArrayList<>();

        for (AsignacionHorario ah : solution.getAsignaciones()) {
            Asignacion original = mapaAsignaciones.get(ah.getAsignacionId());
            if (original == null) continue;

            if (ah.getBloqueHorario() == null) {
                noAsignadas.add(convertirANoAsignada(original, ah));
                continue;
            }

            filas.addAll(mapearAHorario(ah, original, escuela, semestre));
        }

        try {
            horarioRepositorio.saveAll(filas);
            horarioRepositorio.flush();
        } catch (DataIntegrityViolationException e) {
            String mensajeSolape = MensajeErrorUtil.detectarConstraintSolape(e);
            if (mensajeSolape != null) {
                logger.error("🚫 Constraint de solapamiento violada al persistir grupo {}: {}",
                        grupoId, mensajeSolape);
                throw new NegocioExcepcion(
                        MensajeErrorUtil.codigoDesdeConstraint(e),
                        mensajeSolape + " Regenera el horario del grupo.",
                        e);
            }
            logger.error("DataIntegrityViolationException al persistir grupo {}: {}",
                    grupoId,
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
            throw e;
        }

        return new PersistenciaResultado(filas.size(), noAsignadas);
    }

    /** Un bloque puede seguir a otro dentro de la misma sesión si no hay un descanso largo. */
    private static final long TOLERANCIA_VENTANA_MINUTOS = 50;

    /** Duraciones de las sesiones de una asignación; si el patrón no cuadra, todas de 1 hora. */
    private static List<Integer> duracionesDe(Asignacion asignacion) {
        List<Integer> duraciones = new ArrayList<>();
        String patron = asignacion.getDistribucion();
        int horas = asignacion.getHoras() == null ? 0 : asignacion.getHoras();
        if (patron != null && patron.trim().matches("\\d+(\\s*,\\s*\\d+)*")) {
            int suma = 0;
            for (String parte : patron.split(",")) {
                int k = Integer.parseInt(parte.trim());
                if (k > 0) {
                    duraciones.add(k);
                    suma += k;
                }
            }
            if (suma != horas) {
                duraciones.clear();
            }
        }
        if (duraciones.isEmpty()) {
            for (int i = 0; i < horas; i++) {
                duraciones.add(1);
            }
        }
        return duraciones;
    }

    /**
     * Ventanas válidas de una sesión de {@code duracion} horas: para cada bloque de inicio, los
     * bloques que ocuparía. Todos tienen que estar en la disponibilidad del grupo y del maestro, y
     * caer dentro de un tramo continuo del turno (sin descansos largos en medio).
     *
     * Devuelve un mapa ordenado por el orden del turno, para que la heurística de construcción
     * pruebe los inicios en un orden estable.
     */
    private static java.util.Map<Long, List<TurnoHorario>> ventanasDe(
            List<TurnoHorario> bloquesTurno, Long turnoId, int duracion,
            java.util.Set<Long> dispGrupo, java.util.Set<Long> dispMaestro,
            List<TurnoHorario> iniciosOut) {

        java.util.Map<Long, List<TurnoHorario>> ventanas = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, List<TurnoHorario>> porDia = new java.util.LinkedHashMap<>();
        bloquesTurno.stream()
                .filter(b -> b.getTurno() != null && b.getTurno().getTurnoId().equals(turnoId))
                .sorted(Comparator.comparing(TurnoHorario::getDiaSemana)
                        .thenComparing(TurnoHorario::getOrden))
                .forEach(b -> porDia.computeIfAbsent(b.getDiaSemana(), k -> new ArrayList<>()).add(b));

        for (List<TurnoHorario> dia : porDia.values()) {
            int i = 0;
            while (i < dia.size()) {
                int j = i;
                while (j + 1 < dia.size()) {
                    long gap = java.time.Duration
                            .between(dia.get(j).getHoraFin(), dia.get(j + 1).getHoraInicio())
                            .toMinutes();
                    if (gap <= TOLERANCIA_VENTANA_MINUTOS) {
                        j++;
                    } else {
                        break;
                    }
                }
                for (int ini = i; ini + duracion - 1 <= j; ini++) {
                    List<TurnoHorario> bloques = new ArrayList<>();
                    boolean sirve = true;
                    for (int x = ini; x < ini + duracion; x++) {
                        TurnoHorario b = dia.get(x);
                        if (!dispGrupo.contains(b.getId()) || !dispMaestro.contains(b.getId())) {
                            sirve = false;
                            break;
                        }
                        bloques.add(b);
                    }
                    if (sirve) {
                        ventanas.put(dia.get(ini).getId(), bloques);
                        iniciosOut.add(dia.get(ini));
                    }
                }
                i = j + 1;
            }
        }
        return ventanas;
    }

    private ClaseNoAsignadaDTO convertirANoAsignada(Asignacion original, AsignacionHorario ah) {
        ClaseNoAsignadaDTO noAsignada = new ClaseNoAsignadaDTO();
        noAsignada.setAsignacionId(original.getAsignacionId());
        noAsignada.setGrupoId(original.getGrupo().getGrupoId());
        noAsignada.setGrupoNombre(original.getGrupo().getNombre());
        noAsignada.setMateriaId(original.getMateria().getMateriaId());
        noAsignada.setMateriaNombre(original.getMateria().getNombre());
        noAsignada.setMateriaClave(original.getMateria().getClave());
        noAsignada.setMaestroId(original.getMaestro().getMaestroId());
        noAsignada.setMaestroNombre(original.getMaestro().getNombreCompleto());
        noAsignada.setAulaId(original.getAula().getAulaId());
        noAsignada.setAulaNombre(original.getAula().getNombre());
        noAsignada.setMotivo("Sin bloque compatible disponible");
        return noAsignada;
    }

    /**
     * Una sesión colocada ocupa varios bloques, así que produce una fila por bloque: el horario
     * guardado sigue siendo de horas, como lo espera el tablero manual.
     */
    private List<Horario> mapearAHorario(AsignacionHorario ah, Asignacion original,
                                          Escuela escuela, Semestre semestre) {
        List<Horario> filas = new ArrayList<>();
        for (TurnoHorario bloque : ah.getBloquesOcupados()) {
            Horario horario = new Horario();
            horario.setEscuela(escuela);
            horario.setGrupo(original.getGrupo());
            horario.setAsignacion(original);
            horario.setTurnoHorario(bloque);
            horario.setAula(original.getAula());
            horario.setMaestroId(original.getMaestro().getMaestroId());
            horario.setVersion(1);
            horario.setSemestre(semestre);
            filas.add(horario);
        }
        return filas;
    }

    private List<ClaseNoAsignadaDTO> extraerNoAsignadas(
            HorarioSolution solution, List<Asignacion> asignacionesOriginales) {

        Map<Long, Asignacion> mapa = asignacionesOriginales.stream()
                .collect(Collectors.toMap(Asignacion::getAsignacionId, a -> a));

        List<ClaseNoAsignadaDTO> resultado = new ArrayList<>();
        java.util.Set<Long> yaListadas = new java.util.HashSet<>();
        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() == null && yaListadas.add(ah.getAsignacionId())) {
                Asignacion original = mapa.get(ah.getAsignacionId());
                if (original != null) {
                    resultado.add(convertirANoAsignada(original, ah));
                }
            }
        }
        return resultado;
    }

    // ============================================================
    // CONSULTAS
    // ============================================================
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

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);

        if (grupos.isEmpty()) {
            throw new NegocioExcepcion(
                    turnoFiltro != null
                            ? "No hay grupos activos en el turno seleccionado"
                            : "No hay grupos activos en el semestre");
        }

        List<Asignacion> todasAsignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            todasAsignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());
        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long turnoId : turnoIds) {
            bloques.addAll(
                    turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId));
        }

        List<Long> maestroIds = todasAsignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .collect(Collectors.toList());
        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        for (Long m : maestroIds) {
            dispMaestros.addAll(
                    disponibilidadRepositorio.findDisponiblesByMaestroIdAndSemestreId(
                            m, escuelaId, semestreId));
        }

        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        for (Grupo g : grupos) {
            dispGrupos.addAll(
                    disponibilidadGrupoRepositorio.findDisponiblesByGrupoIdAndSemestreId(
                            g.getGrupoId(), escuelaId, semestreId));
        }

        logger.info("=== GENERACIÓN MASIVA === grupos={} asignaciones={} bloques={} dispMaestros={} dispGrupos={}",
                grupos.size(), todasAsignaciones.size(), bloques.size(),
                dispMaestros.size(), dispGrupos.size());

        // Pre-validación (con los chequeos ajustados)
        List<String> problemas = analizarFactibilidad(
                grupos, todasAsignaciones, dispGrupos, dispMaestros);

        if (!problemas.isEmpty()) {
            problemas.forEach(p -> logger.warn("  {}", p));
            List<String> criticos = problemas.stream()
                    .filter(p -> p.startsWith("❌"))
                    .collect(Collectors.toList());
            if (!criticos.isEmpty()) {
                throw new NegocioExcepcion(
                        "No se puede generar el horario por los siguientes problemas:\n" +
                                String.join("\n", criticos));
            }
        }

        List<AsignacionHorario> planificaciones = construirPlanificaciones(
                todasAsignaciones, bloques, dispGrupos, dispMaestros);

        logger.debug("Total asignaciones={} instancias a planificar={}",
                todasAsignaciones.size(), planificaciones.size());

        HorarioSolution problem = new HorarioSolution(
                bloques, dispMaestros, dispGrupos, planificaciones);
        problem.setSemestreId(semestreId);

        long inicio = System.currentTimeMillis();
        // Perfil "masiva": todos los grupos del semestre/turno (app.solver.masiva.*)
        HorarioSolution solution = solverService.resolverMasiva(problem);
        long tiempoMs = System.currentTimeMillis() - inicio;

        HardMediumSoftScore score = (HardMediumSoftScore) solution.getScore();
        logger.info("Score final masiva: {}", score);

        boolean factible = score != null && score.hardScore() >= 0;

        // 🛡️ RED DE SEGURIDAD: nunca reemplazar el horario guardado por uno que no coloca nada.
        // "factible" solo mira las violaciones HARD, y el solver puede devolver 0hard con TODAS las
        // clases sin colocar (medium = -6 por hora). Sin esto se borraba el horario anterior y se
        // insertaban 0 filas: pasó al devolver mal la mejor corrida de los intentos.
        int horasNuevas = 0;
        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() != null) {
                horasNuevas += ah.getDuracion() == null ? 1 : ah.getDuracion();
            }
        }
        Set<Long> bloquesDelTurno = bloques.stream()
                .map(TurnoHorario::getId)
                .collect(Collectors.toSet());
        int horasGuardadasAntes = (int) horarioRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, semestreId).stream()
                .filter(h -> h.getTurnoHorario() != null
                        && bloquesDelTurno.contains(h.getTurnoHorario().getId()))
                .filter(h -> h.getVersion() == null || h.getVersion() == 1)
                .count();

        if (factible && horasNuevas == 0 && horasGuardadasAntes > 0) {
            logger.error("🚫 La generación terminó con 0 horas colocadas ({}hard/{}medium) y hay {} "
                            + "horas guardadas: se CONSERVA el horario anterior.",
                    score.hardScore(), score.mediumScore(), horasGuardadasAntes);
            throw new NegocioExcepcion("generacion_vacia",
                    "La generación terminó sin colocar ninguna clase, así que se conservó el horario "
                            + "anterior. Vuelve a intentarlo o revisa el log del servidor.");
        }
        if (factible && horasGuardadasAntes > 0 && horasNuevas < horasGuardadasAntes / 2) {
            logger.warn("⚠️ La generación nueva coloca {} horas frente a las {} guardadas: si se "
                            + "guarda, reemplazará el horario anterior.", horasNuevas, horasGuardadasAntes);
        } else if (factible) {
            logger.info("Horas colocadas por la generación: {} (guardadas antes: {})",
                    horasNuevas, horasGuardadasAntes);
        }

        if (!factible) {
            logger.error("🚫 Solución masiva INFACTIBLE ({}hard/{}medium/{}soft). No se persiste nada.",
                    score.hardScore(), score.mediumScore(), score.softScore());
        }

        PersistenciaResultado resultado;
        if (factible) {
            for (Grupo g : grupos) {
                horarioRepositorio.deleteByGrupoIdAndVersionAndSemestreId(
                        g.getGrupoId(), 1, escuelaId, semestreId);
            }
            resultado = persistirAsignaciones(
                    solution, todasAsignaciones, escuelaId, semestre, null);
            logger.info("Horarios guardados: {}", resultado.guardados());
        } else {
            int asignadasCount = 0;
            for (AsignacionHorario ah : solution.getAsignaciones()) {
                if (ah.getBloqueHorario() != null) {
                    asignadasCount += ah.getDuracion() == null ? 1 : ah.getDuracion();
                }
            }
            resultado = new PersistenciaResultado(asignadasCount, new ArrayList<>());
        }

        Map<Long, Integer> asignadasPorAsignacion = new HashMap<>();
        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() != null) {
                asignadasPorAsignacion.merge(ah.getAsignacionId(),
                        ah.getDuracion() == null ? 1 : ah.getDuracion(), Integer::sum);
            }
        }

        List<HorarioSolucionMasivaDTO.DetalleAsignacionDTO> detalleAsignaciones = new ArrayList<>();
        for (Asignacion asignacion : todasAsignaciones) {
            HorarioSolucionMasivaDTO.DetalleAsignacionDTO d =
                    new HorarioSolucionMasivaDTO.DetalleAsignacionDTO();

            d.setAsignacionId(asignacion.getAsignacionId());
            d.setGrupoId(asignacion.getGrupo().getGrupoId());
            d.setGrupoNombre(asignacion.getGrupo().getNombre());
            d.setGrupoGrado(asignacion.getGrupo().getGrado());

            Especialidad esp = asignacion.getGrupo().getEspecialidad();
            d.setEspecialidadNombre(esp != null ? esp.getNombre() : null);

            d.setMateriaId(asignacion.getMateria().getMateriaId());
            d.setMateriaClave(asignacion.getMateria().getClave());
            d.setMateriaNombre(asignacion.getMateria().getNombre());

            d.setMaestroId(asignacion.getMaestro().getMaestroId());
            d.setMaestroNombre(asignacion.getMaestro().getTituloNombreCompleto());

            d.setAulaId(asignacion.getAula().getAulaId());
            d.setAulaNombre(asignacion.getAula().getNombre());

            d.setTurnoId(asignacion.getTurno().getTurnoId());
            d.setTurnoNombre(asignacion.getTurno().getNombre());

            int esperadas = asignacion.getHoras() != null ? asignacion.getHoras() : 0;
            int asignadas = asignadasPorAsignacion.getOrDefault(asignacion.getAsignacionId(), 0);

            d.setHorasEsperadas(esperadas);
            d.setClasesAsignadas(asignadas);
            d.setClasesSinAsignar(Math.max(0, esperadas - asignadas));

            if (asignadas == esperadas && esperadas > 0) {
                d.setEstado("OK");
                d.setMotivo(null);
            } else if (asignadas > 0) {
                d.setEstado("PARCIAL");
                d.setMotivo("Solo se asignaron " + asignadas + " de " + esperadas + " horas");
            } else {
                d.setEstado("SIN_ASIGNAR");
                d.setMotivo(factible
                        ? "No se pudo asignar ninguna hora"
                        : "No se asignó: el horario global es infactible (ver conflictos)");
            }

            detalleAsignaciones.add(d);
        }

        detalleAsignaciones.sort(Comparator
                .comparing((HorarioSolucionMasivaDTO.DetalleAsignacionDTO d) -> "OK".equals(d.getEstado()))
                .thenComparing(HorarioSolucionMasivaDTO.DetalleAsignacionDTO::getGrupoNombre)
                .thenComparing(HorarioSolucionMasivaDTO.DetalleAsignacionDTO::getMateriaClave));

        List<HorarioSolucionMasivaDTO.ViolacionConstraintDTO> violacionesDto = new ArrayList<>();
        List<HorarioSolucionMasivaDTO.DetalleConflictoDTO> conflictosDetectados = new ArrayList<>();

        if (!factible) {
            try {
                Map<String, String> violaciones = solverService.analizarViolaciones(solution);
                logger.error("=== VIOLACIONES HARD DETECTADAS ===");
                violaciones.forEach((constraint, detalle) -> {
                    logger.error("  • {} → {}", constraint, detalle);
                    violacionesDto.add(new HorarioSolucionMasivaDTO.ViolacionConstraintDTO(constraint, detalle));
                });
            } catch (Exception ex) {
                logger.warn("No se pudo generar el desglose de violaciones", ex);
            }

            conflictosDetectados = extraerConflictos(solution);
            logger.error("=== CONFLICTOS ESPECÍFICOS ({}) ===", conflictosDetectados.size());
            conflictosDetectados.forEach(c ->
                    logger.error("  • [{}] {} → {}", c.getTipo(), c.getBloqueTexto(), c.getTitulo()));
        }

        HorarioSolucionMasivaDTO dto = new HorarioSolucionMasivaDTO();
        dto.setFactible(factible);
        dto.setMotivoInfactibilidad(factible ? null
                : "No se pudo generar un horario sin solapamientos. Score: " + score.hardScore() + " hard.");
        dto.setViolacionesHard(violacionesDto);
        dto.setConflictosDetectados(conflictosDetectados);
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        dto.setFechaGeneracion(java.time.LocalDateTime.now());
        dto.setScore(score);
        if (score != null) {
            dto.setHardScore(score.hardScore());
            dto.setMediumScore(score.mediumScore());
            dto.setSoftScore(score.softScore());
        }
        dto.setTotalGrupos(grupos.size());
        dto.setTotalAsignaciones(todasAsignaciones.size());
        dto.setTiempoMs(tiempoMs);
        dto.setTiempoSegundos(tiempoMs / 1000);
        dto.setDetalleAsignaciones(detalleAsignaciones);

        Map<Long, Integer> clasesPorGrupo = new HashMap<>();
        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() == null) continue;
            clasesPorGrupo.merge(ah.getGrupoId(), 1, Integer::sum);
        }

        int gruposConHorario = 0;
        int gruposSinAsignaciones = 0;
        int gruposSinDisponibilidad = 0;

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

        dto.setTotalClasesAsignadas(resultado.guardados());
        dto.setGruposConHorario(gruposConHorario);
        dto.setGruposSinAsignaciones(gruposSinAsignaciones);
        dto.setGruposSinDisponibilidad(gruposSinDisponibilidad);

        if (turnoFiltro != null) {
            Turno turno = grupos.get(0).getTurno();
            dto.setTurnoId(turno.getTurnoId());
            dto.setTurnoNombre(turno.getNombre());
        }

        return dto;
    }

    // ============================================================
    // EXTRACCIÓN DE CONFLICTOS
    // ============================================================

    private List<HorarioSolucionMasivaDTO.DetalleConflictoDTO> extraerConflictos(
            HorarioSolution solution) {

        List<HorarioSolucionMasivaDTO.DetalleConflictoDTO> conflictos = new ArrayList<>();

        Map<String, List<AsignacionHorario>> porGrupoBloque = new HashMap<>();
        Map<String, List<AsignacionHorario>> porMaestroBloque = new HashMap<>();
        Map<String, List<AsignacionHorario>> porAulaBloque = new HashMap<>();

        for (AsignacionHorario ah : solution.getAsignaciones()) {
            if (ah.getBloqueHorario() == null) continue;
            TurnoHorario b = ah.getBloqueHorario();
            String bloqueKey = b.getDiaSemana() + "|" + b.getHoraInicio();

            porGrupoBloque
                    .computeIfAbsent(ah.getGrupoId() + "|" + bloqueKey, k -> new ArrayList<>())
                    .add(ah);
            porMaestroBloque
                    .computeIfAbsent(ah.getMaestroId() + "|" + bloqueKey, k -> new ArrayList<>())
                    .add(ah);
            porAulaBloque
                    .computeIfAbsent(ah.getAulaId() + "|" + bloqueKey, k -> new ArrayList<>())
                    .add(ah);
        }

        for (var entry : porGrupoBloque.entrySet()) {
            List<AsignacionHorario> lista = entry.getValue();
            if (lista.size() > 1) {
                AsignacionHorario ref = lista.get(0);
                HorarioSolucionMasivaDTO.DetalleConflictoDTO d =
                        new HorarioSolucionMasivaDTO.DetalleConflictoDTO();
                d.setTipo("GRUPO");
                d.setTitulo("Grupo con " + lista.size() + " clases en el mismo bloque");
                d.setBloqueTexto(formatearBloque(ref.getBloqueHorario()));
                d.setGrupoNombre(ref.getGrupoNombre());
                d.setMaterias(lista.stream()
                        .map(a -> a.getMateriaClave() + " - " + a.getMateriaNombre())
                        .distinct()
                        .toList());
                d.setSugerencia("Revisa la disponibilidad del grupo: tiene " +
                        lista.size() + " clases demandando el mismo bloque.");
                conflictos.add(d);
            }
        }

        for (var entry : porMaestroBloque.entrySet()) {
            List<AsignacionHorario> lista = entry.getValue();
            if (lista.size() > 1) {
                AsignacionHorario ref = lista.get(0);
                HorarioSolucionMasivaDTO.DetalleConflictoDTO d =
                        new HorarioSolucionMasivaDTO.DetalleConflictoDTO();
                d.setTipo("MAESTRO");
                d.setTitulo("Maestro con " + lista.size() + " clases en el mismo bloque");
                d.setBloqueTexto(formatearBloque(ref.getBloqueHorario()));
                d.setMaestroNombre(ref.getMaestroNombre());
                d.setMaterias(lista.stream()
                        .map(a -> a.getGrupoNombre() + " · " + a.getMateriaClave())
                        .distinct()
                        .toList());
                d.setSugerencia("El maestro necesita estar en 2 grupos a la vez. " +
                        "Revisa su disponibilidad o reduce sus asignaciones.");
                conflictos.add(d);
            }
        }

        for (var entry : porAulaBloque.entrySet()) {
            List<AsignacionHorario> lista = entry.getValue();
            if (lista.size() > 1) {
                AsignacionHorario ref = lista.get(0);
                HorarioSolucionMasivaDTO.DetalleConflictoDTO d =
                        new HorarioSolucionMasivaDTO.DetalleConflictoDTO();
                d.setTipo("AULA");
                d.setTitulo("Aula ocupada por " + lista.size() + " clases en el mismo bloque");
                d.setBloqueTexto(formatearBloque(ref.getBloqueHorario()));
                d.setAulaNombre(ref.getAulaNombre());
                d.setMaterias(lista.stream()
                        .map(a -> a.getGrupoNombre() + " · " + a.getMateriaClave())
                        .distinct()
                        .toList());
                d.setSugerencia("Asigna otro aula o cambia el bloque de alguna clase.");
                conflictos.add(d);
            }
        }

        return conflictos;
    }

    private static String formatearBloque(TurnoHorario b) {
        if (b == null) return "Sin bloque";
        String[] dias = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
        String dia = (b.getDiaSemana() != null && b.getDiaSemana() >= 1 && b.getDiaSemana() <= 5)
                ? dias[b.getDiaSemana()] : "Día " + b.getDiaSemana();
        return dia + " " + b.getHoraInicio() + "-" + b.getHoraFin();
    }

    // ============================================================
    // VALIDACIÓN PÚBLICA
    // ============================================================
    public ResultadoValidacionDTO validarFactibilidad(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        List<ValidacionDTO> validaciones = new ArrayList<>();

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);

        if (grupos.isEmpty()) {
            validaciones.add(ValidacionDTO.error("grupos", "Grupos activos",
                    turnoFiltro != null
                            ? "No hay grupos activos en el turno seleccionado"
                            : "No hay grupos activos en el semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        validaciones.add(ValidacionDTO.ok("grupos", "Grupos activos",
                grupos.size() + " grupos activos"));

        List<Asignacion> asignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            asignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        if (asignaciones.isEmpty()) {
            validaciones.add(ValidacionDTO.error("asignaciones", "Asignaciones configuradas",
                    "Ningún grupo tiene asignaciones en este semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        long totalHoras = asignaciones.stream().mapToLong(Asignacion::getHoras).sum();
        validaciones.add(ValidacionDTO.ok("asignaciones", "Asignaciones configuradas",
                asignaciones.size() + " asignaciones · " + totalHoras + " horas a planificar"));

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());

        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long tId : turnoIds) {
            bloques.addAll(turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(tId, semestreId));
        }

        if (bloques.isEmpty()) {
            validaciones.add(ValidacionDTO.error("bloques", "Bloques horarios del turno",
                    "Los turnos de los grupos no tienen bloques configurados en este semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        validaciones.add(ValidacionDTO.ok("bloques", "Bloques horarios del turno",
                bloques.size() + " bloques de clase configurados"));

        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        List<String> gruposSinDisponibilidad = new ArrayList<>();
        for (Grupo g : grupos) {
            List<DisponibilidadGrupo> disp = disponibilidadGrupoRepositorio
                    .findDisponiblesByGrupoIdAndSemestreId(g.getGrupoId(), escuelaId, semestreId);
            if (disp.isEmpty()) {
                gruposSinDisponibilidad.add(g.getNombre());
            }
            dispGrupos.addAll(disp);
        }

        if (!gruposSinDisponibilidad.isEmpty()) {
            validaciones.add(ValidacionDTO.error("disponibilidad_grupos", "Disponibilidad de grupos",
                    "Los siguientes grupos no tienen bloques disponibles: " +
                            String.join(", ", gruposSinDisponibilidad)));
        } else {
            validaciones.add(ValidacionDTO.ok("disponibilidad_grupos", "Disponibilidad de grupos",
                    "Todos los grupos tienen bloques disponibles (" + dispGrupos.size() + " bloques en total)"));
        }

        // 🎯 CHEQUEO: suma de horas por grupo > bloques del grupo
        List<String> gruposSobrecargados = new ArrayList<>();
        Map<Long, Set<Long>> bloquesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            bloquesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }
        for (Grupo g : grupos) {
            int horasGrupo = asignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(g.getGrupoId()))
                    .mapToInt(Asignacion::getHoras)
                    .sum();
            int bloquesGrupo = bloquesPorGrupo.getOrDefault(g.getGrupoId(), Set.of()).size();
            if (horasGrupo > bloquesGrupo && bloquesGrupo > 0) {
                gruposSobrecargados.add(String.format(
                        "%s: %d horas vs %d bloques (faltan %d)",
                        g.getNombre(), horasGrupo, bloquesGrupo, horasGrupo - bloquesGrupo));
            }
        }
        if (!gruposSobrecargados.isEmpty()) {
            validaciones.add(ValidacionDTO.error("carga_grupos", "Carga total por grupo",
                    "Grupos con más horas asignadas que bloques disponibles:\n  " +
                            String.join("\n  ", gruposSobrecargados)));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_grupos", "Carga total por grupo",
                    "Ningún grupo está sobrecargado"));
        }

        // 🎯 CHEQUEO: suma de horas por materia > horas del catálogo
        List<String> excesoHoras = new ArrayList<>();
        Map<Long, Map<Long, List<Asignacion>>> porGrupoMateria = asignaciones.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getGrupo().getGrupoId(),
                        Collectors.groupingBy(a -> a.getMateria().getMateriaId())));

        for (var entryGrupo : porGrupoMateria.entrySet()) {
            for (var entryMat : entryGrupo.getValue().entrySet()) {
                List<Asignacion> lista = entryMat.getValue();
                if (lista.size() <= 1) continue;

                Asignacion ref = lista.get(0);
                int totalHorasAsignadas = lista.stream()
                        .mapToInt(Asignacion::getHoras)
                        .sum();
                Integer horasCatalogo = ref.getMateria().getHorasSemana();

                if (horasCatalogo == null || horasCatalogo <= 0) continue;

                if (totalHorasAsignadas > horasCatalogo) {
                    excesoHoras.add(String.format(
                            "%s · %s: %d asignaciones suman %d horas, catálogo permite %d",
                            ref.getGrupo().getNombre(),
                            ref.getMateria().getClave(),
                            lista.size(),
                            totalHorasAsignadas,
                            horasCatalogo));
                }
            }
        }
        if (!excesoHoras.isEmpty()) {
            validaciones.add(ValidacionDTO.error("exceso_horas_materia", "Horas por materia",
                    "Grupos con más horas asignadas que las permitidas por el catálogo:\n  " +
                            String.join("\n  ", excesoHoras)));
        } else {
            validaciones.add(ValidacionDTO.ok("exceso_horas_materia", "Horas por materia",
                    "Todas las materias respetan sus horas del catálogo"));
        }

        List<Long> maestroIds = asignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .toList();

        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        List<Long> maestrosSinDisponibilidad = new ArrayList<>();
        for (Long mId : maestroIds) {
            List<DisponibilidadMaestro> disp = disponibilidadRepositorio
                    .findDisponiblesByMaestroIdAndSemestreId(mId, escuelaId, semestreId);
            if (disp.isEmpty()) {
                maestrosSinDisponibilidad.add(mId);
            }
            dispMaestros.addAll(disp);
        }

        if (!maestrosSinDisponibilidad.isEmpty()) {
            List<String> nombres = asignaciones.stream()
                    .filter(a -> maestrosSinDisponibilidad.contains(a.getMaestro().getMaestroId()))
                    .map(a -> a.getMaestro().getNombreCompleto())
                    .distinct()
                    .toList();
            validaciones.add(ValidacionDTO.error("disponibilidad_maestros", "Disponibilidad de maestros",
                    "Sin disponibilidad: " + String.join(", ", nombres)));
        } else {
            validaciones.add(ValidacionDTO.ok("disponibilidad_maestros", "Disponibilidad de maestros",
                    "Todos los maestros (" + maestroIds.size() + ") tienen disponibilidad registrada"));
        }

        Set<String> maestroBloque = new HashSet<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            maestroBloque.add(dm.getMaestro().getMaestroId() + "-" + dm.getTurnoHorario().getId());
        }

        List<String> incompatibles = new ArrayList<>();
        List<String> insuficientes = new ArrayList<>();
        for (Asignacion a : asignaciones) {
            Set<Long> bloquesG = bloquesPorGrupo.getOrDefault(
                    a.getGrupo().getGrupoId(), Collections.emptySet());
            long compatibles = bloquesG.stream()
                    .filter(bId -> maestroBloque.contains(a.getMaestro().getMaestroId() + "-" + bId))
                    .count();
            if (compatibles == 0) {
                incompatibles.add(a.getGrupo().getNombre() + " · " +
                        a.getMateria().getNombre() + " · " +
                        a.getMaestro().getNombreCompleto());
            } else if (compatibles < a.getHoras()) {
                insuficientes.add(a.getGrupo().getNombre() + " · " +
                        a.getMateria().getNombre() + ": " +
                        compatibles + "/" + a.getHoras() + " horas");
            }
        }

        if (!incompatibles.isEmpty()) {
            validaciones.add(ValidacionDTO.error("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Asignaciones sin bloques compatibles:\n  " +
                            String.join("\n  ", incompatibles)));
        } else if (!insuficientes.isEmpty()) {
            validaciones.add(ValidacionDTO.advertencia("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Bloques insuficientes para cubrir todas las horas:\n  " +
                            String.join("\n  ", insuficientes)));
        } else {
            validaciones.add(ValidacionDTO.ok("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Todos los maestros tienen bloques compatibles con sus grupos"));
        }

        Map<Long, Integer> horasPorMaestro = new HashMap<>();
        for (Asignacion a : asignaciones) {
            horasPorMaestro.merge(a.getMaestro().getMaestroId(), a.getHoras(), Integer::sum);
        }
        Map<Long, Integer> bloquesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            bloquesPorMaestro.merge(dm.getMaestro().getMaestroId(), 1, Integer::sum);
        }

        List<String> sobrecargados = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : horasPorMaestro.entrySet()) {
            int horas = e.getValue();
            int disp = bloquesPorMaestro.getOrDefault(e.getKey(), 0);
            if (disp > 0 && disp < horas) {
                String nombre = asignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(e.getKey()))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst().orElse("ID " + e.getKey());
                sobrecargados.add(nombre + ": " + horas + " horas, " + disp + " bloques");
            }
        }

        if (!sobrecargados.isEmpty()) {
            validaciones.add(ValidacionDTO.error("carga_maestros", "Capacidad horaria de maestros",
                    "Maestros con más horas que bloques disponibles:\n  " +
                            String.join("\n  ", sobrecargados)));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_maestros", "Capacidad horaria de maestros",
                    "Ningún maestro está sobrecargado"));
        }

        List<String> distInvalidas = new ArrayList<>();
        for (Asignacion a : asignaciones) {
            String d = a.getDistribucion();
            if (d == null || d.isBlank()) continue;
            try {
                int suma = Arrays.stream(d.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .mapToInt(Integer::parseInt)
                        .sum();
                if (suma != a.getHoras()) {
                    distInvalidas.add(a.getMateria().getNombre() +
                            " (" + a.getGrupo().getNombre() + "): distribución '" + d +
                            "' suma " + suma + " pero tiene " + a.getHoras() + " horas");
                }
            } catch (NumberFormatException ex) {
                distInvalidas.add(a.getMateria().getNombre() +
                        " (" + a.getGrupo().getNombre() + "): distribución '" + d +
                        "' tiene formato inválido");
            }
        }

        if (!distInvalidas.isEmpty()) {
            validaciones.add(ValidacionDTO.advertencia("distribuciones", "Distribución de horas",
                    "Distribuciones inconsistentes:\n  " + String.join("\n  ", distInvalidas)));
        } else {
            validaciones.add(ValidacionDTO.ok("distribuciones", "Distribución de horas",
                    "Todas las distribuciones son coherentes"));
        }

        Set<Long> aulasUsadas = asignaciones.stream()
                .map(a -> a.getAula().getAulaId())
                .collect(Collectors.toSet());

        int maxGruposParalelos = grupos.size();
        int aulasDisponibles = aulasUsadas.size();

        if (aulasDisponibles < maxGruposParalelos) {
            validaciones.add(ValidacionDTO.advertencia("carga_aulas", "Carga de aulas",
                    "Hay " + maxGruposParalelos + " grupos pero solo " + aulasDisponibles +
                            " aulas en uso. Los grupos competirán por las mismas aulas durante el mismo bloque."));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_aulas", "Carga de aulas",
                    aulasDisponibles + " aulas disponibles para " + maxGruposParalelos + " grupos"));
        }

        long errores = validaciones.stream().filter(v -> "ERROR".equals(v.estado())).count();
        long advertencias = validaciones.stream().filter(v -> "ADVERTENCIA".equals(v.estado())).count();

        return new ResultadoValidacionDTO(
                validaciones, errores == 0, (int) errores, (int) advertencias);
    }

    /**
    * Analiza cuellos de botella por (grupo, bloque).
    */
    public AnalisisCuelloBotellaDTO analizarCuellosBotella(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);

        if (grupos.isEmpty()) {
            throw new NegocioExcepcion("No hay grupos activos en el semestre/turno");
        }

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());
        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long turnoId : turnoIds) {
            bloques.addAll(
                    turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId));
        }
        Map<Long, TurnoHorario> bloquesPorId = bloques.stream()
                .collect(Collectors.toMap(TurnoHorario::getId, b -> b));

        List<Asignacion> todasAsignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            todasAsignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        List<Long> maestroIds = todasAsignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .toList();
        Map<Long, Set<Long>> bloquesPorMaestro = new HashMap<>();
        for (Long mId : maestroIds) {
            List<DisponibilidadMaestro> disp = disponibilidadRepositorio
                    .findDisponiblesByMaestroIdAndSemestreId(mId, escuelaId, semestreId);
            Set<Long> setBloques = disp.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDisponible()))
                    .map(d -> d.getTurnoHorario().getId())
                    .collect(Collectors.toSet());
            bloquesPorMaestro.put(mId, setBloques);
        }

        Map<Long, Set<Long>> bloquesPorGrupo = new HashMap<>();
        for (Grupo g : grupos) {
            List<DisponibilidadGrupo> disp = disponibilidadGrupoRepositorio
                    .findDisponiblesByGrupoIdAndSemestreId(g.getGrupoId(), escuelaId, semestreId);
            Set<Long> setBloques = disp.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDisponible()))
                    .map(d -> d.getTurnoHorario().getId())
                    .collect(Collectors.toSet());
            bloquesPorGrupo.put(g.getGrupoId(), setBloques);
        }

        Map<Long, List<Asignacion>> asignacionesPorGrupo = todasAsignaciones.stream()
                .collect(Collectors.groupingBy(a -> a.getGrupo().getGrupoId()));

        List<AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO> pares = new ArrayList<>();
        int paresCriticos = 0;
        int paresAdvertencia = 0;
        int paresOk = 0;

        for (Grupo grupo : grupos) {
            Set<Long> bloquesDelGrupo = bloquesPorGrupo.getOrDefault(grupo.getGrupoId(), Set.of());
            List<Asignacion> asignacionesGrupo = asignacionesPorGrupo
                    .getOrDefault(grupo.getGrupoId(), List.of());

            if (asignacionesGrupo.isEmpty()) continue;

            for (TurnoHorario bloque : bloques) {
                if (!bloquesDelGrupo.contains(bloque.getId())) continue;

                List<String> nombresMaestrosDisponibles = new ArrayList<>();
                for (Asignacion a : asignacionesGrupo) {
                    Long mId = a.getMaestro().getMaestroId();
                    Set<Long> bloquesDelMaestro = bloquesPorMaestro.getOrDefault(mId, Set.of());
                    if (bloquesDelMaestro.contains(bloque.getId())) {
                        nombresMaestrosDisponibles.add(a.getMaestro().getNombreCompleto());
                    }
                }

                int disponibles = nombresMaestrosDisponibles.size();
                String severidad;
                if (disponibles <= 1) {
                    severidad = "CRITICO";
                    paresCriticos++;
                } else if (disponibles == 2) {
                    severidad = "ADVERTENCIA";
                    paresAdvertencia++;
                } else {
                    severidad = "OK";
                    paresOk++;
                }

                AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO par =
                        new AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO();
                par.setGrupoId(grupo.getGrupoId());
                par.setGrupoNombre(grupo.getNombre());
                par.setGrupoGrado(grupo.getGrado());
                par.setGrupoTurno(grupo.getTurno() != null ? grupo.getTurno().getNombre() : null);

                par.setTurnoHorarioId(bloque.getId());
                par.setDiaSemana(bloque.getDiaSemana());
                par.setDiaNombre(nombreDia(bloque.getDiaSemana()));
                par.setHoraInicio(bloque.getHoraInicio().toString());
                par.setHoraFin(bloque.getHoraFin().toString());

                par.setMaestrosDisponibles(disponibles);
                par.setTotalAsignacionesGrupo(asignacionesGrupo.size());
                par.setMaestrosNombres(nombresMaestrosDisponibles);
                par.setSeveridad(severidad);

                pares.add(par);
            }
        }

        pares.sort(Comparator
                .comparingInt((AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO p) ->
                        switch (p.getSeveridad()) {
                            case "CRITICO" -> 0;
                            case "ADVERTENCIA" -> 1;
                            default -> 2;
                        })
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getGrupoNombre)
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getDiaSemana)
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getHoraInicio));

        AnalisisCuelloBotellaDTO dto = new AnalisisCuelloBotellaDTO();
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        if (turnoFiltro != null) {
            Turno turno = grupos.get(0).getTurno();
            dto.setTurnoId(turno.getTurnoId());
            dto.setTurnoNombre(turno.getNombre());
        }
        dto.setTotalBloques(bloques.size());
        dto.setTotalGrupos(grupos.size());
        dto.setTotalParesGrupoBloque(pares.size());
        dto.setParesCriticos(paresCriticos);
        dto.setParesAdvertencia(paresAdvertencia);
        dto.setParesOk(paresOk);
        dto.setPares(pares);

        return dto;
    }

   /** Nombre del día en español. */
   private static String nombreDia(Integer dia) {
       if (dia == null) return "?";
       String[] dias = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
       return (dia >= 1 && dia <= 5) ? dias[dia] : "Día " + dia;
   }

    // ============================================================
    // PRE-VALIDACIÓN INTERNA
    // ============================================================
    /**
     * Análisis de factibilidad previo a generar: los problemas que lo bloquean ({@code ❌}) y los
     * avisos. Es el mismo análisis que usa la generación masiva de Timefold, expuesto para que el
     * generador IA comparta exactamente las mismas comprobaciones en vez de tener las suyas.
     */
    public List<String> analizarFactibilidad(
            List<Grupo> grupos,
            List<Asignacion> todasAsignaciones,
            List<DisponibilidadGrupo> dispGrupos,
            List<DisponibilidadMaestro> dispMaestros) {

        List<String> problemas = new ArrayList<>();

        Map<Long, Set<Long>> bloquesDisponiblesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            bloquesDisponiblesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }

        Set<String> maestroBloqueDisponible = new HashSet<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            maestroBloqueDisponible.add(
                    dm.getMaestro().getMaestroId() + "-" + dm.getTurnoHorario().getId()
            );
        }

        for (Grupo grupo : grupos) {
            List<Asignacion> asignacionesGrupo = todasAsignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(grupo.getGrupoId()))
                    .collect(Collectors.toList());

            if (asignacionesGrupo.isEmpty()) continue;

            Set<Long> bloquesGrupo = bloquesDisponiblesPorGrupo.getOrDefault(
                    grupo.getGrupoId(), Collections.emptySet());

            if (bloquesGrupo.isEmpty()) {
                problemas.add("❌ Grupo " + grupo.getNombre() +
                        ": tiene asignaciones pero no tiene bloques configurados en disponibilidad_grupo");
                continue;
            }

            int totalHorasGrupo = asignacionesGrupo.stream()
                    .mapToInt(Asignacion::getHoras)
                    .sum();
            int totalBloquesGrupo = bloquesGrupo.size();

            if (totalHorasGrupo > totalBloquesGrupo) {
                problemas.add(String.format(
                        "❌ Grupo %s: tiene %d horas asignadas en total pero solo %d bloques disponibles " +
                                "(faltan %d bloques). Reduce asignaciones o amplía la disponibilidad del grupo.",
                        grupo.getNombre(), totalHorasGrupo, totalBloquesGrupo,
                        totalHorasGrupo - totalBloquesGrupo));
            }

            Map<Long, List<Asignacion>> porMateria = asignacionesGrupo.stream()
                    .collect(Collectors.groupingBy(a -> a.getMateria().getMateriaId()));

            for (var entry : porMateria.entrySet()) {
                List<Asignacion> lista = entry.getValue();
                if (lista.size() <= 1) continue;

                Asignacion ref = lista.get(0);
                int totalHorasAsignadas = lista.stream()
                        .mapToInt(Asignacion::getHoras)
                        .sum();
                Integer horasCatalogo = ref.getMateria().getHorasSemana();

                if (horasCatalogo == null || horasCatalogo <= 0) continue;

                if (totalHorasAsignadas > horasCatalogo) {
                    String ids = lista.stream()
                            .map(a -> String.valueOf(a.getAsignacionId()))
                            .collect(Collectors.joining(", "));

                    problemas.add(String.format(
                            "❌ Grupo %s · Materia %s: tiene %d asignaciones que suman %d horas, " +
                                    "pero el catálogo de la materia solo permite %d horas. " +
                                    "Asignaciones: [%s]. " +
                                    "Reduce las horas o elimina/desactiva una de las asignaciones.",
                            grupo.getNombre(),
                            ref.getMateria().getClave() + " - " + ref.getMateria().getNombre(),
                            lista.size(),
                            totalHorasAsignadas,
                            horasCatalogo,
                            ids));
                }
            }

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
                            a.getMaestro().getNombreCompleto()));
                } else if (bloquesCompatibles < horasNecesarias) {
                    problemas.add(String.format(
                            "⚠️ Grupo %s · Materia %s · Maestro %s: " +
                                    "%d bloques compatibles pero se necesitan %d horas",
                            grupo.getNombre(),
                            a.getMateria().getNombre(),
                            a.getMaestro().getNombreCompleto(),
                            bloquesCompatibles,
                            horasNecesarias));
                }
            }
        }

        Map<Long, Integer> horasAsignadasPorMaestro = new HashMap<>();
        for (Asignacion a : todasAsignaciones) {
            horasAsignadasPorMaestro.merge(a.getMaestro().getMaestroId(), a.getHoras(), Integer::sum);
        }

        Map<Long, Integer> bloquesDisponiblesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            bloquesDisponiblesPorMaestro.merge(dm.getMaestro().getMaestroId(), 1, Integer::sum);
        }

        for (Map.Entry<Long, Integer> entry : horasAsignadasPorMaestro.entrySet()) {
            Long maestroId = entry.getKey();
            int horasAsignadas = entry.getValue();
            int bloquesDisponibles = bloquesDisponiblesPorMaestro.getOrDefault(maestroId, 0);

            if (bloquesDisponibles == 0) continue;

            if (bloquesDisponibles < horasAsignadas) {
                String nombreMaestro = todasAsignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(maestroId))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst().orElse("Maestro ID " + maestroId);

                problemas.add(String.format(
                        "❌ Maestro %s: tiene %d horas asignadas pero solo %d bloques disponibles " +
                                "(faltan %d bloques)",
                        nombreMaestro, horasAsignadas, bloquesDisponibles,
                        horasAsignadas - bloquesDisponibles));
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
        dto.setMaestroId(h.getMaestroId());
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

    // ============================================================
    // EDICIÓN MANUAL DEL HORARIO (tablero de pines)
    // ============================================================

    /**
     * Valida y aplica una tanda de cambios hechos a mano en el tablero de pines.
     *
     * <h2>Todo o nada</h2>
     * Se valida la tanda completa sobre un modelo en memoria y solo después se escribe.
     * Si algún cambio tiene un problema <b>no se toca la base</b> y se devuelven TODOS los
     * problemas numerados para que el tablero marque cada pin.
     *
     * <h2>Reglas comprobadas (las mismas que usa el solver)</h2>
     * <ul>
     *   <li>El bloque destino pertenece al turno del grupo.</li>
     *   <li>Grupo y maestro libres en ese bloque (choques).</li>
     *   <li>Maestro y grupo disponibles en ese bloque.</li>
     *   <li>Aula: se asigna una libre del turno (se prefiere la de la asignación).</li>
     *   <li>Al colocar, la materia no supera sus horas contratadas.</li>
     * </ul>
     *
     * <p>Los cambios se validan de forma progresiva dentro de la misma tanda, así que un
     * QUITAR seguido de un COLOCAR permite intercambiar dos clases de lugar.
     */
    @Transactional
    public ResultadoManualDTO aplicarCambiosManuales(SolicitudManualDTO solicitud) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, solicitud.getSemestreId());
        Long semestreId = semestre.getSemestreId();
        boolean validarSolo = Boolean.TRUE.equals(solicitud.getValidarSolo());

        List<CambioManualDTO> cambios = solicitud.getCambios() == null
                ? List.of() : solicitud.getCambios();
        if (cambios.isEmpty()) {
            throw new NegocioExcepcion("sin_cambios", "No hay cambios que aplicar");
        }

        // ── Estado actual (el semestre entero son cientos de filas: cabe en memoria) ──
        List<Horario> actuales = horarioRepositorio.findByEscuelaIdAndSemestreId(escuelaId, semestreId);
        Map<Long, Horario> horarioPorId = new HashMap<>();
        Map<Long, Long> bloqueDe = new HashMap<>();
        Map<Long, Aula> aulaDe = new HashMap<>();
        Map<String, String> ocupado = new HashMap<>();
        Map<Long, Integer> colocadasPorAsignacion = new HashMap<>();

        for (Horario h : actuales) {
            horarioPorId.put(h.getHorarioId(), h);
            bloqueDe.put(h.getHorarioId(), h.getTurnoHorario().getId());
            aulaDe.put(h.getHorarioId(), h.getAula());
            ocupar(ocupado, h.getGrupo().getGrupoId(), h.getMaestroId(), h.getAula().getAulaId(),
                    h.getTurnoHorario().getId(), etiquetaAsignacion(h.getAsignacion()));
            colocadasPorAsignacion.merge(h.getAsignacion().getAsignacionId(), 1, Integer::sum);
        }

        List<Aula> aulasSemestre = aulaRepositorio
                .findByEscuelaIdAndActivoTrueAndSemestreId(escuelaId, semestreId);

        // ── Validación progresiva (sin tocar entidades administradas por Hibernate) ──
        List<String> errores = new ArrayList<>();
        List<Horario> porCrear = new ArrayList<>();
        List<Long> porBorrar = new ArrayList<>();
        Map<Long, Long> destinoDe = new HashMap<>();
        Map<Long, Aula> aulaDestinoDe = new HashMap<>();
        int colocados = 0;
        int movidos = 0;
        int quitados = 0;

        for (int i = 0; i < cambios.size(); i++) {
            CambioManualDTO c = cambios.get(i);
            String etiqueta = "Cambio " + (i + 1) + ": ";
            String tipo = c.getTipo() == null ? "" : c.getTipo().trim().toUpperCase();
            try {
                if ("COLOCAR".equals(tipo)) {
                    colocados += validarColocar(c, escuelaId, semestreId, validarSolo ? null : porCrear,
                            ocupado, colocadasPorAsignacion, aulasSemestre);

                } else if ("MOVER".equals(tipo)) {
                    movidos += validarMover(c, escuelaId, semestreId, horarioPorId, bloqueDe, aulaDe,
                            destinoDe, aulaDestinoDe, porBorrar, ocupado, aulasSemestre);

                } else if ("QUITAR".equals(tipo)) {
                    quitados += validarQuitar(c, horarioPorId, bloqueDe, aulaDe, destinoDe,
                            porBorrar, ocupado, colocadasPorAsignacion);

                } else {
                    errores.add(etiqueta + "tipo de cambio desconocido '" + c.getTipo() + "'");
                }
            } catch (NegocioExcepcion e) {
                errores.add(etiqueta + e.getMensajeCrudo());
            }
        }

        ResultadoManualDTO resultado = new ResultadoManualDTO();
        resultado.setSoloValidacion(validarSolo);
        resultado.setColocados(colocados);
        resultado.setMovidos(movidos);
        resultado.setQuitados(quitados);
        resultado.setErrores(errores);

        if (!errores.isEmpty()) {
            resultado.setAplicado(false);
            resultado.setMensaje("No se aplicó ningún cambio: " + errores.size() + " problema(s) por resolver");
            logger.info("Edición manual rechazada: {} problema(s)", errores.size());
            return resultado;
        }

        if (validarSolo) {
            resultado.setAplicado(false);
            resultado.setMensaje("Tanda válida: " + colocados + " por colocar, "
                    + movidos + " por mover, " + quitados + " por quitar");
            return resultado;
        }

        // ── Aplicar ──
        for (Long id : porBorrar) {
            Horario h = horarioPorId.get(id);
            if (h != null) {
                horarioRepositorio.delete(h);
            }
        }
        destinoDe.forEach((horarioId, bloqueId) -> {
            Horario h = horarioPorId.get(horarioId);
            if (h == null) {
                return;
            }
            turnoHorarioRepositorio.findById(bloqueId).ifPresent(h::setTurnoHorario);
            Aula aula = aulaDestinoDe.get(horarioId);
            if (aula != null) {
                h.setAula(aula);
            }
        });
        if (!porCrear.isEmpty()) {
            horarioRepositorio.saveAll(porCrear);
        }

        logger.info("Edición manual aplicada: {} colocada(s), {} movida(s), {} quitada(s)",
                colocados, movidos, quitados);
        resultado.setAplicado(true);
        resultado.setMensaje("Se aplicaron " + colocados + " colocación(es), "
                + movidos + " movimiento(s) y " + quitados + " retiro(s)");
        return resultado;
    }

    /** COLOCAR: una hora de una materia que estaba en la caja pasa a un bloque. */
    private int validarColocar(CambioManualDTO c, Long escuelaId, Long semestreId, List<Horario> porCrear,
                               Map<String, String> ocupado, Map<Long, Integer> colocadasPorAsignacion,
                               List<Aula> aulasSemestre) {
        if (c.getAsignacionId() == null || c.getTurnoHorarioId() == null) {
            throw new NegocioExcepcion("para colocar hacen falta la asignación y el bloque destino");
        }
        Asignacion asignacion = asignacionRepositorio.findById(c.getAsignacionId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró la asignación indicada"));
        if (asignacion.getEscuela() == null
                || !escuelaId.equals(asignacion.getEscuela().getEscuelaId())) {
            throw new NegocioExcepcion("la asignación pertenece a otra escuela");
        }
        if (asignacion.getSemestre() == null
                || !semestreId.equals(asignacion.getSemestre().getSemestreId())) {
            throw new NegocioExcepcion("la asignación pertenece a otro semestre");
        }
        if (Boolean.FALSE.equals(asignacion.getActivo())) {
            throw new NegocioExcepcion("la asignación está inactiva");
        }

        TurnoHorario bloque = turnoHorarioRepositorio.findById(c.getTurnoHorarioId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró el bloque destino"));
        String bloqueTexto = textoBloque(bloque);
        Grupo grupo = asignacion.getGrupo();
        Long maestroId = asignacion.getMaestro().getMaestroId();

        validarBloqueDelTurno(bloque, grupo, bloqueTexto);
        validarLibres(ocupado, grupo, maestroId, bloque.getId(), bloqueTexto);
        validarDisponibilidad(escuelaId, semestreId, maestroId, grupo, bloque.getId(), bloqueTexto);

        int colocadas = colocadasPorAsignacion.getOrDefault(asignacion.getAsignacionId(), 0);
        int horas = asignacion.getHoras() == null ? 0 : asignacion.getHoras();
        if (colocadas >= horas) {
            throw new NegocioExcepcion("la materia " + claveMateria(asignacion)
                    + " ya tiene sus " + horas + " hora(s) colocada(s)");
        }

        Aula aula = elegirAula(aulasSemestre, ocupado, bloque.getId(), asignacion.getAula(),
                grupo, bloqueTexto);
        if (porCrear != null) {
            Horario nuevo = new Horario();
            nuevo.setEscuela(asignacion.getEscuela());
            nuevo.setGrupo(grupo);
            nuevo.setAsignacion(asignacion);
            nuevo.setTurnoHorario(bloque);
            nuevo.setAula(aula);
            nuevo.setMaestroId(maestroId);
            nuevo.setSemestre(asignacion.getSemestre());
            nuevo.setVersion(1);
            porCrear.add(nuevo);
        }

        ocupar(ocupado, grupo.getGrupoId(), maestroId, aula.getAulaId(), bloque.getId(),
                etiquetaAsignacion(asignacion));
        colocadasPorAsignacion.merge(asignacion.getAsignacionId(), 1, Integer::sum);
        return 1;
    }

    /** MOVER: un pin ya colocado cambia de bloque (y de aula si la suya está ocupada). */
    private int validarMover(CambioManualDTO c, Long escuelaId, Long semestreId,
                             Map<Long, Horario> horarioPorId, Map<Long, Long> bloqueDe,
                             Map<Long, Aula> aulaDe, Map<Long, Long> destinoDe,
                             Map<Long, Aula> aulaDestinoDe, List<Long> porBorrar,
                             Map<String, String> ocupado, List<Aula> aulasSemestre) {
        if (c.getHorarioId() == null || c.getTurnoHorarioId() == null) {
            throw new NegocioExcepcion("para mover hacen falta la clase y el bloque destino");
        }
        Horario h = horarioPorId.get(c.getHorarioId());
        if (h == null) {
            throw new NegocioExcepcion("no se encontró la clase colocada indicada");
        }
        if (porBorrar.contains(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había quitado en esta misma tanda");
        }
        TurnoHorario bloque = turnoHorarioRepositorio.findById(c.getTurnoHorarioId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró el bloque destino"));
        if (bloque.getId().equals(bloqueDe.get(h.getHorarioId()))) {
            return 0;  // ya está ahí: no es un error, simplemente no hay nada que hacer
        }

        String bloqueTexto = textoBloque(bloque);
        Grupo grupo = h.getGrupo();
        Long maestroId = h.getMaestroId();
        validarBloqueDelTurno(bloque, grupo, bloqueTexto);

        Long bloqueOrigen = bloqueDe.get(h.getHorarioId());
        Aula aulaOrigen = aulaDe.get(h.getHorarioId());
        // Se libera primero el origen para que mover "en cadena" dentro de la misma tanda funcione.
        liberar(ocupado, grupo.getGrupoId(), maestroId, aulaOrigen.getAulaId(), bloqueOrigen);
        try {
            validarLibres(ocupado, grupo, maestroId, bloque.getId(), bloqueTexto);
            validarDisponibilidad(escuelaId, semestreId, maestroId, grupo, bloque.getId(), bloqueTexto);
            Aula aula = elegirAula(aulasSemestre, ocupado, bloque.getId(), aulaOrigen, grupo, bloqueTexto);
            destinoDe.put(h.getHorarioId(), bloque.getId());
            aulaDestinoDe.put(h.getHorarioId(), aula);
            aulaDe.put(h.getHorarioId(), aula);
            bloqueDe.put(h.getHorarioId(), bloque.getId());
            ocupar(ocupado, grupo.getGrupoId(), maestroId, aula.getAulaId(), bloque.getId(),
                    etiquetaAsignacion(h.getAsignacion()));
            return 1;
        } catch (NegocioExcepcion e) {
            // Devolver la ocupación del origen: la tanda sigue evaluándose desde el estado real.
            ocupar(ocupado, grupo.getGrupoId(), maestroId, aulaOrigen.getAulaId(), bloqueOrigen,
                    etiquetaAsignacion(h.getAsignacion()));
            throw e;
        }
    }

    /** QUITAR: un pin vuelve a la caja (se borra la fila del horario). */
    private int validarQuitar(CambioManualDTO c, Map<Long, Horario> horarioPorId,
                              Map<Long, Long> bloqueDe, Map<Long, Aula> aulaDe,
                              Map<Long, Long> destinoDe, List<Long> porBorrar,
                              Map<String, String> ocupado, Map<Long, Integer> colocadasPorAsignacion) {
        if (c.getHorarioId() == null) {
            throw new NegocioExcepcion("para quitar hace falta la clase");
        }
        Horario h = horarioPorId.get(c.getHorarioId());
        if (h == null) {
            throw new NegocioExcepcion("no se encontró la clase colocada indicada");
        }
        if (porBorrar.contains(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había quitado en esta misma tanda");
        }
        if (destinoDe.containsKey(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había movido en esta misma tanda");
        }
        liberar(ocupado, h.getGrupo().getGrupoId(), h.getMaestroId(),
                aulaDe.get(h.getHorarioId()).getAulaId(), bloqueDe.get(h.getHorarioId()));
        porBorrar.add(h.getHorarioId());
        colocadasPorAsignacion.merge(h.getAsignacion().getAsignacionId(), -1, Integer::sum);
        return 1;
    }

    // ─── Ayudantes de la edición manual ───────────────────────

    private void ocupar(Map<String, String> ocupado, Long grupoId, Long maestroId, Long aulaId,
                        Long bloqueId, String quien) {
        ocupado.put("g|" + grupoId + "|" + bloqueId, quien);
        ocupado.put("m|" + maestroId + "|" + bloqueId, quien);
        ocupado.put("a|" + aulaId + "|" + bloqueId, quien);
    }

    private void liberar(Map<String, String> ocupado, Long grupoId, Long maestroId, Long aulaId,
                         Long bloqueId) {
        ocupado.remove("g|" + grupoId + "|" + bloqueId);
        ocupado.remove("m|" + maestroId + "|" + bloqueId);
        ocupado.remove("a|" + aulaId + "|" + bloqueId);
    }

    private void validarBloqueDelTurno(TurnoHorario bloque, Grupo grupo, String bloqueTexto) {
        Long turnoBloque = bloque.getTurno() == null ? null : bloque.getTurno().getTurnoId();
        Long turnoGrupo = grupo.getTurno() == null ? null : grupo.getTurno().getTurnoId();
        if (turnoBloque == null || !turnoBloque.equals(turnoGrupo)) {
            throw new NegocioExcepcion("el bloque " + bloqueTexto
                    + " no pertenece al turno del grupo " + grupo.getNombre());
        }
    }

    private void validarLibres(Map<String, String> ocupado, Grupo grupo, Long maestroId,
                               Long bloqueId, String bloqueTexto) {
        String enGrupo = ocupado.get("g|" + grupo.getGrupoId() + "|" + bloqueId);
        if (enGrupo != null) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre() + " ya tiene clase el "
                    + bloqueTexto + " (" + enGrupo + ")");
        }
        String enMaestro = ocupado.get("m|" + maestroId + "|" + bloqueId);
        if (enMaestro != null) {
            throw new NegocioExcepcion("el maestro ya tiene clase el " + bloqueTexto
                    + " (" + enMaestro + ")");
        }
    }

    private void validarDisponibilidad(Long escuelaId, Long semestreId, Long maestroId, Grupo grupo,
                                       Long bloqueId, String bloqueTexto) {
        List<DisponibilidadMaestro> dispMaestro = disponibilidadRepositorio
                .findDisponiblesByMaestroIdAndSemestreId(maestroId, escuelaId, semestreId);
        if (dispMaestro.isEmpty()) {
            throw new NegocioExcepcion("el maestro no tiene disponibilidad registrada en este semestre");
        }
        boolean maestroOk = dispMaestro.stream().anyMatch(d -> d.getTurnoHorario() != null
                && bloqueId.equals(d.getTurnoHorario().getId()));
        if (!maestroOk) {
            throw new NegocioExcepcion("el maestro no está disponible el " + bloqueTexto);
        }

        List<DisponibilidadGrupo> dispGrupo = disponibilidadGrupoRepositorio
                .findDisponiblesByGrupoIdAndSemestreId(grupo.getGrupoId(), escuelaId, semestreId);
        if (dispGrupo.isEmpty()) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre()
                    + " no tiene disponibilidad registrada en este semestre");
        }
        boolean grupoOk = dispGrupo.stream().anyMatch(d -> d.getTurnoHorario() != null
                && bloqueId.equals(d.getTurnoHorario().getId()));
        if (!grupoOk) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre()
                    + " no está disponible el " + bloqueTexto);
        }
    }

    /** Primera aula libre del turno del grupo; si la preferida (la de la asignación) lo está, se usa esa. */
    /**
     * true si el patron pide alguna sesion de 2 o mas horas seguidas.
     *
     * Lo pide el generador para colocarlas antes: una sesion doble solo cabe donde hay dos bloques
     * contiguos libres del grupo, del maestro y del aula al mismo tiempo.
     */
    private static boolean tieneSesionLarga(String distribucion) {
        if (distribucion == null || distribucion.isBlank()) {
            return false;
        }
        for (String parte : distribucion.split(",")) {
            try {
                if (Integer.parseInt(parte.trim()) > 1) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Dato sucio en la distribucion: se ignora, igual que en HorarioConstraintProvider.
            }
        }
        return false;
    }

    /** true si al maestro le quedan menos de 4 bloques libres sobre las horas que debe cubrir. */
    private static boolean maestroApretado(Long maestroId, Map<Long, Integer> horasPorMaestro,
                                           Map<Long, Integer> bloquesPorMaestro) {
        int horas = horasPorMaestro.getOrDefault(maestroId, 0);
        int bloques = bloquesPorMaestro.getOrDefault(maestroId, 0);
        return bloques > 0 && (bloques - horas) < 4;
    }

    private Aula elegirAula(List<Aula> aulas, Map<String, String> ocupado, Long bloqueId,
                            Aula preferida, Grupo grupo, String bloqueTexto) {
        Long turnoGrupo = grupo.getTurno() == null ? null : grupo.getTurno().getTurnoId();
        if (preferida != null
                && preferida.getTurno() != null
                && preferida.getTurno().getTurnoId().equals(turnoGrupo)
                && ocupado.get("a|" + preferida.getAulaId() + "|" + bloqueId) == null) {
            return preferida;
        }
        List<Aula> libres = aulas.stream()
                .filter(a -> a.getTurno() != null && a.getTurno().getTurnoId().equals(turnoGrupo))
                .filter(a -> ocupado.get("a|" + a.getAulaId() + "|" + bloqueId) == null)
                .toList();
        // Preferencia de aula: una clase que va a taller se queda en un taller si hay otro libre,
        // en vez de caer en un aula normal.
        boolean quiereTaller = preferida != null && Boolean.TRUE.equals(preferida.getTaller());
        return libres.stream()
                .filter(a -> Boolean.TRUE.equals(a.getTaller()) == quiereTaller)
                .findFirst()
                .or(() -> libres.stream().findFirst())
                .orElseThrow(() -> new NegocioExcepcion("no hay aula libre para el grupo "
                        + grupo.getNombre() + " el " + bloqueTexto));
    }

    /** "lunes 14:00" para los mensajes al usuario. */
    private String textoBloque(TurnoHorario bloque) {
        String dia;
        switch (bloque.getDiaSemana() == null ? 0 : bloque.getDiaSemana()) {
            case 1: dia = "lunes"; break;
            case 2: dia = "martes"; break;
            case 3: dia = "miércoles"; break;
            case 4: dia = "jueves"; break;
            case 5: dia = "viernes"; break;
            default: dia = "día " + bloque.getDiaSemana();
        }
        String hora = bloque.getHoraInicio() == null ? "" : " " + bloque.getHoraInicio();
        return dia + hora;
    }

    private String claveMateria(Asignacion asignacion) {
        if (asignacion.getMateria() == null) {
            return "la materia";
        }
        String clave = asignacion.getMateria().getClave();
        return (clave == null || clave.isBlank()) ? asignacion.getMateria().getNombre() : clave;
    }

    /** "MATE (El Profe)" para decir quién ocupa un bloque. */
    private String etiquetaAsignacion(Asignacion asignacion) {
        if (asignacion == null) {
            return "otra clase";
        }
        String maestro = "sin maestro";
        if (asignacion.getMaestro() != null) {
            String apodo = asignacion.getMaestro().getApodo();
            maestro = (apodo != null && !apodo.isBlank())
                    ? apodo : asignacion.getMaestro().getTituloNombreCompleto();
        }
        return claveMateria(asignacion) + " (" + maestro + ")";
    }
}