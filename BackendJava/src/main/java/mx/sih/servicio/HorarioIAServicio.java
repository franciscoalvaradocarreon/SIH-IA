package mx.sih.servicio;

import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.AsesorHeuristico;
import mx.sih.ia.AsesorIA;
import mx.sih.ia.AsesorLLM;
import mx.sih.ia.DatosIA;
import mx.sih.ia.GeneradorIA;
import mx.sih.ia.IntentoIA;
import mx.sih.ia.ReglasIA;
import mx.sih.modelo.dto.ResultadoValidacionDTO;
import mx.sih.modelo.dto.ValidacionIADTO;
import mx.sih.modelo.entidad.Asignacion;
import mx.sih.modelo.entidad.Aula;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Grupo;
import mx.sih.modelo.entidad.Horario;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.TurnoHorario;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.DisponibilidadGrupoRepositorio;
import mx.sih.repositorio.DisponibilidadMaestroRepositorio;
import mx.sih.repositorio.GrupoRepositorio;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoHorarioRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * GENERADOR DE HORARIOS IA (sin Timefold): carga de datos, pre-validación, intentos y registro.
 *
 * <p>Es el servicio del módulo "Horario IA". No usa el solver: arma el horario con
 * {@link GeneradorIA} (motor propio) y, si hay clave de LLM configurada, con
 * {@link AsesorLLM} como consejero del orden de colocación.
 *
 * <h2>Qué reutiliza del módulo de siempre</h2>
 * La pre-validación NO es una copia: llama a
 * {@link HorarioServicio#analizarFactibilidad} y a {@link HorarioServicio#validarFactibilidad}, así
 * que las dos rutas (Timefold e IA) avisan exactamente de lo mismo. Aquí solo se añaden los chequeos
 * que únicamente tienen sentido para el motor IA (ventanas legales, tramos continuos, una sesión por
 * día y por materia...).
 *
 * <h2>Registro</h2>
 * El registro se hace en UNA transacción y en DOS tandas: primero se borra el horario de los grupos
 * del alcance y se hace {@code flush()}, y solo después se insertan las filas nuevas. Es obligatorio
 * hacerlo así porque Hibernate ordena las inserciones antes de los borrados dentro de la misma
 * transacción, y con las restricciones de exclusión de PostgreSQL eso hacía fallar el guardado (o
 * peor: borraba el horario y dejaba el nuevo sin escribir).
 */
@Service
public class HorarioIAServicio {

    private static final Logger logger = LoggerFactory.getLogger(HorarioIAServicio.class);

    /**
     * A partir de qué índice de holgura un grupo se considera AJUSTADO (ver {@code analizarViabilidad}).
     * Con menos de 1.5 "opciones por hora" (maestros elegibles por bloque + horas de sobra) elegir
     * maestro y hora a la vez es casi forzado: son los grupos donde conviene reacomodar maestros.
     */
    private static final double UMBRAL_GRUPO_AJUSTADO = 1.5;

    private final AsignacionRepositorio asignacionRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final HorarioRepositorio horarioRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadMaestroRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio;
    private final HorarioServicio horarioServicio;

    private final String iaApiKey;
    private final String iaUrl;
    private final String iaModelo;
    private final long iaTiempoLimite;

    public HorarioIAServicio(AsignacionRepositorio asignacionRepositorio,
                             TurnoHorarioRepositorio turnoHorarioRepositorio,
                             HorarioRepositorio horarioRepositorio,
                             GrupoRepositorio grupoRepositorio,
                             SemestreRepositorio semestreRepositorio,
                             DisponibilidadMaestroRepositorio disponibilidadMaestroRepositorio,
                             DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio,
                             HorarioServicio horarioServicio,
                             @Value("${app.ia.api-key:}") String iaApiKey,
                             @Value("${app.ia.url:https://api.openai.com/v1/chat/completions}") String iaUrl,
                             @Value("${app.ia.modelo:gpt-4o-mini}") String iaModelo,
                             @Value("${app.ia.tiempo-limite-segundos:45}") long iaTiempoLimite) {
        this.asignacionRepositorio = asignacionRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.horarioRepositorio = horarioRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.disponibilidadMaestroRepositorio = disponibilidadMaestroRepositorio;
        this.disponibilidadGrupoRepositorio = disponibilidadGrupoRepositorio;
        this.horarioServicio = horarioServicio;
        this.iaApiKey = iaApiKey == null ? "" : iaApiKey.trim();
        this.iaUrl = iaUrl;
        this.iaModelo = iaModelo;
        this.iaTiempoLimite = iaTiempoLimite;
    }

    // ============================================================
    // CARGA DE DATOS
    // ============================================================

    /** Datos completos del alcance (semestre + turno opcional), igual que la generación masiva. */
    public DatosIA cargarDatos(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);
        if (grupos.isEmpty()) {
            throw new NegocioExcepcion("sin_grupos",
                    turnoFiltro != null
                            ? "No hay grupos activos en el turno seleccionado"
                            : "No hay grupos activos en el semestre");
        }

        List<Asignacion> asignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            asignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long turnoId : turnoIds) {
            bloques.addAll(turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId));
        }

        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        Set<Long> maestroIds = asignaciones.stream()
                .filter(a -> a.getMaestro() != null)
                .map(a -> a.getMaestro().getMaestroId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long m : maestroIds) {
            dispMaestros.addAll(disponibilidadMaestroRepositorio
                    .findDisponiblesByMaestroIdAndSemestreId(m, escuelaId, semestreId));
        }

        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        for (Grupo g : grupos) {
            dispGrupos.addAll(disponibilidadGrupoRepositorio
                    .findDisponiblesByGrupoIdAndSemestreId(g.getGrupoId(), escuelaId, semestreId));
        }

        logger.info("Horario IA: grupos={} asignaciones={} bloques={} dispGrupos={} dispMaestros={}",
                grupos.size(), asignaciones.size(), bloques.size(), dispGrupos.size(), dispMaestros.size());

        return new DatosIA(bloques, grupos, asignaciones, dispGrupos, dispMaestros);
    }

    // ============================================================
    // PRE-VALIDACIÓN
    // ============================================================

    /**
     * Pre-validación completa: la del backend de siempre + los chequeos del motor IA.
     *
     * <p>Los "imposibles" (asignaturas sin ninguna ventana legal) no bloquean la generación: el motor
     * las reportará como pendientes con el motivo. Lo que sí bloquea son los errores de información
     * (sin aula, sin disponibilidad, patrón más largo que el tramo continuo del turno...).
     *
     * <p>Las DOS REVISIONES FINAS del diagnóstico ({@code analizarRevisiones}) son avisos: describen
     * qué impide colocar cada hora, pero no tocan {@code aptoParaGenerar}.
     *
     * @param escuelaId      escuela activa de la sesión
     * @param semestreIdParam semestre pedido; si viene nulo se usa el activo
     * @param turnoIdParam   turno del alcance (opcional)
     * @param datos          datos ya cargados por {@link #cargarDatos}
     */
    public ValidacionIADTO validar(Long escuelaId, Long semestreIdParam, Long turnoIdParam, DatosIA datos) {
        ResultadoValidacionDTO backend = horarioServicio.validarFactibilidad(semestreIdParam, turnoIdParam);

        List<String> criticos = horarioServicio.analizarFactibilidad(
                datos.grupos(), datos.asignaciones(),
                datos.disponibilidadGrupo(), datos.disponibilidadMaestro())
                .stream().filter(p -> p.startsWith("❌")).toList();

        List<ValidacionIADTO.ChequeoIA> chequeos = new ArrayList<>();
        List<ValidacionIADTO.MateriaImposible> imposibles = new ArrayList<>();

        // ── disponibilidad por grupo y por maestro ──
        Map<Long, Set<Long>> dispG = new HashMap<>();
        for (DisponibilidadGrupo dg : datos.disponibilidadGrupo()) {
            if (Boolean.FALSE.equals(dg.getDisponible()) || dg.getTurnoHorario() == null) {
                continue;
            }
            dispG.computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }
        Map<Long, Set<Long>> dispM = new HashMap<>();
        for (DisponibilidadMaestro dm : datos.disponibilidadMaestro()) {
            if (Boolean.FALSE.equals(dm.getDisponible()) || dm.getTurnoHorario() == null) {
                continue;
            }
            dispM.computeIfAbsent(dm.getMaestro().getMaestroId(), k -> new HashSet<>())
                    .add(dm.getTurnoHorario().getId());
        }

        // ── bloques por TURNO y día, y tramo continuo máximo ──
        // Por turno y no solo por día: el campo orden se repite en cada turno, así que mezclarlos
        // entrelaza bloques de mañana y tarde y la contigüidad nunca se cumple. Ese era el bug que
        // hacía parecer "imposibles" a asignaturas que sí caben (el mismo que ya se corrigió en el
        // motor, y que aquí seguía vivo en la pre-validación).
        Map<Long, Map<Integer, List<TurnoHorario>>> porTurnoDia = new LinkedHashMap<>();
        for (TurnoHorario b : datos.bloques()) {
            if (b.getDiaSemana() == null || b.getTurno() == null || b.getTurno().getTurnoId() == null) {
                continue;
            }
            porTurnoDia.computeIfAbsent(b.getTurno().getTurnoId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(b.getDiaSemana(), k -> new ArrayList<>()).add(b);
        }
        porTurnoDia.values().forEach(m -> m.values()
                .forEach(l -> l.sort(Comparator.comparing(TurnoHorario::getOrden))));
        int tramoMaximo = 0;
        int diasDisponibles = 0;
        for (Map<Integer, List<TurnoHorario>> porDia : porTurnoDia.values()) {
            diasDisponibles = Math.max(diasDisponibles, porDia.size());
            for (List<TurnoHorario> arr : porDia.values()) {
                int mejor = 0;
                int actual = 0;
                for (int i = 0; i < arr.size(); i++) {
                    if (i == 0) {
                        actual = 1;
                    } else {
                        long gap = minutos(arr.get(i).getHoraInicio()) - minutos(arr.get(i - 1).getHoraFin());
                        actual = gap > ReglasIA.TOLERANCIA_CONTIGUIDAD_MIN ? 1 : actual + 1;
                    }
                    mejor = Math.max(mejor, actual);
                }
                tramoMaximo = Math.max(tramoMaximo, mejor);
            }
        }

        // ── ventanas legales por asignación y sesiones ──
        Map<Long, Integer> ventanasPorAsignacion = new HashMap<>();
        Map<Long, Integer> sesionesPorAsignacion = new HashMap<>();
        int totalSesiones = 0;
        int totalVentanas = 0;
        int horasDemandadas = 0;
        for (Asignacion a : datos.asignaciones()) {
            if (a.getGrupo() == null || a.getMaestro() == null) {
                continue;
            }
            List<Integer> duraciones = GeneradorIA.duracionesDe(a);
            sesionesPorAsignacion.merge(a.getAsignacionId(), duraciones.size(), Integer::sum);
            totalSesiones += duraciones.size();
            horasDemandadas += a.getHoras() == null ? 0 : a.getHoras();
            int ventanas = 0;
            Long turnoId = a.getGrupo().getTurno() != null ? a.getGrupo().getTurno().getTurnoId() : null;
            Map<Integer, List<TurnoHorario>> diasDelTurno = turnoId != null
                    ? porTurnoDia.getOrDefault(turnoId, Map.of()) : Map.of();
            for (int dur : duraciones) {
                ventanas += contarVentanasLegales(diasDelTurno, dispG.get(a.getGrupo().getGrupoId()),
                        dispM.get(a.getMaestro().getMaestroId()), dur);
            }
            ventanasPorAsignacion.put(a.getAsignacionId(), ventanas);
            totalVentanas += ventanas;
        }

        // 1) asignaciones sin aula (la tabla horario exige aula_id)
        List<Asignacion> sinAula = datos.asignaciones().stream()
                .filter(a -> a.getAula() == null)
                .toList();
        chequeos.add(new ValidacionIADTO.ChequeoIA("Aulas asignadas",
                sinAula.isEmpty() ? "OK" : "ERROR",
                sinAula.isEmpty()
                        ? "Todas las asignaciones tienen aula."
                        : sinAula.size() + " asignaciones sin aula: el horario no se puede guardar sin "
                        + "aula. Ejemplo: " + etiqueta(sinAula.get(0))));

        // 2) maestros sin disponibilidad cargada
        List<String> maestrosSinDisp = datos.asignaciones().stream()
                .filter(a -> a.getMaestro() != null)
                .filter(a -> dispM.getOrDefault(a.getMaestro().getMaestroId(), Set.of()).isEmpty())
                .map(a -> a.getMaestro().getTituloNombreCompleto())
                .distinct()
                .toList();
        chequeos.add(new ValidacionIADTO.ChequeoIA("Disponibilidad de maestros",
                maestrosSinDisp.isEmpty() ? "OK" : "ERROR",
                maestrosSinDisp.isEmpty()
                        ? "Todos los maestros con clase tienen bloques disponibles."
                        : maestrosSinDisp.size() + " maestros sin ningún bloque disponible: "
                        + String.join(", ", recortarLista(maestrosSinDisp))));

        // 3) patrón más largo que el tramo continuo del turno
        List<String> patronesImposibles = new ArrayList<>();
        for (Asignacion a : datos.asignaciones()) {
            for (int dur : GeneradorIA.duracionesDe(a)) {
                if (dur > tramoMaximo) {
                    patronesImposibles.add(etiqueta(a) + " pide " + dur
                            + " h seguidas y el tramo continuo más largo es " + tramoMaximo + " h");
                    break;
                }
            }
        }
        chequeos.add(new ValidacionIADTO.ChequeoIA("Tramos continuos del turno",
                patronesImposibles.isEmpty() ? "OK" : "ERROR",
                patronesImposibles.isEmpty()
                        ? "Tramo continuo más largo: " + tramoMaximo + " bloques."
                        : String.join("; ", recortarLista(patronesImposibles))));

        // 4) más sesiones que días (la regla dura es una sesión por materia y día)
        List<String> demasiadasSesiones = new ArrayList<>();
        for (Asignacion a : datos.asignaciones()) {
            int sesiones = sesionesPorAsignacion.getOrDefault(a.getAsignacionId(), 0);
            if (sesiones > diasDisponibles) {
                demasiadasSesiones.add(etiqueta(a) + ": " + sesiones + " sesiones para "
                        + diasDisponibles + " días");
            }
        }
        chequeos.add(new ValidacionIADTO.ChequeoIA("Sesiones contra días del turno",
                demasiadasSesiones.isEmpty() ? "OK" : "ERROR",
                demasiadasSesiones.isEmpty()
                        ? "Ninguna materia necesita más sesiones que días tiene el turno ("
                        + diasDisponibles + ")."
                        : "Una sesión por materia y día: " + String.join("; ",
                        recortarLista(demasiadasSesiones))));

        // 5) holgura de bloques por grupo
        int gruposApretados = 0;
        int peorHolgura = Integer.MAX_VALUE;
        String peorGrupo = "-";
        for (Grupo g : datos.grupos()) {
            int horas = datos.asignaciones().stream()
                    .filter(a -> a.getGrupo() != null && a.getGrupo().getGrupoId().equals(g.getGrupoId()))
                    .mapToInt(a -> a.getHoras() == null ? 0 : a.getHoras())
                    .sum();
            if (horas == 0) {
                continue;
            }
            int bloques = dispG.getOrDefault(g.getGrupoId(), Set.of()).size();
            int holgura = bloques - horas;
            if (holgura < peorHolgura) {
                peorHolgura = holgura;
                peorGrupo = g.getNombre();
            }
            if (holgura < 0) {
                gruposApretados++;
            }
        }
        chequeos.add(new ValidacionIADTO.ChequeoIA("Holgura de bloques por grupo",
                gruposApretados == 0 ? "OK" : "ERROR",
                gruposApretados == 0
                        ? "El grupo más apretado (" + peorGrupo + ") tiene " + peorHolgura
                        + " bloques de sobra."
                        : gruposApretados + " grupos piden más horas que bloques disponibles."));

        // 6) asignaturas sin ninguna ventana legal
        for (Asignacion a : datos.asignaciones()) {
            int ventanas = ventanasPorAsignacion.getOrDefault(a.getAsignacionId(), 0);
            if (ventanas > 0) {
                continue;
            }
            String motivo;
            Set<Long> g = dispG.getOrDefault(
                    a.getGrupo() != null ? a.getGrupo().getGrupoId() : -1L, Set.of());
            Set<Long> m = dispM.getOrDefault(
                    a.getMaestro() != null ? a.getMaestro().getMaestroId() : -1L, Set.of());
            if (g.isEmpty() && m.isEmpty()) {
                motivo = "Ni el grupo ni el maestro tienen disponibilidad cargada en el semestre.";
            } else if (g.isEmpty()) {
                motivo = "El grupo no tiene bloques disponibles.";
            } else if (m.isEmpty()) {
                motivo = "El maestro no tiene bloques disponibles.";
            } else {
                Set<Long> comunes = new HashSet<>(g);
                comunes.retainAll(m);
                motivo = comunes.isEmpty()
                        ? "El grupo y el maestro no coinciden en ningún bloque del turno."
                        : "Coinciden en " + comunes.size() + " bloques, pero no hay "
                        + duracionesTexto(a) + " contiguas dentro de un mismo día.";
            }
            imposibles.add(new ValidacionIADTO.MateriaImposible(a.getAsignacionId(),
                    a.getGrupo() != null ? a.getGrupo().getNombre() : "?",
                    a.getMateria() != null ? a.getMateria().getClave() + " " + a.getMateria().getNombre() : "?",
                    a.getMaestro() != null ? a.getMaestro().getTituloNombreCompleto() : "?",
                    a.getHoras() == null ? 0 : a.getHoras(), 0, motivo));
        }
        chequeos.add(new ValidacionIADTO.ChequeoIA("Ventanas legales por asignatura",
                imposibles.isEmpty() ? "OK" : "ADVERTENCIA",
                imposibles.isEmpty()
                        ? "Las " + datos.asignaciones().size() + " asignaturas tienen al menos una "
                        + "ventana legal (" + totalVentanas + " en total)."
                        : imposibles.size() + " asignaturas no caben con los datos actuales: el motor "
                        + "las dejará pendientes y dirá por qué."));

        // ── análisis de viabilidad del reparto (diagnóstico, nunca bloquea) ──
        // Incluye las dos revisiones finas del diagnóstico.
        ValidacionIADTO.AnalisisViabilidad viabilidad = analizarViabilidad(
                datos, dispG, dispM, porTurnoDia);

        // El análisis se asoma también a los chequeos: es la primera pregunta del usuario
        // ("¿esto cabe?") y así se ve sin desplegar nada.
        if (viabilidad.resumen().maestrosEnDeficit() > 0) {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Reparto de maestros",
                    "ADVERTENCIA",
                    viabilidad.resumen().maestrosEnDeficit() + " maestros tienen más horas asignadas que "
                            + "bloques legales: " + viabilidad.resumen().horasSinHueco()
                            + " h no caben en el horario. Mira el análisis de viabilidad."));
        } else {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Reparto de maestros", "OK",
                    "Ningún maestro pide más horas que bloques legales tiene."));
        }
        if (viabilidad.resumen().bloquesSinMaestro() > 0) {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Bloques sin ningún maestro posible",
                    "ADVERTENCIA",
                    viabilidad.resumen().bloquesSinMaestro() + " bloques de "
                            + viabilidad.resumen().gruposImposibles()
                            + " grupos no los puede dar ningún maestro de ese grupo."));
        }

        // ── las dos revisiones finas, asomadas también a los chequeos ──
        // Son ADVERTENCIA (nunca ERROR): describen qué impide colocar cada hora, pero en ningún caso
        // cambian `aptoParaGenerar`. La holgura de horas es cero por diseño, así que un hueco equivale
        // exactamente a una hora que no se colocó: estas revisiones dicen de quién es la culpa.
        ValidacionIADTO.ResumenRevisiones rr = viabilidad.revisiones().resumen();
        if (rr.paresConDeficit() > 0) {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Cupo real por maestro y grupo", "ADVERTENCIA",
                    rr.paresConDeficit() + " pares (maestro, grupo) deben más horas de las que caben en "
                            + "sus bloques comunes: " + rr.horasDeficit() + " h imposibles."));
        } else {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Cupo real por maestro y grupo", "OK",
                    "Ningún par (maestro, grupo) debe más horas que bloques tiene en común."));
        }
        if (rr.bloquesConUnMaestro() > 0) {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Bloques con un solo maestro posible",
                    "ADVERTENCIA",
                    rr.bloquesConUnMaestro() + " bloques de " + rr.gruposConBloqueUnico()
                            + " grupos dependen de un único maestro (y "
                            + rr.bloquesConDosMaestros() + " más tienen solo 2)."));
        } else {
            chequeos.add(new ValidacionIADTO.ChequeoIA("Bloques con un solo maestro posible", "OK",
                    "Ningún bloque depende de un único maestro: siempre hay al menos 2 opciones."));
        }

        boolean apto = criticos.isEmpty() && chequeos.stream()
                .noneMatch(c -> "ERROR".equals(c.estado()));

        logger.info("Pre-validación IA: {} errores del backend, {} imposibles, {} maestros en déficit, "
                        + "apto={}", criticos.size(), imposibles.size(),
                viabilidad.resumen().maestrosEnDeficit(), apto);
        logger.info("Revisiones IA: {} pares con déficit ({} h), {} bloques con 1 maestro",
                rr.paresConDeficit(), rr.horasDeficit(), rr.bloquesConUnMaestro());

        return new ValidacionIADTO(backend, chequeos, imposibles, apto,
                datos.grupos().size(), datos.asignaciones().size(), totalSesiones, datos.bloques().size(),
                horasDemandadas, totalVentanas, viabilidad);
    }

    // ============================================================
    // ANÁLISIS DE VIABILIDAD
    // ============================================================

    /**
     * ANÁLISIS DE VIABILIDAD DEL REPARTO: ¿cabe esto? ¿y quién va más justo?
     *
     * <p>Es el diagnóstico que responde a "el motor no coloca todo" cuando la culpa NO es del motor:
     * hay maestros con más horas asignadas que huecos legales posibles, o bloques de un grupo que no
     * puede cubrir ningún maestro suyo. Nada de esto bloquea la generación.
     *
     * <h3>Unidades (importante para leer los números)</h3>
     * Todo se mide en BLOQUES, que son horas de clase: {@code horasAsignadas} son horas (campo
     * {@code horas} de la asignación) y {@code ventanasLegales} son bloques. Comparar "horas" contra
     * "bloques" es legítimo porque cada bloque es una hora, pero es una COTA SUPERIOR de capacidad: no
     * mira contigüidad ni la regla de una sesión por día. Si sale déficit aquí, es imposible de verdad;
     * si sale holgado, todavía puede fallar por esas otras reglas.
     *
     * <h3>Índice de holgura por grupo</h3>
     * {@code indiceHolgura = maestrosPromedio + (bloquesDisponibles - horasNecesarias)}
     * donde {@code maestrosPromedio} es el promedio de maestros disponibles en cada bloque disponible
     * del grupo. Son "cuántas opciones tengo por hora": cuántos maestros puedo elegir por bloque, más
     * cuántas horas de sobra tengo en el turno. Cuanto MÁS BAJO, más justo va el grupo:
     * <ul>
     *   <li>{@code IMPOSIBLE}: alguna hora no la puede dar nadie (0 maestros en un bloque) o el grupo
     *       pide más horas que bloques tiene.</li>
     *   <li>{@code AJUSTADO}: índice menor que {@value #UMBRAL_GRUPO_AJUSTADO} (elegir maestro y hora a
     *       la vez es casi forzado; aquí conviene reacomodar maestros).</li>
     *   <li>{@code HOLGADO}: el resto.</li>
     * </ul>
     * La lista {@code desbalance} sale de este índice ordenado de menor a mayor (el más justo arriba).
     *
     * <h3>Dos revisiones finas</h3>
     * Además del reparto bruto, el análisis incluye las dos revisiones de
     * {@code analizarRevisiones}, que sí miran la capacidad real de cada par maestro-grupo y los
     * bloques con pocos maestros posibles para responder a "¿qué impide colocar CADA hora?".
     */
    private ValidacionIADTO.AnalisisViabilidad analizarViabilidad(
            DatosIA datos,
            Map<Long, Set<Long>> dispG,
            Map<Long, Set<Long>> dispM,
            Map<Long, Map<Integer, List<TurnoHorario>>> porTurnoDia) {

        // Bloques ÚNICOS por turno: si algún día un grupo compartiera turno con otro, contar los
        // bloques turno por turno duplicaría el mismo bloque y el déficit saldría mal.
        Map<Long, Set<Long>> bloquesPorTurno = new HashMap<>();
        Map<Long, String> etiquetaBloque = new HashMap<>();
        for (TurnoHorario b : datos.bloques()) {
            if (b.getTurno() == null || b.getTurno().getTurnoId() == null) {
                continue;
            }
            etiquetaBloque.putIfAbsent(b.getId(), etiquetaBloque(b));
            bloquesPorTurno.computeIfAbsent(b.getTurno().getTurnoId(), k -> new LinkedHashSet<>())
                    .add(b.getId());
        }

        // Asignaciones que realmente piden clase (con grupo y maestro), agrupadas por maestro y grupo.
        List<Asignacion> asignaciones = datos.asignaciones().stream()
                .filter(a -> a.getGrupo() != null && a.getMaestro() != null)
                .toList();
        Map<Long, List<Asignacion>> porMaestro = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            porMaestro.computeIfAbsent(a.getMaestro().getMaestroId(), k -> new ArrayList<>()).add(a);
        }
        Map<Long, List<Asignacion>> porGrupo = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            porGrupo.computeIfAbsent(a.getGrupo().getGrupoId(), k -> new ArrayList<>()).add(a);
        }
        // Índice de grupos por id: lo usan las revisiones finas para resolver turno y nombre con el id.
        Map<Long, Grupo> gruposPorId = new LinkedHashMap<>();
        for (Grupo g : datos.grupos()) {
            gruposPorId.put(g.getGrupoId(), g);
        }

        // ── por maestro ──
        List<ValidacionIADTO.MaestroViabilidad> maestros = new ArrayList<>();
        int maestrosEnDeficit = 0;
        int horasSinHueco = 0;
        for (Map.Entry<Long, List<Asignacion>> e : porMaestro.entrySet()) {
            List<Asignacion> suyas = e.getValue();
            Set<Long> dispMaestro = dispM.getOrDefault(e.getKey(), Set.of());

            int horas = suyas.stream().mapToInt(a -> a.getHoras() == null ? 0 : a.getHoras()).sum();
            Set<Long> horarioAsignado = new HashSet<>();
            Set<String> materias = new LinkedHashSet<>();
            Set<Long> grupos = new LinkedHashSet<>();
            for (Asignacion a : suyas) {
                grupos.add(a.getGrupo().getGrupoId());
                Long turnoId = a.getGrupo().getTurno() != null
                        ? a.getGrupo().getTurno().getTurnoId() : null;
                if (turnoId != null) {
                    horarioAsignado.addAll(bloquesPorTurno.getOrDefault(turnoId, Set.of()));
                }
                if (a.getMateria() != null) {
                    materias.add((a.getMateria().getClave() == null ? "" : a.getMateria().getClave() + " ")
                            + (a.getMateria().getNombre() == null ? "" : a.getMateria().getNombre()));
                }
            }

            // Un bloque cuenta como ventana si el maestro está disponible Y al menos uno de sus
            // grupos también (el maestro puede dar clase a cualquiera de sus grupos en ese bloque).
            int ventanas = 0;
            for (Long bloqueId : horarioAsignado) {
                if (!dispMaestro.contains(bloqueId)) {
                    continue;
                }
                for (Long grupoId : grupos) {
                    if (dispG.getOrDefault(grupoId, Set.of()).contains(bloqueId)) {
                        ventanas++;
                        break;
                    }
                }
            }

            int deficit = Math.max(0, horas - ventanas);
            if (deficit > 0) {
                maestrosEnDeficit++;
                horasSinHueco += deficit;
            }
            String severidad = deficit > 0 ? "IMPOSIBLE"
                    : (horas - ventanas <= 1 ? "AJUSTADO" : "HOLGADO");
            maestros.add(new ValidacionIADTO.MaestroViabilidad(e.getKey(),
                    suyas.get(0).getMaestro().getTituloNombreCompleto(), horas, ventanas, deficit,
                    new ArrayList<>(materias), grupos.size(), severidad));
        }
        // El que más horas no puede colocar, primero; a igualdad, el que más horas pide.
        maestros.sort(Comparator.comparingInt(ValidacionIADTO.MaestroViabilidad::deficit).reversed()
                .thenComparing(Comparator.comparingInt(
                        ValidacionIADTO.MaestroViabilidad::horasAsignadas).reversed()));

        // ── por grupo ──
        List<ValidacionIADTO.GrupoViabilidad> grupos = new ArrayList<>();
        int gruposImposibles = 0;
        int bloquesSinMaestroTotal = 0;
        for (Grupo g : datos.grupos()) {
            List<Asignacion> suyas = porGrupo.getOrDefault(g.getGrupoId(), List.of());
            int horas = suyas.stream().mapToInt(a -> a.getHoras() == null ? 0 : a.getHoras()).sum();

            Set<Long> bloqueTurno = g.getTurno() != null
                    ? bloquesPorTurno.getOrDefault(g.getTurno().getTurnoId(), Set.of()) : Set.of();
            Set<Long> disponibles = new LinkedHashSet<>(dispG.getOrDefault(g.getGrupoId(), Set.of()));
            disponibles.retainAll(bloqueTurno);

            Set<Long> maestrosDelGrupo = new LinkedHashSet<>();
            for (Asignacion a : suyas) {
                maestrosDelGrupo.add(a.getMaestro().getMaestroId());
            }

            // Para cada bloque disponible del grupo: cuántos de sus maestros pueden dar clase ahí.
            int sumaMaestros = 0;
            List<String> bloquesCero = new ArrayList<>();
            for (Long bloqueId : disponibles) {
                int cuantos = 0;
                for (Long maestroId : maestrosDelGrupo) {
                    if (dispM.getOrDefault(maestroId, Set.of()).contains(bloqueId)) {
                        cuantos++;
                    }
                }
                sumaMaestros += cuantos;
                if (cuantos == 0) {
                    bloquesCero.add(etiquetaBloque.getOrDefault(bloqueId, "bloque " + bloqueId));
                }
            }
            double promedio = disponibles.isEmpty() ? 0.0 : (double) sumaMaestros / disponibles.size();
            int sobra = disponibles.size() - horas;
            double indice = promedio + sobra;

            String severidad;
            if (!bloquesCero.isEmpty() || sobra < 0) {
                severidad = "IMPOSIBLE";
                gruposImposibles++;
            } else if (indice < UMBRAL_GRUPO_AJUSTADO) {
                severidad = "AJUSTADO";
            } else {
                severidad = "HOLGADO";
            }
            bloquesSinMaestroTotal += bloquesCero.size();
            grupos.add(new ValidacionIADTO.GrupoViabilidad(g.getGrupoId(), g.getNombre(), horas,
                    disponibles.size(), redondear(promedio), maestrosDelGrupo.size(), bloquesCero.size(),
                    bloquesCero, redondear(indice), severidad));
        }

        // Desbalance: de más a menos ajustado. Primero los imposibles y, dentro de cada grupo, el
        // índice de holgura más bajo (el más justo) arriba.
        List<ValidacionIADTO.GrupoViabilidad> ordenados = new ArrayList<>(grupos);
        ordenados.sort(Comparator
                .comparingInt((ValidacionIADTO.GrupoViabilidad g) -> "IMPOSIBLE".equals(g.severidad()) ? 0 : 1)
                .thenComparingDouble(ValidacionIADTO.GrupoViabilidad::indiceHolgura)
                .thenComparing(ValidacionIADTO.GrupoViabilidad::grupo,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        grupos = ordenados;

        List<ValidacionIADTO.GrupoDesbalance> desbalance = grupos.stream()
                .map(g -> new ValidacionIADTO.GrupoDesbalance(g.grupoId(), g.grupo(),
                        g.horasNecesarias(), g.bloquesDisponibles(), g.indiceHolgura(), g.severidad()))
                .toList();
        int gruposAjustados = (int) grupos.stream()
                .filter(g -> "AJUSTADO".equals(g.severidad())).count();

        logger.info("Viabilidad IA: {} maestros ({} en déficit, {} h sin hueco), {} grupos "
                        + "({} imposibles, {} ajustados), {} bloques sin maestro",
                maestros.size(), maestrosEnDeficit, horasSinHueco, grupos.size(), gruposImposibles,
                gruposAjustados, bloquesSinMaestroTotal);

        // ── las dos revisiones finas (diagnóstico, nunca bloquea) ──
        ValidacionIADTO.RevisionesViabilidad revisiones = analizarRevisiones(
                contextoRevisiones(datos, dispG, dispM, porTurnoDia, etiquetaBloque, gruposPorId));

        return new ValidacionIADTO.AnalisisViabilidad(maestros, grupos, desbalance,
                new ValidacionIADTO.ResumenViabilidad(maestrosEnDeficit, horasSinHueco, gruposImposibles,
                        bloquesSinMaestroTotal, gruposAjustados),
                revisiones);
    }

    // ============================================================
    // LAS DOS REVISIONES FINAS
    // ============================================================

    /**
     * CONTEXTO COMPARTIDO DE LAS DOS REVISIONES.
     *
     * <p>Se calcula una sola vez y se pasa a las dos: así ninguna repite trabajo pesado (los índices
     * de bloques por turno y por maestro, los nombres y la relación bloque → turno).
     *
     * @param gruposPorId  grupos del alcance indexados por id
     * @param asignaciones asignaciones con grupo y maestro (las que realmente piden clase)
     * @param bloquesPorTurno bloques del turno de cada grupo (los que el motor puede usar)
     * @param porTurnoDia  bloques del turno agrupados por día y ordenados por {@code orden}
     * @param bloquePorId  turno al que pertenece cada bloque
     * @param etiquetaBloque "Lunes 07:00-08:00" de cada bloque
     * @param disponiblesGrupo bloques disponibles de cada grupo (intersección con el turno ya aplicada)
     * @param disponiblesMaestro bloques disponibles de cada maestro
     * @param gruposDeMaestro ids de los grupos a los que da clase cada maestro
     */
    private record ContextoRevisiones(
            Map<Long, Grupo> gruposPorId,
            List<Asignacion> asignaciones,
            Map<Long, Set<Long>> bloquesPorTurno,
            Map<Long, Map<Integer, List<TurnoHorario>>> porTurnoDia,
            Map<Long, Long> bloquePorId,
            Map<Long, String> etiquetaBloque,
            Map<Long, Set<Long>> disponiblesGrupo,
            Map<Long, Set<Long>> disponiblesMaestro,
            Map<Long, Set<Long>> gruposDeMaestro) {
    }

    /** Arma el contexto compartido de las revisiones a partir de los datos y las disponibilidades. */
    private ContextoRevisiones contextoRevisiones(
            DatosIA datos,
            Map<Long, Set<Long>> dispG,
            Map<Long, Set<Long>> dispM,
            Map<Long, Map<Integer, List<TurnoHorario>>> porTurnoDia,
            Map<Long, String> etiquetaBloque,
            Map<Long, Grupo> gruposPorId) {

        Map<Long, Set<Long>> bloquesPorTurno = new HashMap<>();
        Map<Long, Long> bloquePorId = new HashMap<>();
        for (TurnoHorario b : datos.bloques()) {
            if (b.getTurno() == null || b.getTurno().getTurnoId() == null) {
                continue;
            }
            bloquePorId.put(b.getId(), b.getTurno().getTurnoId());
            bloquesPorTurno.computeIfAbsent(b.getTurno().getTurnoId(), k -> new LinkedHashSet<>())
                    .add(b.getId());
        }

        List<Asignacion> asignaciones = datos.asignaciones().stream()
                .filter(a -> a.getGrupo() != null && a.getMaestro() != null)
                .toList();

        Map<Long, Set<Long>> disponiblesGrupo = new HashMap<>();
        for (Grupo g : datos.grupos()) {
            Set<Long> bloqueTurno = g.getTurno() != null
                    ? bloquesPorTurno.getOrDefault(g.getTurno().getTurnoId(), Set.of()) : Set.of();
            disponiblesGrupo.put(g.getGrupoId(),
                    bloquesEnTurno(dispG.getOrDefault(g.getGrupoId(), Set.of()), bloqueTurno));
        }

        // Un maestro se mide contra los bloques de TODOS los turnos en los que da clase (puede dar
        // clase a grupos de turnos distintos, y en cada uno su disponibilidad es la misma fila).
        Map<Long, Set<Long>> bloquesDeSusTurnos = new HashMap<>();
        Map<Long, Set<Long>> gruposDeMaestro = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            Long maestroId = a.getMaestro().getMaestroId();
            gruposDeMaestro.computeIfAbsent(maestroId, k -> new LinkedHashSet<>())
                    .add(a.getGrupo().getGrupoId());
            Long turnoId = a.getGrupo().getTurno() != null
                    ? a.getGrupo().getTurno().getTurnoId() : null;
            if (turnoId != null) {
                bloquesDeSusTurnos.computeIfAbsent(maestroId, k -> new LinkedHashSet<>())
                        .addAll(bloquesPorTurno.getOrDefault(turnoId, Set.of()));
            }
        }
        Map<Long, Set<Long>> disponiblesMaestro = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : bloquesDeSusTurnos.entrySet()) {
            disponiblesMaestro.put(e.getKey(), bloquesEnTurno(
                    dispM.getOrDefault(e.getKey(), Set.of()), e.getValue()));
        }

        return new ContextoRevisiones(gruposPorId, asignaciones, bloquesPorTurno, porTurnoDia,
                bloquePorId, etiquetaBloque, disponiblesGrupo, disponiblesMaestro, gruposDeMaestro);
    }

    /**
     * LAS DOS REVISIONES: aquí se busca QUÉ IMPIDE COLOCAR CADA HORA.
     *
     * <p>El negocio tiene una particularidad que lo cambia todo: cada grupo tiene EXACTAMENTE las
     * mismas horas que bloques disponibles (36 h en 36 bloques, 33 en 33). La holgura de horas es CERO
     * por diseño, así que un hueco en el horario equivale exactamente a una hora que no se colocó: no
     * hay margen para compensar moviendo clases. Por eso estas revisiones no miden "cuánto sobra",
     * sino "qué bloquea cada hora concreta":
     *
     * <ol>
     *   <li><b>Cupo real por maestro y grupo</b>: para cada par (maestro, grupo), horas que debe dar
     *       contra bloques en los que los dos están libres a la vez. Si las horas superan esos bloques
     *       comunes, hay un déficit real e imposible de resolver. Es más fino que el déficit global del
     *       maestro: explica el caso típico de un maestro que da la misma materia en 3 grupos con pocos
     *       bloques comunes con alguno de ellos.</li>
     *   <li><b>Bloques con pocos maestros posibles</b>: por grupo, cuántos de sus maestros están
     *       disponibles en cada bloque. Con 1 solo maestro es un PUNTO ÚNICO DE FALLO: si ese maestro se
     *       ocupa en otro grupo, ese bloque queda libre garantizado (y con holgura cero, eso es una hora
     *       perdida). También se cuentan los bloques con 2.</li>
     * </ol>
     *
     * <p>INFORMACIÓN, nunca error: nada de esto cambia {@code aptoParaGenerar}.
     */
    private ValidacionIADTO.RevisionesViabilidad analizarRevisiones(ContextoRevisiones ctx) {

        List<ValidacionIADTO.CupoMaestroGrupo> cupo = revisarCupoMaestroGrupo(ctx);
        List<ValidacionIADTO.GrupoBloquesApretados> bloquesApretados = revisarBloquesApretados(ctx);

        int paresConDeficit = (int) cupo.stream()
                .filter(c -> c.deficit() > 0).count();
        int horasDeficit = cupo.stream().mapToInt(ValidacionIADTO.CupoMaestroGrupo::deficit).sum();
        int gruposConBloqueUnico = (int) bloquesApretados.stream()
                .filter(g -> g.bloquesConUno() > 0).count();
        int bloquesConUnMaestro = bloquesApretados.stream()
                .mapToInt(ValidacionIADTO.GrupoBloquesApretados::bloquesConUno).sum();
        int bloquesConDosMaestros = bloquesApretados.stream()
                .mapToInt(ValidacionIADTO.GrupoBloquesApretados::bloquesConDos).sum();

        logger.info("Revisiones IA: {} pares con déficit ({} h), {} bloques con 1 maestro y {} con 2",
                paresConDeficit, horasDeficit, bloquesConUnMaestro, bloquesConDosMaestros);

        return new ValidacionIADTO.RevisionesViabilidad(cupo, bloquesApretados,
                new ValidacionIADTO.ResumenRevisiones(paresConDeficit,
                horasDeficit, gruposConBloqueUnico, bloquesConUnMaestro, bloquesConDosMaestros));
    }

    /**
     * REVISIÓN 1 · CUPO REAL DE CADA PAR (MAESTRO, GRUPO).
     *
     * <p>Compara las horas que ese maestro DEBE dar en ese grupo (suma de {@code asignacion.horas} de
     * sus materias en ese grupo) contra los bloques en los que el maestro Y el grupo están disponibles
     * A LA VEZ, que son los únicos donde esa clase puede caer. Si debe más horas que bloques comunes,
     * el déficit es real: no cabe por muchas vueltas que dé el motor.
     *
     * <p>Por qué es más fino que la revisión que ya existía: aquella medía las horas del maestro contra
     * TODOS sus huecos (de todos sus grupos juntos) y podía dar holgado aunque el reparto por grupo
     * fuera imposible. Un maestro que da la misma materia en tres grupos con pocos bloques comunes con
     * uno de ellos sale aquí y no allí.
     *
     * <p>Las horas se suman por MATERIA dentro del par: si la misma materia y grupo está partida en dos
     * filas de asignación, las dos cuentan.
     */
    private List<ValidacionIADTO.CupoMaestroGrupo> revisarCupoMaestroGrupo(ContextoRevisiones ctx) {
        // Clave del par: maestro + grupo (el grupo va después como Long, no como texto, para no
        // confundir el grupo 1 con el 10).
        record Par(Long maestroId, Long grupoId) {
        }

        Map<Par, Map<String, Integer>> horasPorMateria = new LinkedHashMap<>();
        Map<Par, Asignacion> primera = new LinkedHashMap<>();
        for (Asignacion a : ctx.asignaciones()) {
            Par par = new Par(a.getMaestro().getMaestroId(), a.getGrupo().getGrupoId());
            primera.putIfAbsent(par, a);
            horasPorMateria.computeIfAbsent(par, k -> new LinkedHashMap<>())
                    .merge(nombreMateria(a), a.getHoras() == null ? 0 : a.getHoras(), Integer::sum);
        }

        List<ValidacionIADTO.CupoMaestroGrupo> cupo = new ArrayList<>();
        for (Map.Entry<Par, Map<String, Integer>> e : horasPorMateria.entrySet()) {
            Asignacion a = primera.get(e.getKey());
            int horas = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            int bloquesComunes = comunes(ctx, e.getKey().grupoId(), e.getKey().maestroId()).size();
            int deficit = Math.max(0, horas - bloquesComunes);
            cupo.add(new ValidacionIADTO.CupoMaestroGrupo(e.getKey().maestroId(),
                    a.getMaestro().getTituloNombreCompleto(), e.getKey().grupoId(),
                    a.getGrupo().getNombre(), horas, bloquesComunes, deficit,
                    new ArrayList<>(e.getValue().keySet()),
                    severidadCapacidad(deficit, horas - bloquesComunes)));
        }
        // El más grave arriba: primero el que más horas no puede colocar y, a igualdad, el que más pide.
        cupo.sort(Comparator.comparingInt(ValidacionIADTO.CupoMaestroGrupo::deficit).reversed()
                .thenComparing(Comparator.comparingInt(
                        ValidacionIADTO.CupoMaestroGrupo::horasEnGrupo).reversed()));
        return cupo;
    }

    /**
     * REVISIÓN 2 · BLOQUES CON POCOS MAESTROS POSIBLES.
     *
     * <p>Por grupo se cuenta cuántos de SUS maestros están disponibles en cada bloque disponible del
     * grupo. Se señalan los bloques con 1 solo maestro posible: es un PUNTO ÚNICO DE FALLO. Si ese
     * maestro se ocupa en otro grupo en ese mismo bloque, este grupo se queda sin nadie que pueda dar
     * esa hora. Y como la holgura de horas es cero, esa hora perdida no se recupera moviendo clases.
     *
     * <p>Se cuentan también los bloques con 2 maestros: no son un imposible, pero un choque de
     * disponibilidad basta para convertirlos en el mismo problema.
     */
    private List<ValidacionIADTO.GrupoBloquesApretados> revisarBloquesApretados(ContextoRevisiones ctx) {
        List<ValidacionIADTO.GrupoBloquesApretados> grupos = new ArrayList<>();
        for (Grupo g : ctx.gruposPorId().values()) {
            Set<Long> disponibles = ctx.disponiblesGrupo().getOrDefault(g.getGrupoId(), Set.of());
            Set<Long> maestrosDelGrupo = maestrosDelGrupo(ctx, g.getGrupoId());

            int conUno = 0;
            int conDos = 0;
            int minimo = Integer.MAX_VALUE;
            List<ValidacionIADTO.BloquePocosMaestros> detalle = new ArrayList<>();
            // En orden de día y hora, que es como se lee el horario.
            for (TurnoHorario bloque : bloquesEnOrden(ctx, disponibles)) {
                Long bloqueId = bloque.getId();
                List<Long> posibles = maestrosPosibles(ctx, maestrosDelGrupo, bloqueId);
                minimo = Math.min(minimo, posibles.size());
                if (posibles.size() == 1) {
                    conUno++;
                    detalle.add(new ValidacionIADTO.BloquePocosMaestros(
                            ctx.etiquetaBloque().getOrDefault(bloqueId, "bloque " + bloqueId), 1,
                            nombreMaestro(ctx, posibles.get(0))));
                } else if (posibles.size() == 2) {
                    conDos++;
                    detalle.add(new ValidacionIADTO.BloquePocosMaestros(
                            ctx.etiquetaBloque().getOrDefault(bloqueId, "bloque " + bloqueId), 2, null));
                }
            }
            if (disponibles.isEmpty()) {
                // Sin bloques disponibles no hay mínimo que enseñar: se deja 0 en vez de MAX_VALUE.
                minimo = 0;
            }
            grupos.add(new ValidacionIADTO.GrupoBloquesApretados(g.getGrupoId(), g.getNombre(),
                    disponibles.size(), conUno, conDos, minimo, detalle));
        }
        // El grupo con más puntos únicos de fallo arriba; a igualdad, el que tenga menos maestros por bloque.
        grupos.sort(Comparator
                .comparingInt(ValidacionIADTO.GrupoBloquesApretados::bloquesConUno).reversed()
                .thenComparingInt(ValidacionIADTO.GrupoBloquesApretados::maestrosMinimo)
                .thenComparing(ValidacionIADTO.GrupoBloquesApretados::grupo,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return grupos;
    }

    // ============================================================
    // AUXILIARES DE LAS REVISIONES
    // ============================================================

    /**
     * "Imposible / ajustado / holgado" según cuánto sobra. Se usa en las revisiones que comparan una
     * capacidad con una necesidad (0 o 1 de sobra ya es ir justo).
     */
    private static String severidadCapacidad(int deficit, int sobra) {
        if (deficit > 0) {
            return "IMPOSIBLE";
        }
        return sobra <= 1 ? "AJUSTADO" : "HOLGADO";
    }

    /** Bloques de un grupo que además pertenecen a su turno (los únicos que el motor puede usar). */
    private static Set<Long> bloquesEnTurno(Set<Long> bloques, Set<Long> bloqueTurno) {
        Set<Long> salida = new LinkedHashSet<>(bloques);
        salida.retainAll(bloqueTurno);
        return salida;
    }

    /** "Clave Nombre" de una materia, que es como se enseña en la pantalla. */
    private static String nombreMateria(Asignacion a) {
        if (a.getMateria() == null) {
            return "?";
        }
        String clave = a.getMateria().getClave() == null ? "" : a.getMateria().getClave() + " ";
        return clave + (a.getMateria().getNombre() == null ? "" : a.getMateria().getNombre());
    }

    private static String nombreMaestro(ContextoRevisiones ctx, Long maestroId) {
        for (Asignacion a : ctx.asignaciones()) {
            if (a.getMaestro().getMaestroId().equals(maestroId)) {
                return a.getMaestro().getTituloNombreCompleto();
            }
        }
        return "maestro " + maestroId;
    }

    /** Los bloques de un grupo ordenados por día y hora, que es como se lee el horario. */
    private List<TurnoHorario> bloquesEnOrden(ContextoRevisiones ctx, Set<Long> ids) {
        List<TurnoHorario> orden = new ArrayList<>();
        if (ids.isEmpty()) {
            return orden;
        }
        // Todos los bloques que se comparan son del turno del grupo (la disponibilidad ya se recortó
        // contra él), así que basta con recorrer los días de ese turno en orden.
        Map<Integer, List<TurnoHorario>> dias = ctx.porTurnoDia()
                .getOrDefault(ctx.bloquePorId().get(ids.iterator().next()), Map.of());
        for (List<TurnoHorario> arr : dias.values()) {
            for (TurnoHorario b : arr) {
                if (ids.contains(b.getId())) {
                    orden.add(b);
                }
            }
        }
        return orden;
    }

    /** Ids de los maestros que dan clase en ese grupo. */
    private static Set<Long> maestrosDelGrupo(ContextoRevisiones ctx, Long grupoId) {
        Set<Long> maestros = new LinkedHashSet<>();
        for (Asignacion a : ctx.asignaciones()) {
            if (a.getGrupo().getGrupoId().equals(grupoId)) {
                maestros.add(a.getMaestro().getMaestroId());
            }
        }
        return maestros;
    }

    /** De los maestros del grupo, cuáles están disponibles en ese bloque. */
    private static List<Long> maestrosPosibles(ContextoRevisiones ctx, Set<Long> maestros, Long bloqueId) {
        List<Long> posibles = new ArrayList<>();
        for (Long maestroId : maestros) {
            if (ctx.disponiblesMaestro().getOrDefault(maestroId, Set.of()).contains(bloqueId)) {
                posibles.add(maestroId);
            }
        }
        return posibles;
    }

    /** Bloques en los que el grupo y el maestro están disponibles a la vez. */
    private static Set<Long> comunes(ContextoRevisiones ctx, Long grupoId, Long maestroId) {
        Set<Long> salida = new LinkedHashSet<>(
                ctx.disponiblesGrupo().getOrDefault(grupoId, Set.of()));
        salida.retainAll(ctx.disponiblesMaestro().getOrDefault(maestroId, Set.of()));
        return salida;
    }

    /** Dos decimales, que es lo que se enseña en la pantalla. */
    private static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** "Lunes 07:00-08:00", para poder señalar el bloque exacto que nadie puede dar. */
    private static String etiquetaBloque(TurnoHorario b) {
        String dia = switch (b.getDiaSemana() == null ? 0 : b.getDiaSemana()) {
            case 1 -> "Lunes";
            case 2 -> "Martes";
            case 3 -> "Miércoles";
            case 4 -> "Jueves";
            case 5 -> "Viernes";
            case 6 -> "Sábado";
            case 7 -> "Domingo";
            default -> "Día " + b.getDiaSemana();
        };
        return dia + " " + b.getHoraInicio() + "-" + b.getHoraFin();
    }

    private int contarVentanasLegales(Map<Integer, List<TurnoHorario>> porDia,
                                      Set<Long> dispG, Set<Long> dispM, int dur) {
        if (dispG == null || dispM == null) {
            return 0;
        }
        int total = 0;
        for (List<TurnoHorario> arr : porDia.values()) {
            for (int p = 0; p + dur - 1 < arr.size(); p++) {
                boolean ok = true;
                for (int x = p; x < p + dur; x++) {
                    if (x > p && minutos(arr.get(x).getHoraInicio()) - minutos(arr.get(x - 1).getHoraFin())
                            > ReglasIA.TOLERANCIA_CONTIGUIDAD_MIN) {
                        ok = false;
                        break;
                    }
                    Long id = arr.get(x).getId();
                    if (!dispG.contains(id) || !dispM.contains(id)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    total++;
                }
            }
        }
        return total;
    }

    // ============================================================
    // INTENTOS
    // ============================================================

    /** Asesor configurado: el LLM solo si el modo es {@code llm} y hay clave. */
    public AsesorIA asesor(String modo) {
        return asesor(modo, null, null, null);
    }

    /**
     * Asesor para UNA generación concreta.
     *
     * <p>La clave puede venir en la propia petición: en ese caso se usa solo para este trabajo y se
     * descarta al terminar (no se guarda en ningún fichero, no se escribe en el log y no se devuelve
     * en ninguna respuesta). Si no viene, se usa la de {@code app.ia.api-key}. En modo {@code llm} sin
     * ninguna de las dos se corta aquí con un mensaje claro, en vez de generar sin asesor y sin avisar.
     */
    public AsesorIA asesor(String modo, String apiKey, String url, String modelo) {
        if (!"llm".equalsIgnoreCase(modo)) {
            return new AsesorHeuristico();
        }
        String clave = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : iaApiKey;
        if (!AsesorLLM.configurado(clave)) {
            throw new NegocioExcepcion("falta_api_key",
                    "Para usar el asesor IA hay que indicar la clave de la API.");
        }
        String destino = url != null && !url.isBlank() ? url.trim() : iaUrl;
        String elegido = modelo != null && !modelo.isBlank() ? modelo.trim() : iaModelo;
        logger.info("Horario IA: asesor LLM {} (clave propia de la generación: {})",
                elegido, apiKey != null && !apiKey.isBlank());
        return new AsesorLLM(clave, destino, elegido, iaTiempoLimite);
    }

    public boolean llmConfigurado() {
        return AsesorLLM.configurado(iaApiKey);
    }

    /** Modelo configurado, para proponerlo en la pantalla cuando se pide la clave. */
    public String getModeloPorDefecto() {
        return iaModelo;
    }

    public int getIntentosPorDefecto() {
        return 6;
    }

    /** Un intento completo del motor IA, con tope de tiempo propio. */
    public IntentoIA intento(DatosIA datos, int numero, long semilla, int segundosMax, int maxPasos,
                             boolean asignarMaestros, boolean asignarAulas, AsesorIA asesor,
                             Consumer<String> log) {
        IntentoIA intento = new GeneradorIA()
                .generarIntento(datos, numero, maxPasos, semilla, segundosMax,
                        asignarMaestros, asignarAulas, asesor, log);
        if (asesor != null && asesor.nota() != null) {
            log.accept("  asesor: " + asesor.nota());
        }
        return intento;
    }

    /** Compara dos intentos: primero el que no tiene problemas, luego más horas, luego mejor score. */
    public static int comparar(IntentoIA a, IntentoIA b) {
        boolean av = a.getProblemas().isEmpty();
        boolean bv = b.getProblemas().isEmpty();
        if (av != bv) {
            return av ? -1 : 1;
        }
        if (a.getHoras() != b.getHoras()) {
            return Integer.compare(b.getHoras(), a.getHoras());
        }
        if (a.getPendientes().size() != b.getPendientes().size()) {
            return Integer.compare(a.getPendientes().size(), b.getPendientes().size());
        }
        return Integer.compare(b.getMedium(), a.getMedium());
    }

    public IntentoIA mejor(List<IntentoIA> intentos) {
        return intentos.stream().min(HorarioIAServicio::comparar).orElse(null);
    }

    // ============================================================
    // REGISTRO EN EL HORARIO REAL
    // ============================================================

    /** Resultado de registrar un intento: qué se escribió en la tabla `horario`. */
    public record RegistroIA(int filas, int horas, int grupos, int pendientes) {
    }

    /**
     * Escribe en la tabla `horario` el intento indicado, REEMPLAZANDO el horario de los grupos del
     * alcance. Dos tandas obligatorias (borrar + flush, insertar + flush) dentro de la misma
     * transacción: si algo falla, no se toca nada.
     */
    @Transactional
    public RegistroIA registrar(Long semestreIdParam, Long turnoIdParam, IntentoIA intento) {
        Long escuelaId = getEscuelaId();

        if (intento == null) {
            throw new NegocioExcepcion("sin_intento", "No hay ningún intento que registrar.");
        }
        if (!intento.getProblemas().isEmpty()) {
            throw new NegocioExcepcion("intento_con_problemas",
                    "El intento " + intento.getNumero() + " tiene " + intento.getProblemas().size()
                            + " problemas y no se puede registrar: "
                            + String.join("; ", recortarLista(intento.getProblemas())));
        }
        if (intento.getHoras() <= 0) {
            throw new NegocioExcepcion("intento_vacio",
                    "El intento " + intento.getNumero() + " no coloca ninguna hora.");
        }

        // Los datos se recargan AQUÍ, dentro de la transacción: así las entidades son gestionadas
        // (no hace falta pasar entidades desligadas a Hibernate) y el registro usa la información
        // más reciente, no la que había cuando se lanzó el intento.
        DatosIA datos = cargarDatos(semestreIdParam, turnoIdParam);
        Long semestreId = resolverSemestre(escuelaId, semestreIdParam).getSemestreId();

        Map<Long, Asignacion> asignaciones = new HashMap<>();
        for (Asignacion a : datos.asignaciones()) {
            asignaciones.put(a.getAsignacionId(), a);
        }
        Map<Long, TurnoHorario> bloques = new HashMap<>();
        for (TurnoHorario b : datos.bloques()) {
            bloques.put(b.getId(), b);
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);
        Semestre semestre = new Semestre();
        semestre.setSemestreId(semestreId);

        List<Horario> filas = new ArrayList<>(intento.getFilas().size());
        for (IntentoIA.Fila fila : intento.getFilas()) {
            Asignacion a = asignaciones.get(fila.getAsignacionId());
            TurnoHorario b = bloques.get(fila.getTurnoHorarioId());
            if (a == null || b == null) {
                throw new NegocioExcepcion("intento_incoherente",
                        "El intento " + intento.getNumero() + " referencia una asignación o un bloque "
                                + "que ya no existe. Revalida y vuelve a generar.");
            }
            Horario horario = new Horario();
            horario.setEscuela(escuela);
            horario.setGrupo(a.getGrupo());
            horario.setAsignacion(a);
            horario.setTurnoHorario(b);
            horario.setAula(a.getAula());
            // En modo maestros y/o modo aulas, la fila trae el maestro y el taller que eligió el
            // motor; si no, los de la asignación. Se comprueba cada uno por separado, porque las dos
            // banderas son independientes.
            horario.setMaestroId(fila.getMaestroId() != null && fila.getMaestroId() > 0
                    ? fila.getMaestroId() : a.getMaestro().getMaestroId());
            if (fila.getAulaId() != null && fila.getAulaId() > 0) {
                Aula aula = new Aula();
                aula.setAulaId(fila.getAulaId());
                horario.setAula(aula);
            } else {
                horario.setAula(a.getAula());
            }
            horario.setVersion(1);
            horario.setSemestre(semestre);
            filas.add(horario);
        }

        try {
            // Tanda 1: borrar el horario anterior de TODOS los grupos del alcance (y vaciar la sesión).
            // Se borra también el de los grupos que este intento deja sin clases: si no, se quedaría
            // el horario viejo de esos grupos mezclado con el nuevo.
            for (Grupo g : datos.grupos()) {
                horarioRepositorio.deleteByGrupoIdAndVersionAndSemestreId(
                        g.getGrupoId(), 1, escuelaId, semestreId);
            }
            horarioRepositorio.flush();

            // Tanda 2: insertar el horario nuevo.
            horarioRepositorio.saveAll(filas);
            horarioRepositorio.flush();

        } catch (DataIntegrityViolationException e) {
            String solape = MensajeErrorUtil.detectarConstraintSolape(e);
            if (solape != null) {
                logger.error("🚫 Horario IA: constraint de solapamiento al registrar el intento {}: {}",
                        intento.getNumero(), solape);
                throw new NegocioExcepcion(MensajeErrorUtil.codigoDesdeConstraint(e), solape, e);
            }
            logger.error("Horario IA: error de integridad al registrar el intento {}",
                    intento.getNumero(), e);
            throw e;
        }

        logger.info("Horario IA: intento {} registrado ({} filas, {} h, {} grupos del alcance)",
                intento.getNumero(), filas.size(), intento.getHoras(), datos.grupos().size());

        return new RegistroIA(filas.size(), intento.getHoras(), datos.grupos().size(),
                intento.getPendientes().size());
    }

    // ============================================================
    // AUXILIARES
    // ============================================================

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    private Semestre resolverSemestre(Long escuelaId, Long semestreIdParam) {
        if (semestreIdParam != null) {
            return semestreRepositorio.findByIdAndEscuelaId(semestreIdParam, escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion(
                            "Semestre no encontrado con ID: " + semestreIdParam));
        }
        return semestreRepositorio
                .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
    }

    private static long minutos(java.time.LocalTime h) {
        return h == null ? -1 : h.getHour() * 60L + h.getMinute();
    }

    private static String etiqueta(Asignacion a) {
        String grupo = a.getGrupo() != null ? a.getGrupo().getNombre() : "?";
        String materia = a.getMateria() != null ? a.getMateria().getClave() : "?";
        return materia + " (" + grupo + ")";
    }

    private static String duracionesTexto(Asignacion a) {
        List<Integer> d = GeneradorIA.duracionesDe(a);
        return d.size() == 1 ? d.get(0) + " h seguidas" : d + " h repartidas";
    }

    private static List<String> recortarLista(List<String> lista) {
        return lista.size() <= 6 ? lista : new ArrayList<>(lista.subList(0, 6));
    }
}
