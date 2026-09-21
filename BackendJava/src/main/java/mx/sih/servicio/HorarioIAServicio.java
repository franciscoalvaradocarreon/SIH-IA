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
     */
    public ValidacionIADTO validar(Long semestreIdParam, Long turnoIdParam, DatosIA datos) {
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

        boolean apto = criticos.isEmpty() && chequeos.stream()
                .noneMatch(c -> "ERROR".equals(c.estado()));

        logger.info("Pre-validación IA: {} errores del backend, {} imposibles, apto={}",
                criticos.size(), imposibles.size(), apto);

        return new ValidacionIADTO(backend, chequeos, imposibles, apto,
                datos.grupos().size(), datos.asignaciones().size(), totalSesiones, datos.bloques().size(),
                horasDemandadas, totalVentanas);
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
                             boolean asignarMaestros, AsesorIA asesor, Consumer<String> log) {
        IntentoIA intento = new GeneradorIA()
                .generarIntento(datos, numero, maxPasos, semilla, segundosMax, asignarMaestros, asesor, log);
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
            // En modo stock, la fila trae el maestro y el taller que eligió el motor; si no, los de
            // la asignación.
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
