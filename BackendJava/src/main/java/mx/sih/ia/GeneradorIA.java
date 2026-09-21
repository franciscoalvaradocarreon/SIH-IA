package mx.sih.ia;

import mx.sih.modelo.entidad.Asignacion;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.Grupo;
import mx.sih.modelo.entidad.TurnoHorario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * GENERADOR DE HORARIOS SIN TIMEFOLD (motor IA).
 *
 * <p>Algoritmo propio, con las reglas de {@link ReglasIA} (las mismas que el provider del solver):
 *
 * <ol>
 *   <li><b>Construcción fail-first</b>: primero las asignaturas con menos ventanas legales; cada
 *       sesión se coloca en el día y la ventana que menos daño hace al día del grupo.</li>
 *   <li><b>Búsqueda local</b>: mover una sesión, dejarla pendiente o colocar una pendiente,
 *       aceptando solo lo que mejora el score.</li>
 *   <li><b>Reempaquetado por grupo-día</b>: liberar el día y rehacerlo desde la primera hora
 *       (búsqueda en profundidad sobre las posiciones). Es lo que quita huecos.</li>
 *   <li><b>Reempaquetado por maestro-día</b>: repartir sus sesiones del día entre sus grupos.</li>
 *   <li><b>Abrir ventana</b>: si una sesión no tiene ventana libre se libera moviendo a quien la
 *       ocupa, recursivo y con reversión atómica.</li>
 *   <li><b>Reconstruir materia o grupo entero</b> cuando queda algo pendiente.</li>
 *   <li><b>Deduplicar y comprobar</b>: se rehace la ocupación desde cero y se verifican las reglas
 *       duras; el intento se publica con la lista de problemas (0 = válido).</li>
 * </ol>
 */
public class GeneradorIA {

    /** Cuántos ciclos extra seguidos sin mejora se toleran antes de declarar convergencia. */
    private static final int MAX_CICLOS_SIN_MEJORA = 5;

    /** Un intento completo. */
    public IntentoIA generarIntento(DatosIA datos, int numero, int maxPasos, long semilla,
                                    int segundosMax, boolean asignarMaestros,
                                    AsesorIA asesor, Consumer<String> log) {
        long inicio = System.currentTimeMillis();
        long limite = segundosMax > 0 ? inicio + segundosMax * 1000L : 0L;
        Corrida c = new Corrida(datos, semilla, limite);

        // Todo lo que cuenta el motor va al log del servidor y a la bitácora del intento.
        List<String> bitacora = new ArrayList<>();
        Consumer<String> registro = mensaje -> {
            bitacora.add(mensaje);
            log.accept(mensaje);
        };

        registro.accept("Intento " + numero + ": " + c.sesiones.size() + " sesiones · " + c.grupos.size()
                + " grupos · demanda " + c.demanda + " h"
                + (segundosMax > 0 ? " · tope " + segundosMax + " s" : ""));
        c.construir(asesor, registro);
        registro.accept("  problemas duros tras construir: " + c.reconstruirMapas().size());
        c.buscarLocal(maxPasos);
        registro.accept("  problemas duros tras búsqueda local: " + c.reconstruirMapas().size());
        c.cicloReempaquetado(maxPasos, registro);
        registro.accept("  problemas duros tras reempaquetado: " + c.reconstruirMapas().size());
        c.reconstruirTodo(registro);
        registro.accept("  problemas duros tras reconstrucción: " + c.reconstruirMapas().size());

        // USAR EL PRESUPUESTO: las fases de arriba se detienen en cuanto convergen (con datos
        // holgados pasaba en 5-8 s y el tope de segundos no servía de nada). Este bucle aprovecha lo
        // que quede de tiempo: perturba (saca sesiones, sobre todo de las que bloquean a las
        // pendientes) y vuelve a buscar + reempaquetar + reconstruir. Si un ciclo no mejora se
        // revierte tal cual estaba; tras 3 ciclos sin mejora (o agotado el reloj) se detiene. Así el
        // tope de segundos vuelve a significar "cuánto buscar", no "cuánto puede durar".
        int mejorHoras = c.horasColocadas(c.sesiones);
        int mejorPendientes = c.pendientesCount();
        int sinMejora = 0;
        // 5 ciclos sin mejora (antes 3): insiste más antes de rendirse y aprovecha más del tope de
        // segundos. Si no, con datos holgados el intento convergía en pocos segundos.
        while (sinMejora < MAX_CICLOS_SIN_MEJORA && !c.agotado()) {
            List<int[]> snap = c.snapshot();
            c.perturbar();
            c.buscarLocal(maxPasos);
            c.cicloReempaquetado(maxPasos, registro);
            c.reconstruirTodo(registro);
            int horas = c.horasColocadas(c.sesiones);
            int pendientes = c.pendientesCount();
            if (horas > mejorHoras || (horas == mejorHoras && pendientes < mejorPendientes)) {
                mejorHoras = horas;
                mejorPendientes = pendientes;
                sinMejora = 0;
                registro.accept("  ciclo extra: " + horas + " h · pendientes " + pendientes);
            } else {
                c.restaurarTodo(snap);
                sinMejora++;
            }
        }
        if (sinMejora >= MAX_CICLOS_SIN_MEJORA) {
            registro.accept("  convergió: " + MAX_CICLOS_SIN_MEJORA + " ciclos extra sin mejora ("
                    + mejorHoras + " h, " + mejorPendientes + " pendientes)");
        }

        // Modo "asignar maestros desde el stock": al final, para no interferir con las fases normales.
        if (asignarMaestros) {
            c.asignarMaestros(registro);
        }

        // COMPACTACIÓN FINAL: la cobertura queda CONGELADA (no se quita ni se añade ninguna sesión,
        // solo se reordenan dentro de su día) y se optimiza únicamente la forma. Es lo que evita que
        // "abrir disponibilidad" deje huecos o arranques tardíos: el motor ya no puede cambiar horas
        // por forma, así que cierra la semana compacta pase lo que pase.
        c.compactar(registro);

        // SEPARAR MATERIAS DEL MISMO MAESTRO EN EL GRUPO: con la cobertura congelada y la adyacencia
        // puntuando altísimo, se reempaquetan los días donde un maestro tiene dos materias pegadas.
        // Es la regla que el usuario pidió explícita: deben quedar separadas.
        c.separarAdyacencias(registro);

        IntentoIA intento = c.aIntento(numero);
        intento.setBitacora(bitacora);
        intento.setAsesor(asesor != null ? asesor.nombre() : "heuristica");
        intento.setMilisegundos(System.currentTimeMillis() - inicio);
        registro.accept("Intento " + numero + ": " + intento.getHoras() + "/" + intento.getHorasDemandadas()
                + " h · medium " + intento.getMedium() + " · pendientes " + intento.getPendientes().size()
                + " · problemas " + intento.getProblemas().size());
        return intento;
    }

    /** Ventana: posición inicial en el día y bloques que ocuparía. */
    private record Ventana(int pos, List<Long> ids) {
    }

    /**
     * Duraciones de las sesiones de una asignación según su patrón ("2,2,1" = dos sesiones de 2 h y
     * una de 1 h). Si el patrón no cuadra con las horas, se reparte de una en una.
     */
    public static List<Integer> duracionesDe(Asignacion a) {
        int horas = a.getHoras() == null ? 0 : a.getHoras();
        List<Integer> partes = new ArrayList<>();
        String patron = a.getDistribucion() == null ? "" : a.getDistribucion().trim();
        if (patron.matches("\\d+(\\s*,\\s*\\d+)*")) {
            int suma = 0;
            for (String trozo : patron.split(",")) {
                int k = Integer.parseInt(trozo.trim());
                if (k > 0) {
                    partes.add(k);
                    suma += k;
                }
            }
            if (suma != horas) {
                partes.clear();
            }
        }
        if (partes.isEmpty()) {
            for (int i = 0; i < horas; i++) {
                partes.add(1);
            }
        }
        return partes;
    }

    /** Destino: día + ventana. */
    private record Destino(int dia, Ventana ventana) {
    }

    /** Una sesión (trozo del patrón de una asignatura). */
    private static final class Sesion {
        final Asignacion asig;
        final long asigId;
        final int dur;
        final long gid;
        /** Maestro asignado. MUTABLE en el modo "stock": el motor puede cambiarlo por otro del pool. */
        long mid;
        /** Aula asignada. MUTABLE en el modo stock: el motor puede elegir otro taller de la materia. */
        long aid;
        /** Maestros elegibles (stock de la materia: los que ya la imparten en las asignaciones). */
        final Set<Long> stock;
        /** Talleres elegibles (aulas que ya usa esa materia en las asignaciones). */
        final Set<Long> stockAulas;
        /** true si es la clase de Jóvenes (regla especial de asignación de maestro). */
        final boolean jovenes;
        Integer dia;
        int pos = -1;
        List<Long> ids = List.of();

        Sesion(Asignacion a, int dur, Set<Long> stock, Set<Long> stockAulas, boolean jovenes) {
            this.asig = a;
            this.asigId = a.getAsignacionId();
            this.dur = dur;
            this.gid = a.getGrupo().getGrupoId();
            this.mid = a.getMaestro() != null ? a.getMaestro().getMaestroId() : 0L;
            this.aid = a.getAula() != null ? a.getAula().getAulaId() : 0L;
            this.stock = stock;
            this.stockAulas = stockAulas;
            this.jovenes = jovenes;
        }

        boolean colocada() {
            return dia != null;
        }
    }

    /** Estado completo de una corrida. */
    private static final class Corrida {

        final List<TurnoHorario> bloques;
        final List<Grupo> grupos;
        final List<Asignacion> asignaciones;
        final Map<Long, Set<Long>> dispG = new HashMap<>();
        final Map<Long, Set<Long>> dispM = new HashMap<>();

        /**
         * STOCK DE MAESTROS POR MATERIA (sin tablas nuevas): los maestros distintos que ya imparten
         * la materia en las asignaciones del semestre. En el modo "asignar maestros", el motor puede
         * elegir a cualquiera de ellos para cualquier grupo de esa materia.
         */
        final Map<Long, Set<Long>> stockPorMateria = new HashMap<>();

        /** STOCK DE TALLERES POR MATERIA: las aulas que ya usa la materia en las asignaciones. */
        final Map<Long, Set<Long>> stockAulasPorMateria = new HashMap<>();

        final List<Integer> dias = new ArrayList<>();
        /**
         * Bloques de clase por TURNO y día, ordenados.
         *
         * <p>Es por turno y no solo por día a propósito: el campo {@code orden} se repite en cada
         * turno (1..n), así que si se mezclan los turnos en una sola lista el orden queda entrelazado
         * (orden 1 del matutino, orden 1 del vespertino, orden 2 del matutino...) y dos posiciones
         * consecutivas son de turnos distintos: la contigüidad nunca se cumpliría y ninguna sesión de
         * 2 o más horas encontraría ventana. Ese fue exactamente el fallo medido (28 asignaturas
         * "imposibles" con 19 bloques coincidentes).
         */
        final Map<Long, Map<Integer, List<TurnoHorario>>> porTurnoDia = new HashMap<>();
        final Map<Long, Long> turnoDeGrupo = new HashMap<>();
        /** Ventanas legales por "turno|día|duración". */
        final Map<String, List<Ventana>> ventanas = new HashMap<>();

        final List<Sesion> sesiones = new ArrayList<>();
        final Map<Long, Sesion> ocupG = new HashMap<>();
        final Map<Long, Sesion> ocupM = new HashMap<>();
        final Map<Long, Sesion> ocupA = new HashMap<>();
        final Map<Long, Sesion> asigDia = new HashMap<>();

        final Random rnd;
        final int demanda;
        /** Momento (epoch ms) en que el intento debe parar; 0 = sin tope. */
        final long limite;
        /** Traza de las aperturas de ventana (solo se rellena durante la reconstrucción). */
        Consumer<String> traza = mensaje -> { };
        /** Últimas líneas de traza: se volcan a la bitácora solo si una apertura deja problemas. */
        final List<String> trazaReciente = new ArrayList<>();
        /** Peso de la adyacencia en el coste; se sube a 1000 solo en la fase de separación final. */
        int pesoAdyacencia = ReglasIA.PESO_ADYACENCIA;
        int score;

        Corrida(DatosIA datos, long semilla, long limite) {
            this.bloques = datos.bloques();
            this.grupos = datos.grupos();
            this.asignaciones = datos.asignaciones();
            this.rnd = new Random(semilla);
            this.limite = limite;

            for (Grupo g : grupos) {
                if (g.getTurno() != null && g.getTurno().getTurnoId() != null) {
                    turnoDeGrupo.put(g.getGrupoId(), g.getTurno().getTurnoId());
                }
            }
            for (TurnoHorario b : bloques) {
                if (Boolean.TRUE.equals(b.getDescanso()) || b.getDiaSemana() == null
                        || b.getTurno() == null || b.getTurno().getTurnoId() == null) {
                    continue;
                }
                porTurnoDia.computeIfAbsent(b.getTurno().getTurnoId(), k -> new HashMap<>())
                        .computeIfAbsent(b.getDiaSemana(), k -> new ArrayList<>()).add(b);
            }
            for (Map<Integer, List<TurnoHorario>> porDia : porTurnoDia.values()) {
                porDia.values().forEach(l -> l.sort(Comparator.comparing(TurnoHorario::getOrden)));
                dias.addAll(porDia.keySet());
            }
            dias.sort(Integer::compareTo);

            for (Map.Entry<Long, Map<Integer, List<TurnoHorario>>> porTurno : porTurnoDia.entrySet()) {
                for (Map.Entry<Integer, List<TurnoHorario>> porDia : porTurno.getValue().entrySet()) {
                    int d = porDia.getKey();
                    List<TurnoHorario> arr = porDia.getValue();
                    for (int dur = 1; dur <= arr.size(); dur++) {
                        List<Ventana> lista = new ArrayList<>();
                        for (int p = 0; p + dur - 1 < arr.size(); p++) {
                            boolean ok = true;
                            for (int x = p; x < p + dur - 1; x++) {
                                if (minutos(arr.get(x + 1).getHoraInicio()) - minutos(arr.get(x).getHoraFin())
                                        > ReglasIA.TOLERANCIA_CONTIGUIDAD_MIN) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) {
                                List<Long> ids = new ArrayList<>();
                                for (int x = p; x < p + dur; x++) {
                                    ids.add(arr.get(x).getId());
                                }
                                lista.add(new Ventana(p, List.copyOf(ids)));
                            }
                        }
                        ventanas.put(porTurno.getKey() + "|" + d + "|" + dur, lista);
                    }
                }
            }

            // Stock de maestros por materia: los maestros distintos que ya la imparten.
            for (Asignacion a : asignaciones) {
                if (a.getMateria() == null || a.getMaestro() == null) {
                    continue;
                }
                stockPorMateria.computeIfAbsent(a.getMateria().getMateriaId(), k -> new LinkedHashSet<>())
                        .add(a.getMaestro().getMaestroId());
            }
            // Stock de talleres por materia: las aulas distintas que ya usa.
            for (Asignacion a : asignaciones) {
                if (a.getMateria() == null || a.getAula() == null) {
                    continue;
                }
                stockAulasPorMateria.computeIfAbsent(a.getMateria().getMateriaId(), k -> new LinkedHashSet<>())
                        .add(a.getAula().getAulaId());
            }

            Set<Long> idsTurno = new HashSet<>();
            for (TurnoHorario b : bloques) {
                idsTurno.add(b.getId());
            }
            for (DisponibilidadGrupo dg : datos.disponibilidadGrupo()) {
                if (Boolean.FALSE.equals(dg.getDisponible()) || dg.getTurnoHorario() == null) {
                    continue;
                }
                dispG.computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                        .add(dg.getTurnoHorario().getId());
            }
            for (DisponibilidadMaestro dm : datos.disponibilidadMaestro()) {
                if (Boolean.FALSE.equals(dm.getDisponible()) || dm.getTurnoHorario() == null
                        || !idsTurno.contains(dm.getTurnoHorario().getId())) {
                    continue;
                }
                dispM.computeIfAbsent(dm.getMaestro().getMaestroId(), k -> new HashSet<>())
                        .add(dm.getTurnoHorario().getId());
            }

            int total = 0;
            for (Asignacion a : asignaciones) {
                if (a.getGrupo() == null) {
                    continue;
                }
                Set<Long> stock = a.getMateria() != null
                        ? stockPorMateria.getOrDefault(a.getMateria().getMateriaId(), Set.of())
                        : Set.of();
                Set<Long> stockAulas = a.getMateria() != null
                        ? stockAulasPorMateria.getOrDefault(a.getMateria().getMateriaId(), Set.of())
                        : Set.of();
                boolean jovenes = a.getMateria() != null && a.getMateria().getClave() != null
                        && a.getMateria().getClave().contains("óvenes");
                for (int dur : duracionesDe(a)) {
                    sesiones.add(new Sesion(a, dur, stock, stockAulas, jovenes));
                    total += dur;
                }
            }
            this.demanda = total;
        }

        static long minutos(java.time.LocalTime h) {
            return h == null ? -1 : h.getHour() * 60L + h.getMinute();
        }

        static long clave(long id, long bloqueId) {
            return id * 1_000_000L + bloqueId;
        }

        static long claveDia(long asignacionId, int dia) {
            return asignacionId * 10L + dia;
        }

        /** Turno del grupo (0 si no se pudo resolver: entonces no tendrá bloques ni ventanas). */
        long turnoDe(long grupoId) {
            return turnoDeGrupo.getOrDefault(grupoId, 0L);
        }

        /** Bloques de clase de ese grupo en ese día (los de SU turno). */
        List<TurnoHorario> dia(long grupoId, int dia) {
            return porTurnoDia.getOrDefault(turnoDe(grupoId), Map.of())
                    .getOrDefault(dia, List.of());
        }

        /** Ventanas legales de una sesión en un día concreto, dentro del turno de su grupo. */
        List<Ventana> ventanasDe(Sesion s, int dia) {
            return ventanas.getOrDefault(turnoDe(s.gid) + "|" + dia + "|" + s.dur, List.of());
        }

        // ───────── factibilidad y coste ─────────

        /** Destinos posibles ahora mismo (día + ventana) respetando disponibilidad y ocupación. */
        List<Destino> candidatas(Sesion s) {
            List<Destino> out = new ArrayList<>();
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return out;
            }
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)
                                || ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))
                                || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        out.add(new Destino(d, v));
                    }
                }
            }
            return out;
        }

        int costeDia(long gid, int dia) {
            List<TurnoHorario> arr = dia(gid, dia);
            if (arr.isEmpty()) {
                return 0;
            }
            int n = arr.size();
            Sesion[] occ = new Sesion[n];
            int primera = -1;
            int ultima = -1;
            for (int p = 0; p < n; p++) {
                occ[p] = ocupG.get(clave(gid, arr.get(p).getId()));
                if (occ[p] != null) {
                    if (primera < 0) {
                        primera = p;
                    }
                    ultima = p;
                }
            }
            if (primera < 0) {
                return 0;
            }
            int c = primera != 0 ? ReglasIA.PESO_ARRANQUE : 0;
            int huecos = 0;
            for (int p = primera; p < ultima; p++) {
                if (occ[p] == null) {
                    huecos++;
                }
            }
            c += Math.min(ReglasIA.PESO_HUECO * huecos, ReglasIA.TOPE_HUECOS_DIA);
            for (int p = 0; p + 1 < n; p++) {
                if (minutos(arr.get(p + 1).getHoraInicio()) - minutos(arr.get(p).getHoraFin())
                        > ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                    continue;
                }
                Sesion a = occ[p];
                Sesion b = occ[p + 1];
                if (a != null && b != null && a.asigId != b.asigId && a.mid == b.mid) {
                    c += pesoAdyacencia;
                }
            }
            return c;
        }

        void colocar(Sesion s, int dia, Ventana v) {
            s.dia = dia;
            s.pos = v.pos();
            s.ids = v.ids();
            for (long bid : v.ids()) {
                ocupG.put(clave(s.gid, bid), s);
                ocupM.put(clave(s.mid, bid), s);
                if (s.aid != 0) {
                    ocupA.put(clave(s.aid, bid), s);
                }
            }
            asigDia.put(claveDia(s.asigId, dia), s);
        }

        /** Quita la sesión y devuelve su colocación anterior (null si no estaba colocada). */
        int[] quitar(Sesion s) {
            if (!s.colocada()) {
                return null;
            }
            int[] antes = {s.dia, s.pos};
            for (long bid : s.ids) {
                ocupG.remove(clave(s.gid, bid));
                ocupM.remove(clave(s.mid, bid));
                if (s.aid != 0) {
                    ocupA.remove(clave(s.aid, bid));
                }
            }
            asigDia.remove(claveDia(s.asigId, s.dia));
            s.dia = null;
            s.pos = -1;
            s.ids = List.of();
            return antes;
        }

        void restaurar(Sesion s, int[] antes) {
            if (antes == null) {
                return;
            }
            colocar(s, antes[0], new Ventana(antes[1], bloquesDe(s, antes[0], antes[1])));
        }

        List<Long> bloquesDe(Sesion s, int dia, int pos) {
            List<TurnoHorario> arr = dia(s.gid, dia);
            List<Long> ids = new ArrayList<>();
            for (int x = pos; x < pos + s.dur; x++) {
                ids.add(arr.get(x).getId());
            }
            return List.copyOf(ids);
        }

        List<int[]> snapshot() {
            List<int[]> snap = new ArrayList<>(sesiones.size());
            for (Sesion s : sesiones) {
                snap.add(s.colocada() ? new int[]{s.dia, s.pos} : null);
            }
            return snap;
        }

        /**
         * Devuelve TODAS las sesiones al estado guardado en el snapshot.
         *
         * <p>Se quitan todas primero y se colocan después. Hacerlo de una en una (quitar y colocar la
         * misma) dejaba ocupados los bloques de las sesiones que aún no se habían restaurado, así que
         * la colocación restaurada de una podía caer encima de la actual de otra: de ahí salían los
         * "choque en el bloque N" y las materias con dos sesiones el mismo día que detectaba la
         * comprobación dura.
         */
        void restaurarTodo(List<int[]> snap) {
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    quitar(s);
                }
            }
            for (int i = 0; i < sesiones.size(); i++) {
                int[] w = snap.get(i);
                if (w != null) {
                    Sesion s = sesiones.get(i);
                    colocar(s, w[0], new Ventana(w[1], bloquesDe(s, w[0], w[1])));
                }
            }
        }

        /** Rehace los mapas desde el estado de las sesiones y devuelve las inconsistencias. */
        List<String> reconstruirMapas() {
            ocupG.clear();
            ocupM.clear();
            ocupA.clear();
            asigDia.clear();
            Set<String> avisos = new LinkedHashSet<>();
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    continue;
                }
                Set<Long> g = dispG.get(s.gid);
                Set<Long> m = dispM.get(s.mid);
                for (long bid : s.ids) {
                    // Reglas duras de disponibilidad: el bloque debe estar en las dos.
                    if (g == null || !g.contains(bid)) {
                        avisos.add("el bloque " + bid + " no está disponible para el grupo de la "
                                + "asignación " + s.asigId);
                    }
                    if (m == null || !m.contains(bid)) {
                        avisos.add("el bloque " + bid + " no está disponible para el maestro de la "
                                + "asignación " + s.asigId);
                    }
                    if (ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))
                            || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                        avisos.add("choque en el bloque " + bid + " (asignación " + s.asigId + ")");
                    }
                    ocupG.put(clave(s.gid, bid), s);
                    ocupM.put(clave(s.mid, bid), s);
                    if (s.aid != 0) {
                        ocupA.put(clave(s.aid, bid), s);
                    }
                }
                // Sesión entera: los bloques tienen que ser contiguos dentro del día.
                List<TurnoHorario> arr = dia(s.gid, s.dia);
                if (arr.isEmpty() || s.pos < 0 || s.pos + s.ids.size() > arr.size()) {
                    avisos.add("la asignación " + s.asigId + " está en un día sin bloques (" + s.dia + ")");
                } else {
                    for (int x = 0; x + 1 < s.ids.size(); x++) {
                        if (minutos(arr.get(s.pos + x + 1).getHoraInicio())
                                - minutos(arr.get(s.pos + x).getHoraFin())
                                > ReglasIA.TOLERANCIA_CONTIGUIDAD_MIN) {
                            avisos.add("la sesión de la asignación " + s.asigId
                                    + " no es contigua el día " + s.dia);
                            break;
                        }
                    }
                }
                if (asigDia.containsKey(claveDia(s.asigId, s.dia))) {
                    avisos.add("la materia " + s.asigId + " tiene dos sesiones el día " + s.dia);
                }
                asigDia.put(claveDia(s.asigId, s.dia), s);
            }
            return new ArrayList<>(avisos);
        }

        /** Detalle de los choques actuales: quién ocupa cada bloque compartido. */
        List<String> choquesDetallados() {
            Map<String, List<String>> porGrupo = new LinkedHashMap<>();
            Map<String, List<String>> porMaestro = new LinkedHashMap<>();
            Map<String, List<String>> porAula = new LinkedHashMap<>();
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    continue;
                }
                String quien = "asig" + s.asigId + "(" + s.dur + "h)@" + s.dia + "/" + s.pos;
                for (long bid : s.ids) {
                    porGrupo.computeIfAbsent(s.gid + "|" + bid, k -> new ArrayList<>()).add(quien);
                    porMaestro.computeIfAbsent(s.mid + "|" + bid, k -> new ArrayList<>()).add(quien);
                    if (s.aid != 0) {
                        porAula.computeIfAbsent(s.aid + "|" + bid, k -> new ArrayList<>()).add(quien);
                    }
                }
            }
            List<String> out = new ArrayList<>();
            porGrupo.forEach((k, v) -> {
                if (v.size() > 1) {
                    out.add("grupo " + k + " → " + String.join(" y ", v));
                }
            });
            porMaestro.forEach((k, v) -> {
                if (v.size() > 1) {
                    out.add("maestro " + k + " → " + String.join(" y ", v));
                }
            });
            porAula.forEach((k, v) -> {
                if (v.size() > 1) {
                    out.add("aula " + k + " → " + String.join(" y ", v));
                }
            });
            return out;
        }

        int ganancia(Sesion s) {
            return ReglasIA.PESO_HORA * s.dur + (s.dur >= 2 ? ReglasIA.PESO_SESION_LARGA : 0);
        }

        /**
         * ¿Se agotó el tiempo del intento? Solo se consulta en los bucles externos (pasos de la
         * búsqueda local y rondas de reempaquetado) y en fronteras de grupo/maestro, nunca en medio
         * de un reempaquetado: así el corte deja siempre un estado consistente y publicable.
         */
        boolean agotado() {
            return limite > 0 && System.currentTimeMillis() > limite;
        }

        int puntuar() {
            reconstruirMapas();
            int total = 0;
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    total += ganancia(s);
                } else if (s.dur >= 2) {
                    total -= ReglasIA.PESO_SESION_LARGA;
                }
            }
            for (Grupo g : grupos) {
                for (int d : dias) {
                    total -= costeDia(g.getGrupoId(), d);
                }
            }
            return total;
        }

        /**
         * Mejor destino para una sesión SIN colocarla (no toca el estado salvo el temporal de la
         * medición, que se deshace): mayor ganancia neta respecto al estado actual.
         */
        Destino mejorDestino(Sesion s, List<Long> excluir) {
            Map<Integer, Integer> antes = new HashMap<>();
            for (int d : dias) {
                antes.put(d, costeDia(s.gid, d));
            }
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return null;
            }
            Destino mejor = null;
            int mejorGan = Integer.MIN_VALUE;
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    if (!excluir.isEmpty() && v.ids().stream().anyMatch(excluir::contains)) {
                        continue;
                    }
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)
                                || ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))
                                || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    colocar(s, d, v);
                    int despues = costeDia(s.gid, d);
                    quitar(s);                                  // se deja SIN colocar
                    int gan = ganancia(s) - (despues - antes.get(d));
                    if (gan > mejorGan) {
                        mejorGan = gan;
                        mejor = new Destino(d, v);
                    }
                }
            }
            return mejor;
        }

        // ───────── 1) construcción fail-first ─────────
        void construir(AsesorIA asesor, Consumer<String> log) {
            Map<Long, Integer> nVentanas = new HashMap<>();
            for (Sesion s : sesiones) {
                Set<Long> g = dispG.get(s.gid);
                Set<Long> m = dispM.get(s.mid);
                int n = 0;
                if (g != null && m != null) {
                    for (int d : dias) {
                        for (Ventana v : ventanasDe(s, d)) {
                            boolean ok = true;
                            for (long bid : v.ids()) {
                                if (!g.contains(bid) || !m.contains(bid)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) {
                                n++;
                            }
                        }
                    }
                }
                nVentanas.merge(s.asigId, n, Integer::sum);
            }

            Map<Long, Integer> prioridad = new HashMap<>();
            if (asesor != null) {
                List<Long> sugerido = asesor.ordenSugerido(asignaciones, nVentanas);
                for (int i = 0; i < sugerido.size(); i++) {
                    // putIfAbsent: si el asesor repite un id, manda la primera aparición.
                    prioridad.putIfAbsent(sugerido.get(i), i);
                }
                if (!prioridad.isEmpty()) {
                    log.accept("  asesor " + asesor.nombre() + ": orden completo propuesto para "
                            + prioridad.size() + " de " + asignaciones.size() + " materias");
                }
            }

            List<Sesion> orden = new ArrayList<>(sesiones);
            Comparator<Sesion> failFirst = Comparator
                    .comparingInt((Sesion s) -> nVentanas.getOrDefault(s.asigId, 0))
                    .thenComparingInt(s -> -s.dur)
                    .thenComparingLong(s -> s.asigId);

            int colocadas;
            if (prioridad.isEmpty()) {
                orden.sort(failFirst);
                colocadas = construirEnOrden(orden);
            } else {
                // El asesor propone el ORDEN COMPLETO y su criterio manda, PERO se compara contra el
                // fail-first de siempre: el punto de partida es el que más horas coloque. Así un mal
                // orden del LLM no puede dejar el tablero peor que la heurística (fue lo que pasó: un
                // orden malo dejaba muchas horas fuera, entre ellas las sesiones largas).
                List<Sesion> conAsesor = new ArrayList<>(sesiones);
                conAsesor.sort(Comparator
                        .comparingInt((Sesion s) -> prioridad.getOrDefault(s.asigId, Integer.MAX_VALUE))
                        .thenComparingInt(s -> -s.dur)
                        .thenComparingLong(s -> s.asigId));
                int delAsesor = construirEnOrden(conAsesor);
                List<int[]> hechoAsesor = snapshot();
                for (Sesion s : sesiones) {
                    if (s.colocada()) {
                        quitar(s);
                    }
                }
                List<Sesion> delMotor = new ArrayList<>(sesiones);
                delMotor.sort(failFirst);
                int delFailFirst = construirEnOrden(delMotor);
                if (delFailFirst > delAsesor) {
                    colocadas = delFailFirst;
                    log.accept("  asesor " + asesor.nombre() + ": su orden coloca " + delAsesor
                            + " sesiones y el fail-first " + delFailFirst + " → se usa el fail-first");
                } else {
                    restaurarTodo(hechoAsesor);
                    colocadas = delAsesor;
                    log.accept("  asesor " + asesor.nombre() + ": su orden coloca " + delAsesor
                            + " sesiones (fail-first " + delFailFirst + ") → se usa el del asesor");
                }
            }
            score = puntuar();
            log.accept("  construcción: " + colocadas + "/" + sesiones.size() + " sesiones · score " + score);
        }

        /** Coloca las sesiones en el orden dado (sin quitar nada antes) y devuelve cuántas cupieron. */
        private int construirEnOrden(List<Sesion> orden) {
            int colocadas = 0;
            for (Sesion s : orden) {
                Destino d = mejorDestino(s, List.of());
                if (d != null) {
                    colocar(s, d.dia(), d.ventana());
                    colocadas++;
                }
            }
            return colocadas;
        }

        // ───────── 2) búsqueda local ─────────
        void buscarLocal(int pasos) {
            buscarLocal(pasos, sesiones);
        }

        /**
         * Búsqueda local restringida a un subconjunto: las demás sesiones no se tocan. La usa el
         * reacomodo de grupos completos, que trabaja sobre los grupos implicados y deja fijo el resto
         * del horario.
         */
        void buscarLocal(int pasos, List<Sesion> universo) {
            if (universo.isEmpty()) {
                return;
            }
            for (int paso = 0; paso < pasos; paso++) {
                if ((paso & 255) == 0 && agotado()) {
                    break;
                }
                Sesion s = universo.get(rnd.nextInt(universo.size()));

                if (!s.colocada()) {                            // colocar una pendiente
                    Destino d = mejorDestino(s, List.of());
                    if (d == null) {
                        continue;
                    }
                    int antes = costeDia(s.gid, d.dia());
                    colocar(s, d.dia(), d.ventana());
                    int delta = ganancia(s) - (costeDia(s.gid, d.dia()) - antes);
                    if (!(delta > 0 || (delta == 0 && rnd.nextBoolean()))) {
                        quitar(s);
                    }
                    continue;
                }

                if (rnd.nextInt(100) < 8) {                     // dejarla pendiente
                    int dia = s.dia;
                    int antes = costeDia(s.gid, dia);
                    int[] orig = quitar(s);
                    int delta = -ganancia(s) + (costeDia(s.gid, dia) - antes);
                    if (!(delta > 0 || (delta == 0 && rnd.nextBoolean()))) {
                        restaurar(s, orig);
                    }
                    continue;
                }

                int diaOrig = s.dia;
                int posOrig = s.pos;
                int antesA = costeDia(s.gid, diaOrig);
                int[] orig = quitar(s);
                List<Destino> cands = new ArrayList<>();
                for (Destino d : candidatas(s)) {
                    if (!(d.dia() == diaOrig && d.ventana().pos() == posOrig)) {
                        cands.add(d);
                    }
                }
                if (cands.isEmpty()) {
                    restaurar(s, orig);
                    continue;
                }
                Destino d = cands.get(rnd.nextInt(cands.size()));
                int antesB = costeDia(s.gid, d.dia());
                colocar(s, d.dia(), d.ventana());
                int delta = ganancia(s)
                        - ((costeDia(s.gid, d.dia()) - antesB) + (costeDia(s.gid, diaOrig) - antesA));
                if (!(delta > 0 || (delta == 0 && rnd.nextBoolean()))) {
                    quitar(s);
                    restaurar(s, orig);
                }
            }
            score = puntuar();
        }

        // ───────── 3) reempaquetado ─────────
        void cicloReempaquetado(int pasos, Consumer<String> log) {
            Set<Long> maestros = new LinkedHashSet<>();
            for (Sesion s : sesiones) {
                maestros.add(s.mid);
            }
            for (int ronda = 1; ronda <= 18; ronda++) {
                int g = 0;
                for (Grupo gr : grupos) {
                    if (agotado()) {
                        break;
                    }
                    for (int d : dias) {
                        if (reempaquetarDia(gr.getGrupoId(), d)) {
                            g++;
                        }
                    }
                }
                int m = 0;
                for (long mid : maestros) {
                    if (agotado()) {
                        break;
                    }
                    for (int d : dias) {
                        if (reempaquetarMaestroDia(mid, d)) {
                            m++;
                        }
                    }
                }
                score = puntuar();
                log.accept("  ronda " + ronda + ": " + g + " grupo-día + " + m + " maestro-día · score " + score);
                if (g == 0 && m == 0) {
                    break;
                }
                buscarLocal(Math.max(1000, pasos / 4));
            }
        }

        /** Rehace un grupo-día desde la primera hora (búsqueda en profundidad por posiciones). */
        boolean reempaquetarDia(long gid, int dia) {
            List<TurnoHorario> arr = dia(gid, dia);
            if (arr.isEmpty()) {
                return false;
            }
            List<Sesion> suyas = new ArrayList<>();
            for (TurnoHorario b : arr) {
                Sesion s = ocupG.get(clave(gid, b.getId()));
                if (s != null && !suyas.contains(s)) {
                    suyas.add(s);
                }
            }
            if (suyas.isEmpty()) {
                return false;
            }
            int antes = costeDia(gid, dia);
            List<int[]> orig = new ArrayList<>();
            for (Sesion s : suyas) {
                orig.add(s.colocada() ? new int[]{s.dia, s.pos} : null);
            }
            for (Sesion s : suyas) {
                quitar(s);
            }

            Map<Sesion, List<Ventana>> opciones = new LinkedHashMap<>();
            for (Sesion s : suyas) {
                List<Ventana> libres = new ArrayList<>();
                Set<Long> m = dispM.get(s.mid);
                Set<Long> gs = dispG.get(s.gid);
                for (Ventana v : ventanasDe(s, dia)) {
                    boolean ok = m != null && gs != null;
                    if (ok) {
                        for (long bid : v.ids()) {
                            if (!m.contains(bid) || !gs.contains(bid) || ocupM.containsKey(clave(s.mid, bid))
                                    || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    if (ok) {
                        libres.add(v);
                    }
                }
                opciones.put(s, libres);
            }

            Map<Sesion, Ventana> mejor = new LinkedHashMap<>();
            int[] mejorCoste = {Integer.MAX_VALUE};
            explorar(0, suyas, opciones, new boolean[suyas.size()], new ArrayList<>(), arr, gid, dia,
                    mejor, mejorCoste);

            if (!mejor.isEmpty() && mejorCoste[0] < antes) {
                for (Map.Entry<Sesion, Ventana> e : mejor.entrySet()) {
                    colocar(e.getKey(), dia, e.getValue());
                }
                return true;
            }
            for (Sesion s : suyas) {
                quitar(s);
            }
            for (int i = 0; i < suyas.size(); i++) {
                restaurar(suyas.get(i), orig.get(i));
            }
            return false;
        }

        private void explorar(int p, List<Sesion> suyas, Map<Sesion, List<Ventana>> opciones,
                              boolean[] usada, List<Sesion> asignadas, List<TurnoHorario> arr,
                              long gid, int dia, Map<Sesion, Ventana> mejor, int[] mejorCoste) {
            if (p >= arr.size()) {
                for (boolean u : usada) {
                    if (!u) {
                        return;                                   // todas han de quedar colocadas
                    }
                }
                int c = costeDia(gid, dia);
                if (c < mejorCoste[0]) {
                    mejorCoste[0] = c;
                    mejor.clear();
                    for (Sesion s : asignadas) {
                        mejor.put(s, new Ventana(s.pos, s.ids));
                    }
                }
                return;
            }
            if (costeDia(gid, dia) >= mejorCoste[0]) {
                return;
            }
            boolean quedan = false;
            for (boolean u : usada) {
                if (!u) {
                    quedan = true;
                    break;
                }
            }
            if (quedan) {
                explorar(p + 1, suyas, opciones, usada, asignadas, arr, gid, dia, mejor, mejorCoste);
            }
            for (int i = 0; i < suyas.size(); i++) {
                if (usada[i]) {
                    continue;
                }
                Sesion s = suyas.get(i);
                Ventana v = null;
                for (Ventana cand : opciones.getOrDefault(s, List.of())) {
                    if (cand.pos() == p) {
                        v = cand;
                        break;
                    }
                }
                if (v == null) {
                    continue;
                }
                boolean choca = false;
                for (long bid : v.ids()) {
                    if (ocupG.containsKey(clave(gid, bid))) {
                        choca = true;
                        break;
                    }
                }
                if (choca) {
                    continue;
                }
                usada[i] = true;
                colocar(s, dia, v);
                asignadas.add(s);
                explorar(p + s.dur, suyas, opciones, usada, asignadas, arr, gid, dia, mejor, mejorCoste);
                asignadas.remove(asignadas.size() - 1);
                quitar(s);
                usada[i] = false;
            }
        }

        /** Reparte las sesiones de un maestro ese día entre sus grupos (sin solaparse). */
        boolean reempaquetarMaestroDia(long mid, int dia) {
            List<Sesion> suyas = new ArrayList<>();
            for (Sesion s : sesiones) {
                if (s.mid == mid && s.dia != null && s.dia == dia) {
                    suyas.add(s);
                }
            }
            if (suyas.size() < 2) {
                return false;
            }
            Set<Long> afectados = new LinkedHashSet<>();
            for (Sesion s : suyas) {
                afectados.add(s.gid);
            }
            int antes = 0;
            for (long g : afectados) {
                antes += costeDia(g, dia);
            }
            List<int[]> orig = new ArrayList<>();
            for (Sesion s : suyas) {
                orig.add(s.colocada() ? new int[]{s.dia, s.pos} : null);
            }
            for (Sesion s : suyas) {
                quitar(s);
            }

            List<List<Ventana>> opciones = new ArrayList<>();
            for (Sesion s : suyas) {
                List<Ventana> libres = new ArrayList<>();
                Set<Long> g = dispG.get(s.gid);
                Set<Long> m = dispM.get(s.mid);
                for (Ventana v : ventanasDe(s, dia)) {
                    boolean ok = g != null && m != null;
                    if (ok) {
                        for (long bid : v.ids()) {
                            // Al mover una sesión DENTRO del mismo día hay que volver a comprobar las
                            // dos disponibilidades: el maestro puede tener huecos a media jornada, así
                            // que su bloque de antes no sirve como prueba para el de ahora.
                            if (!g.contains(bid) || !m.contains(bid)
                                    || ocupG.containsKey(clave(s.gid, bid))
                                    || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    if (ok) {
                        libres.add(v);
                    }
                }
                opciones.add(libres);
            }

            List<Ventana> mejor = new ArrayList<>();
            int[] mejorCoste = {Integer.MAX_VALUE};
            explorarMaestro(0, suyas, opciones, new ArrayList<>(), afectados, dia, mejor, mejorCoste);

            if (mejor.size() == suyas.size() && mejorCoste[0] < antes) {
                for (int i = 0; i < suyas.size(); i++) {
                    colocar(suyas.get(i), dia, mejor.get(i));
                }
                return true;
            }
            for (Sesion s : suyas) {
                quitar(s);
            }
            for (int i = 0; i < suyas.size(); i++) {
                restaurar(suyas.get(i), orig.get(i));
            }
            return false;
        }

        private void explorarMaestro(int i, List<Sesion> suyas, List<List<Ventana>> opciones,
                                     List<Ventana> actual, Set<Long> afectados, int dia,
                                     List<Ventana> mejor, int[] mejorCoste) {
            if (i == suyas.size()) {
                int c = 0;
                for (long g : afectados) {
                    c += costeDia(g, dia);
                }
                if (c < mejorCoste[0]) {
                    mejorCoste[0] = c;
                    mejor.clear();
                    mejor.addAll(actual);
                }
                return;
            }
            Sesion s = suyas.get(i);
            for (Ventana v : opciones.get(i)) {
                boolean choca = false;
                for (Ventana w : actual) {
                    for (long bid : v.ids()) {
                        if (w.ids().contains(bid)) {
                            choca = true;
                            break;
                        }
                    }
                    if (choca) {
                        break;
                    }
                }
                if (choca) {
                    continue;
                }
                actual.add(v);
                colocar(s, dia, v);
                explorarMaestro(i + 1, suyas, opciones, actual, afectados, dia, mejor, mejorCoste);
                quitar(s);
                actual.remove(actual.size() - 1);
            }
        }

        // ───────── 4) reconstrucción y deduplicación ─────────
        void reconstruirTodo(Consumer<String> log) {
            for (int vuelta = 1; vuelta <= 4; vuelta++) {
                int dup = deduplicar();
                int puestas = 0;
                for (Sesion s : sesiones) {
                    if (!s.colocada()) {
                        Destino d = mejorDestino(s, List.of());
                        if (d != null) {
                            colocar(s, d.dia(), d.ventana());
                            puestas++;
                        }
                    }
                }
                int abiertas = 0;
                for (Sesion s : sesiones) {
                    if (!s.colocada()) {
                        trazaReciente.clear();
                        traza = mensaje -> {
                            if (trazaReciente.size() < 200) {
                                trazaReciente.add(mensaje);
                            }
                        };
                        boolean abrio = abrirVentana(s, 3, List.of());
                        traza = mensaje -> { };
                        if (abrio) {
                            abiertas++;
                            int problemas = reconstruirMapas().size();
                            log.accept("   apertura para la asignación " + s.asigId + ": problemas duros "
                                    + problemas + " · " + choquesDetallados().size() + " choque(s)");
                            if (problemas > 0) {
                                // El detalle solo interesa cuando algo ha quedado mal.
                                trazaReciente.forEach(log);
                            }
                        }
                    }
                }
                int rec = reconstruirIncompletas();
                score = puntuar();
                log.accept("  reconstrucción " + vuelta + ": duplicados " + dup + " · colocadas " + puestas
                        + " · ventanas abiertas " + abiertas + " · materias completadas " + rec
                        + " · score " + score);
                if (dup + puestas + abiertas + rec == 0) {
                    break;
                }
            }
            reconstruirGrupos(log);
            // 4b) Si aún faltan horas y el culpable es el horario de OTROS grupos, se rehacen esos
            //     grupos completos (y después se repasa el grupo que se quedó corto).
            reacomodarGrupos(5, log);
            reconstruirGrupos(log);
            // 4c) Cierre con prioridad a las sesiones LARGAS: es mejor dejar pendiente una hora de
            //     1 h que una de 2 o más, porque la larga es la que más cuesta acomodar a mano.
            //     Va al final, para que ningún paso posterior deshaga el trueque.
            rescatarLargas(log);
        }

        /**
         * RESCATE FINAL DE SESIONES LARGAS.
         *
         * <p>Por cada pendiente de 2 o más horas se buscan sus ventanas legales; si la ocupa alguien
         * se intenta recolocarlo fuera y, si no cabe en ningún lado, se le SACRIFICA (queda pendiente)
         * para colocar la larga. El trueque solo se acepta si las horas colocadas no bajan y las
         * reglas duras siguen en cero; si no, se revierte todo.
         */
        void rescatarLargas(Consumer<String> log) {
            List<Sesion> largas = new ArrayList<>();
            for (Sesion s : sesiones) {
                if (!s.colocada() && s.dur >= 2) {
                    largas.add(s);
                }
            }
            largas.sort(Comparator.comparingInt((Sesion s) -> -s.dur)
                    .thenComparingInt(this::ventanasLegales)
                    .thenComparingLong(s -> s.asigId));

            for (Sesion s : largas) {
                if (agotado() || s.colocada()) {
                    continue;
                }
                boolean rescatada = false;
                for (int d : dias) {
                    if (rescatada) {
                        break;
                    }
                    if (asigDia.containsKey(claveDia(s.asigId, d))) {
                        continue;
                    }
                    for (Ventana v : ventanasDe(s, d)) {
                        if (rescatada) {
                            break;
                        }
                        if (!disponiblePorDisponibilidad(s, v)) {
                            continue;
                        }
                        List<int[]> snap = snapshot();
                        int antes = horasColocadas(sesiones);

                        Set<Sesion> ocupantes = new LinkedHashSet<>();
                        for (long bid : v.ids()) {
                            Sesion x = ocupG.get(clave(s.gid, bid));
                            if (x != null && x != s) {
                                ocupantes.add(x);
                            }
                            Sesion y = ocupM.get(clave(s.mid, bid));
                            if (y != null && y != s) {
                                ocupantes.add(y);
                            }
                            if (s.aid != 0) {
                                Sesion z = ocupA.get(clave(s.aid, bid));
                                if (z != null && z != s) {
                                    ocupantes.add(z);
                                }
                            }
                        }
                        for (Sesion x : ocupantes) {
                            quitar(x);
                        }
                        // Primero se intenta recolocar a los ocupantes fuera de esta ventana...
                        for (Sesion x : ocupantes) {
                            if (!x.colocada()) {
                                Destino destino = mejorDestino(x, v.ids());
                                if (destino != null) {
                                    colocar(x, destino.dia(), destino.ventana());
                                }
                            }
                        }
                        // ...y si la ventana quedó libre, entra la larga.
                        boolean hecho = false;
                        if (sigueSiendoValida(s, d, v)) {
                            colocar(s, d, v);
                            int despues = horasColocadas(sesiones);
                            if (despues >= antes && reconstruirMapas().isEmpty()) {
                                hecho = true;
                                rescatada = true;
                                log.accept("  rescate largo: asignación " + s.asigId + " (" + s.dur
                                        + "h) en día " + d + " pos " + v.pos() + " · horas " + antes
                                        + " → " + despues);
                            }
                        }
                        if (!hecho) {
                            restaurarTodo(snap);
                        }
                    }
                }
            }
        }

        int deduplicar() {
            Set<Long> g = new HashSet<>();
            Set<Long> m = new HashSet<>();
            Set<Long> a = new HashSet<>();
            Set<Long> ad = new HashSet<>();
            int quitadas = 0;
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    continue;
                }
                boolean choca = ad.contains(claveDia(s.asigId, s.dia));
                if (!choca) {
                    for (long bid : s.ids) {
                        if (g.contains(clave(s.gid, bid)) || m.contains(clave(s.mid, bid))
                                || (s.aid != 0 && a.contains(clave(s.aid, bid)))) {
                            choca = true;
                            break;
                        }
                    }
                }
                if (choca) {
                    quitar(s);
                    quitadas++;
                    continue;
                }
                ad.add(claveDia(s.asigId, s.dia));
                for (long bid : s.ids) {
                    g.add(clave(s.gid, bid));
                    m.add(clave(s.mid, bid));
                    if (s.aid != 0) {
                        a.add(clave(s.aid, bid));
                    }
                }
            }
            return quitadas;
        }

        List<List<Sesion>> materiasIncompletas() {
            Map<Long, List<Sesion>> porAsig = new LinkedHashMap<>();
            for (Sesion s : sesiones) {
                porAsig.computeIfAbsent(s.asigId, k -> new ArrayList<>()).add(s);
            }
            List<List<Sesion>> out = new ArrayList<>();
            for (List<Sesion> l : porAsig.values()) {
                for (Sesion s : l) {
                    if (!s.colocada()) {
                        out.add(l);
                        break;
                    }
                }
            }
            return out;
        }

        int reconstruirIncompletas() {
            int hechas = 0;
            for (List<Sesion> grupo : materiasIncompletas()) {
                List<int[]> orig = new ArrayList<>();
                for (Sesion s : grupo) {
                    orig.add(s.colocada() ? new int[]{s.dia, s.pos} : null);
                }
                for (Sesion s : grupo) {
                    quitar(s);
                }
                boolean ok = true;
                for (Sesion s : grupo) {
                    Destino d = mejorDestino(s, List.of());
                    if (d == null) {
                        ok = false;
                        break;
                    }
                    colocar(s, d.dia(), d.ventana());
                }
                boolean completa = true;
                for (Sesion s : grupo) {
                    if (!s.colocada()) {
                        completa = false;
                        break;
                    }
                }
                if (ok && completa) {
                    hechas++;
                } else {
                    for (Sesion s : grupo) {
                        quitar(s);
                    }
                    for (int i = 0; i < grupo.size(); i++) {
                        restaurar(grupo.get(i), orig.get(i));
                    }
                }
            }
            return hechas;
        }

        /** Rehace el horario de un grupo entero si le falta alguna hora (los demás no se tocan). */
        void reconstruirGrupos(Consumer<String> log) {
            for (Grupo g : grupos) {
                List<Sesion> suyas = new ArrayList<>();
                boolean falta = false;
                for (Sesion s : sesiones) {
                    if (s.gid == g.getGrupoId()) {
                        suyas.add(s);
                        if (!s.colocada()) {
                            falta = true;
                        }
                    }
                }
                if (!falta) {
                    continue;
                }
                List<int[]> orig = snapshot();
                int antes = 0;
                for (Sesion s : suyas) {
                    if (s.colocada()) {
                        antes += s.dur;
                    }
                }
                for (Sesion s : suyas) {
                    quitar(s);
                }
                suyas.sort(Comparator.comparingInt((Sesion s) -> -s.dur).thenComparingLong(s -> s.asigId));
                boolean ok = true;
                for (Sesion s : suyas) {
                    Destino d = mejorDestino(s, List.of());
                    if (d == null) {
                        ok = false;
                        break;
                    }
                    colocar(s, d.dia(), d.ventana());
                }
                int despues = 0;
                for (Sesion s : suyas) {
                    if (s.colocada()) {
                        despues += s.dur;
                    }
                }
                if (!ok || despues <= antes) {
                    restaurarTodo(orig);
                } else {
                    log.accept("  grupo " + g.getNombre() + ": reconstruido " + antes + " -> " + despues + " h");
                }
            }
        }

        /**
         * ¿La colocación de {@code s} en esa ventana sigue siendo legal AHORA? Comprueba las reglas
         * duras que dependen del estado: ventana libre (grupo, maestro y aula) y que la materia no
         * tenga ya otra sesión ese día.
         */
        boolean sigueSiendoValida(Sesion s, int dia, Ventana v) {
            if (asigDia.containsKey(claveDia(s.asigId, dia))) {
                return false;
            }
            for (long bid : v.ids()) {
                if (ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))
                        || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                    return false;
                }
            }
            return true;
        }

        /** Libera una ventana moviendo a quien la ocupa, con reversión atómica. */
        boolean abrirVentana(Sesion s, int prof, List<Long> vetados) {
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return false;
            }
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    if (!vetados.isEmpty() && v.ids().stream().anyMatch(vetados::contains)) {
                        continue;
                    }
                    boolean disponible = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)) {
                            disponible = false;
                            break;
                        }
                    }
                    if (!disponible) {
                        continue;
                    }
                    Set<Sesion> culpables = new LinkedHashSet<>();
                    for (long bid : v.ids()) {
                        Sesion x = ocupG.get(clave(s.gid, bid));
                        if (x != null && x != s) {
                            culpables.add(x);
                        }
                        Sesion y = ocupM.get(clave(s.mid, bid));
                        if (y != null && y != s) {
                            culpables.add(y);
                        }
                        if (s.aid != 0) {
                            Sesion z = ocupA.get(clave(s.aid, bid));
                            if (z != null && z != s) {
                                culpables.add(z);
                            }
                        }
                    }
                    if (culpables.isEmpty()) {
                        if (!sigueSiendoValida(s, d, v)) {
                            continue;
                        }
                        traza.accept("    abre la asignación " + s.asigId + " (" + s.dur + "h) en día " + d
                                + " pos " + v.pos() + " sin mover a nadie");
                        colocar(s, d, v);
                        return true;
                    }
                    if (prof <= 0 || culpables.size() > 2) {
                        continue;
                    }
                    List<int[]> snap = snapshot();
                    for (Sesion x : culpables) {
                        quitar(x);
                    }
                    // Los bloques que hay que liberar se ACUMULAN hacia abajo: la sesión que se mueve
                    // en un nivel no puede acabar en la ventana que un nivel superior está liberando
                    // (ni en la de ningún ancestro). Si se pasara solo la ventana del nivel actual, una
                    // sesión movida dos niveles más abajo aterrizaba justo en la ventana del ancestro
                    // y quedaban dos sesiones en el mismo bloque: era el origen de los choques.
                    List<Long> vetadosHijos = new ArrayList<>(vetados);
                    for (long bid : v.ids()) {
                        if (!vetadosHijos.contains(bid)) {
                            vetadosHijos.add(bid);
                        }
                    }
                    boolean ok = true;
                    for (Sesion x : culpables) {
                        if (!abrirVentana(x, prof - 1, vetadosHijos)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        // La recursión ha movido sesiones: el mundo ya no es el de antes, así que hay
                        // que volver a comprobar que la ventana sigue libre y que la materia no ha
                        // aparecido mientras tanto en ese mismo día (la comprobación del principio del
                        // bucle ya no vale). Sin esto quedaban materias con dos sesiones el mismo día.
                        if (sigueSiendoValida(s, d, v)) {
                            StringBuilder quien = new StringBuilder();
                            for (Sesion x : culpables) {
                                quien.append(' ').append(x.asigId).append('@').append(x.dia).append('/').append(x.pos);
                            }
                            traza.accept("    abre la asignación " + s.asigId + " (" + s.dur + "h) en día " + d
                                    + " pos " + v.pos() + " moviendo:" + quien);
                            colocar(s, d, v);
                            return true;
                        }
                    }
                    restaurarTodo(snap);
                }
            }
            return false;
        }

        // ───────── 4b) reacomodo de GRUPOS COMPLETOS ─────────

        /** Cuántos grupos se llegan a liberar de golpe (más grupos = el rehacer se dispara). */
        private static final int MAX_GRUPOS_REACOMODO = 4;
        /** Tope de sesiones que se liberan en un reacomodo. */
        private static final int MAX_SESIONES_REACOMODO = 120;
        /**
         * MODO PROFUNDO, solo para sesiones largas (2+ h): topes más generosos, porque una larga
         * merece mover más piezas que una de 1 h.
         */
        private static final int MAX_GRUPOS_REACOMODO_PROFUNDO = 6;
        private static final int MAX_SESIONES_REACOMODO_PROFUNDO = 200;

        /** Una ventana candidata a reacomodo: día, ventana y cuántos grupos habría que rehacer. */
        private record Candidata(int dia, Ventana ventana, int grupos) {
        }

        /** Ventanas legales de una sesión por disponibilidad (sin mirar la ocupación). */
        int ventanasLegales(Sesion s) {
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return 0;
            }
            int n = 0;
            for (int d : dias) {
                for (Ventana v : ventanasDe(s, d)) {
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        n++;
                    }
                }
            }
            return n;
        }

        int horasColocadas(List<Sesion> lista) {
            int horas = 0;
            for (Sesion s : lista) {
                if (s.colocada()) {
                    horas += s.dur;
                }
            }
            return horas;
        }

        int pendientesCount() {
            int n = 0;
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    n++;
                }
            }
            return n;
        }

        /**
         * PERTURBACIÓN para el bucle de presupuesto: saca un puñado de sesiones colocadas para que la
         * siguiente búsqueda recombine. La mayor parte de la cuota sale de los ocupantes de las
         * ventanas de las sesiones pendientes (son justo los que las bloquean) y el resto al azar.
         */
        void perturbar() {
            Set<Sesion> objetivos = new LinkedHashSet<>();
            for (Sesion p : sesiones) {
                if (p.colocada()) {
                    continue;
                }
                for (int d : dias) {
                    if (asigDia.containsKey(claveDia(p.asigId, d))) {
                        continue;
                    }
                    for (Ventana v : ventanasDe(p, d)) {
                        if (!disponiblePorDisponibilidad(p, v)) {
                            continue;
                        }
                        for (long bid : v.ids()) {
                            Sesion x = ocupG.get(clave(p.gid, bid));
                            if (x != null) {
                                objetivos.add(x);
                            }
                            Sesion y = ocupM.get(clave(p.mid, bid));
                            if (y != null) {
                                objetivos.add(y);
                            }
                            if (p.aid != 0) {
                                Sesion z = ocupA.get(clave(p.aid, bid));
                                if (z != null) {
                                    objetivos.add(z);
                                }
                            }
                        }
                    }
                }
            }
            List<Sesion> pool = new ArrayList<>(objetivos);
            int cuota = 2 + rnd.nextInt(6);
            for (int i = 0; i < cuota && !pool.isEmpty(); i++) {
                Sesion s = pool.remove(rnd.nextInt(pool.size()));
                if (s.colocada()) {
                    quitar(s);
                }
            }
            List<Sesion> todas = new ArrayList<>();
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    todas.add(s);
                }
            }
            int extra = 1 + rnd.nextInt(3);
            for (int i = 0; i < extra && !todas.isEmpty(); i++) {
                Sesion s = todas.remove(rnd.nextInt(todas.size()));
                if (s.colocada()) {
                    quitar(s);
                }
            }
        }

        List<Sesion> sesionesDe(Set<Long> grupos) {
            List<Sesion> out = new ArrayList<>();
            for (Sesion s : sesiones) {
                if (grupos.contains(s.gid)) {
                    out.add(s);
                }
            }
            return out;
        }

        /** ¿La ventana cae dentro de la disponibilidad del grupo y del maestro? */
        boolean disponiblePorDisponibilidad(Sesion s, Ventana v) {
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return false;
            }
            for (long bid : v.ids()) {
                if (!g.contains(bid) || !m.contains(bid)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Grupos que OCUPAN esta ventana concreta: el propio (su horario llena esos bloques), el del
         * maestro que está dando clase ahí y el del aula. Son los que habría que rehacer para dejar
         * libre justo esta ventana.
         */
        Set<Long> gruposQueOcupan(Sesion s, Ventana v) {
            Set<Long> out = new LinkedHashSet<>();
            out.add(s.gid);
            for (long bid : v.ids()) {
                Sesion x = ocupG.get(clave(s.gid, bid));
                if (x != null && x != s) {
                    out.add(x.gid);
                }
                Sesion y = ocupM.get(clave(s.mid, bid));
                if (y != null && y != s) {
                    out.add(y.gid);
                }
                if (s.aid != 0) {
                    Sesion z = ocupA.get(clave(s.aid, bid));
                    if (z != null && z != s) {
                        out.add(z.gid);
                    }
                }
            }
            return out;
        }

        /**
         * REACOMODAR GRUPOS COMPLETOS.
         *
         * <p>Cuando a una sesión no le cabe la clase porque son OTROS grupos los que ocupan sus
         * ventanas (el maestro está dando clase a otro grupo, el aula está cogida, o su propio grupo
         * tiene el día lleno), libera el horario COMPLETO de esos grupos y lo vuelve a armar junto con
         * la sesión pendiente. Es decir: no se conforma con recolocar la sesión, rehace los grupos que
         * estorban. Si el resultado no mejora, se revierte todo y se queda como estaba.
         *
         * <p>Se prueba de la sesión más restringida a la menos, porque esas son las que interesa
         * rescatar. El conjunto se acota ({@link #MAX_GRUPOS_REACOMODO} grupos, {@link
         * #MAX_SESIONES_REACOMODO} sesiones) para que el rehacer no se dispare: cuando hay demasiados
         * grupos implicados se deja pasar y se reporta como pendiente, que es lo honesto.
         */
        void reacomodarGrupos(int maxVueltas, Consumer<String> log) {
            for (int vuelta = 1; vuelta <= maxVueltas; vuelta++) {
                if (agotado()) {
                    return;
                }
                List<Sesion> pendientes = new ArrayList<>();
                for (Sesion s : sesiones) {
                    if (!s.colocada()) {
                        pendientes.add(s);
                    }
                }
                if (pendientes.isEmpty()) {
                    return;
                }
                pendientes.sort(Comparator.comparingInt(this::ventanasLegales)
                        .thenComparingLong(s -> s.asigId));

                boolean mejoro = false;
                for (Sesion s : pendientes) {
                    if (agotado()) {
                        break;
                    }
                    boolean rescatada = false;
                    // Se ataca VENTANA POR VENTANA y, dentro de cada sesión, primero las ventanas que
                    // exigen rehacer MENOS grupos: son las baratas y las que antes se pueden pagar
                    // dentro del presupuesto. Mirar la unión de todas las ventanas no servía (salían 5
                    // o más grupos implicados y el reacomodo se descartaba siempre), y probarlas en
                    // orden arbitrario quemaba el tiempo en las caras.
                    List<Candidata> candidatas = new ArrayList<>();
                    for (int d : dias) {
                        if (asigDia.containsKey(claveDia(s.asigId, d))) {
                            continue;
                        }
                        for (Ventana v : ventanasDe(s, d)) {
                            if (!disponiblePorDisponibilidad(s, v)) {
                                continue;
                            }
                            int n = gruposQueOcupan(s, v).size();
                            int topeGrupos = s.dur >= 2 ? MAX_GRUPOS_REACOMODO_PROFUNDO : MAX_GRUPOS_REACOMODO;
                            if (n <= topeGrupos) {
                                candidatas.add(new Candidata(d, v, n));
                            }
                        }
                    }
                    candidatas.sort(Comparator.comparingInt(Candidata::grupos)
                            .thenComparingInt(Candidata::dia));

                    for (Candidata cand : candidatas) {
                        if (rescatada || agotado()) {
                            break;
                        }
                        int d = cand.dia();
                        Ventana v = cand.ventana();
                        Set<Long> grupos = gruposQueOcupan(s, v);
                        int topeGrupos = s.dur >= 2 ? MAX_GRUPOS_REACOMODO_PROFUNDO : MAX_GRUPOS_REACOMODO;
                        int topeSesiones = s.dur >= 2 ? MAX_SESIONES_REACOMODO_PROFUNDO : MAX_SESIONES_REACOMODO;
                        if (grupos.size() > topeGrupos) {
                            continue;
                        }
                        List<Sesion> universo = sesionesDe(grupos);
                        if (universo.size() > topeSesiones) {
                            continue;
                        }
                            int antes = horasColocadas(universo);
                            List<int[]> snap = snapshot();

                            // 1) Se libera el horario COMPLETO de esos grupos (y la sesión pendiente).
                            for (Sesion x : universo) {
                                if (x.colocada()) {
                                    quitar(x);
                                }
                            }
                            // 2) Se vuelve a armar fail-first, con la sesión a rescatar la PRIMERA:
                            //    así se queda con la ventana que acabamos de liberar.
                            List<Sesion> orden = new ArrayList<>(universo);
                            orden.remove(s);
                            orden.sort(Comparator.comparingInt(this::ventanasLegales)
                                    .thenComparingInt(x -> -x.dur)
                                    .thenComparingLong(x -> x.asigId));
                            orden.add(0, s);
                            for (Sesion x : orden) {
                                Destino destino = mejorDestino(x, List.of());
                                if (destino != null) {
                                    colocar(x, destino.dia(), destino.ventana());
                                }
                            }
                            // Si el rehacer a lo bruto dejó algo fuera, se insiste SOLO con lo que
                            // quedó sin colocar (es lo barato y lo que suele desatascar la cadena:
                            // liberar un hueco del grupo obliga a recolocar otra de sus clases).
                            List<Sesion> sinColocar = new ArrayList<>();
                            for (Sesion x : universo) {
                                if (!x.colocada()) {
                                    sinColocar.add(x);
                                }
                            }
                            if (!sinColocar.isEmpty() && sinColocar.size() <= 8) {
                                buscarLocal(Math.max(400, sinColocar.size() * 150), sinColocar);
                                for (Sesion x : sinColocar) {
                                    if (!x.colocada()) {
                                        Destino destino = mejorDestino(x, List.of());
                                        if (destino != null) {
                                            colocar(x, destino.dia(), destino.ventana());
                                        }
                                    }
                                }
                            }

                            int despues = horasColocadas(universo);
                            boolean mejor = s.colocada() && despues > antes
                                    && reconstruirMapas().isEmpty();
                            if (mejor) {
                                mejoro = true;
                                rescatada = true;
                                log.accept("  reacomodo " + vuelta + ": asignación " + s.asigId
                                        + " (" + s.dur + "h) rescatada en día " + d + " pos " + v.pos()
                                        + " rehaciendo " + grupos.size() + " grupo(s), "
                                        + universo.size() + " sesiones → " + antes + " a " + despues + " h");
                            } else {
                                restaurarTodo(snap);
                            }
                        }
                    }
                if (!mejoro) {
                    return;
                }
            }
        }

        // ───────── 4d) MODO "ASIGNAR MAESTROS DESDE EL STOCK" ─────────

        void colocarCon(Sesion s, long mid, int dia, Ventana v) {
            s.mid = mid;
            colocar(s, dia, v);
        }

        /** ¿La disponibilidad de ese maestro cubre todos los bloques? */
        boolean cubre(long mid, List<Long> ids) {
            Set<Long> m = dispM.get(mid);
            if (m == null) {
                return false;
            }
            for (long bid : ids) {
                if (!m.contains(bid)) {
                    return false;
                }
            }
            return true;
        }

        /** ¿El maestro tiene choque con OTRA sesión en esos bloques? */
        boolean chocaMaestro(long mid, List<Long> ids, Sesion misma) {
            for (long bid : ids) {
                Sesion x = ocupM.get(clave(mid, bid));
                if (x != null && x != misma) {
                    return true;
                }
            }
            return false;
        }

        /** Regla de Jóvenes: el maestro debe tener OTRA clase (no Jóvenes) en ese grupo. */
        boolean daOtraClaseEnElGrupo(long mid, long gid) {
            for (Sesion s : sesiones) {
                if (s.colocada() && s.gid == gid && s.mid == mid && !s.jovenes) {
                    return true;
                }
            }
            return false;
        }

        /** Igual que {@link #mejorDestino}, pero probando la disponibilidad de un maestro concreto. */
        Destino mejorDestinoConMaestro(Sesion s, long t, List<Long> excluir) {
            Map<Integer, Integer> antes = new HashMap<>();
            for (int d : dias) {
                antes.put(d, costeDia(s.gid, d));
            }
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(t);
            if (g == null || m == null) {
                return null;
            }
            Destino mejor = null;
            int mejorGan = Integer.MIN_VALUE;
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    if (!excluir.isEmpty() && v.ids().stream().anyMatch(excluir::contains)) {
                        continue;
                    }
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)
                                || ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(t, bid))
                                || (s.aid != 0 && ocupA.containsKey(clave(s.aid, bid)))) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    colocarCon(s, t, d, v);
                    int despues = costeDia(s.gid, d);
                    quitar(s);
                    int gan = ganancia(s) - (despues - antes.get(d));
                    if (gan > mejorGan) {
                        mejorGan = gan;
                        mejor = new Destino(d, v);
                    }
                }
            }
            return mejor;
        }

        /**
         * ASIGNAR MAESTROS DESDE EL STOCK (modo opcional, sin tablas nuevas).
         *
         * <p>El stock de una materia son los maestros que ya la imparten en las asignaciones. Esta
         * fase, que corre al final y solo en este modo:
         *
         * <ol>
         *   <li>Re-asigna las sesiones colocadas al maestro del stock que cubre sus bloques con MENOS
         *       carga (así se balancea y se liberan franjas de los saturados).</li>
         *   <li>Intenta colocar cada pendiente probando cada maestro del stock con su disponibilidad
         *       (la misma medición de ventanas, pero por maestro).</li>
         * </ol>
         *
         * <p>REGLAS DURAS del modo: el maestro debe estar en el stock de la materia y disponible en
         * los bloques (y sin choque). Y la regla de Jóvenes: el maestro de Jóvenes debe dar OTRA
         * clase en ese grupo (en este plan) y solo se le asigna UN grupo de Jóvenes por maestro.
         *
         * <p>Si un maestro del stock no tiene fila de disponibilidad cargada, simplemente no puede
         * tomar clases (se refleja en la pre-validación del módulo como aviso).
         */
        void asignarMaestros(Consumer<String> log) {
            Map<Long, Integer> carga = new HashMap<>();
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    carga.merge(s.mid, s.dur, Integer::sum);
                }
            }
            Set<Long> maestrosConJovenes = new LinkedHashSet<>();
            for (Sesion s : sesiones) {
                if (s.colocada() && s.jovenes) {
                    maestrosConJovenes.add(s.mid);
                }
            }

            // 1) Re-asignar las colocadas al maestro del stock con menor carga (si le cubre y no choca).
            int reasignadas = 0;
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    continue;
                }
                long mejor = s.mid;
                int mejorCarga = carga.getOrDefault(s.mid, Integer.MAX_VALUE);
                for (long t : s.stock) {
                    if (t == s.mid) {
                        continue;
                    }
                    if (s.jovenes && maestrosConJovenes.contains(t)) {
                        continue;                       // un Jóvenes por maestro
                    }
                    if (s.jovenes && !daOtraClaseEnElGrupo(t, s.gid)) {
                        continue;                       // debe dar otra clase en el grupo
                    }
                    if (!cubre(t, s.ids) || chocaMaestro(t, s.ids, s)) {
                        continue;
                    }
                    if (creaAdyacencia(s, t, s.dia, s.pos)) {
                        continue;                       // no crear materias pegadas al reasignar
                    }
                    int c = carga.getOrDefault(t, 0);
                    if (c < mejorCarga) {
                        mejor = t;
                        mejorCarga = c;
                    }
                }
                if (mejor != s.mid) {
                    for (long bid : s.ids) {
                        ocupM.remove(clave(s.mid, bid));
                    }
                    carga.merge(s.mid, -s.dur, Integer::sum);
                    s.mid = mejor;
                    for (long bid : s.ids) {
                        ocupM.put(clave(s.mid, bid), s);
                    }
                    carga.merge(s.mid, s.dur, Integer::sum);
                    reasignadas++;
                }
            }

            // 2) Colocar pendientes probando cada maestro del stock.
            int rescatadas = 0;
            List<Sesion> pendientes = new ArrayList<>();
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    pendientes.add(s);
                }
            }
            pendientes.sort(Comparator.comparingInt((Sesion s) -> -s.dur)
                    .thenComparingLong(s -> s.asigId));
            for (Sesion s : pendientes) {
                if (agotado()) {
                    break;
                }
                boolean puesto = false;
                for (long t : s.stock) {
                    if (s.jovenes && maestrosConJovenes.contains(t)) {
                        continue;
                    }
                    if (s.jovenes && !daOtraClaseEnElGrupo(t, s.gid)) {
                        continue;
                    }
                    // Además del maestro, se prueba cada TALLER del stock de la materia.
                    for (long al : s.stockAulas) {
                        long prev = s.aid;
                        s.aid = al;
                        Destino d = mejorDestinoConMaestro(s, t, List.of());
                        if (d == null) {
                            s.aid = prev;
                            continue;
                        }
                        // No se veta la colocación por crear una adyacencia: primero se coloca (la
                        // cobertura manda) y la fase separarAdyacencias, que corre después, la separa
                        // moviendo una de las dos clases a otro día. Veto hubo y dejó horas fuera.
                        colocarCon(s, t, d.dia(), d.ventana());
                        if (s.jovenes) {
                            maestrosConJovenes.add(t);
                        }
                        carga.merge(t, s.dur, Integer::sum);
                        rescatadas++;
                        puesto = true;
                        break;
                    }
                    if (puesto) {
                        break;
                    }
                }
            }
            log.accept("  asignación de maestros y talleres: " + reasignadas + " reasignada(s) · "
                    + rescatadas + " pendiente(s) rescatada(s)");
        }

        /**
         * COMPACTACIÓN FINAL (forma sin tocar cobertura).
         *
         * <p>Repite el reempaquetado por grupo-día hasta que una pasada completa no mejore nada. Como
         * el reempaquetado rehace el día con LAS MISMAS sesiones (todas tienen que quedar colocadas),
         * las horas no cambian: solo se reordenan para arrancar a primera hora, sin huecos y con
         * menos adyacencias. Es la red de seguridad contra "abrí disponibilidad y me dejó huecos".
         */
        void compactar(Consumer<String> log) {
            int mejoras = 0;
            for (int pasada = 1; pasada <= 8; pasada++) {
                if (agotado()) {
                    break;
                }
                int enPasada = 0;
                for (Grupo g : grupos) {
                    for (int d : dias) {
                        if (reempaquetarDia(g.getGrupoId(), d)) {
                            enPasada++;
                        }
                    }
                }
                mejoras += enPasada;
                if (enPasada == 0) {
                    break;
                }
            }
            if (mejoras > 0) {
                log.accept("  compactación: " + mejoras + " grupo-día(s) reordenado(s)");
            }
        }

        /**
         * SEPARAR MATERIAS DEL MISMO MAESTRO EN EL GRUPO.
         *
         * <p>Con la cobertura ya cerrada, se reempaquetan los grupo-día donde un maestro tiene dos
         * materias distintas pegadas, puntuando la adyacencia con 1000: la separación manda sobre
         * huecos y arranques, y como el reempaquetado conserva las mismas sesiones, las horas no se
         * tocan. Si aun así queda alguna pegada, es que ese día no admite otro orden y queda visible
         * en la métrica de adyacencias del intento.
         */
        void separarAdyacencias(Consumer<String> log) {
            int corregidas = 0;
            int pesoOriginal = pesoAdyacencia;
            pesoAdyacencia = 1000;
            try {
                // 1) Dentro del mismo día: reempaquetar los grupo-día con pares pegados.
                for (int pasada = 1; pasada <= 4; pasada++) {
                    if (agotado()) {
                        break;
                    }
                    int enPasada = 0;
                    for (Grupo g : grupos) {
                        for (int d : dias) {
                            if (adyacenciasEn(g.getGrupoId(), d) > 0
                                    && reempaquetarDia(g.getGrupoId(), d)) {
                                enPasada++;
                            }
                        }
                    }
                    corregidas += enPasada;
                    if (enPasada == 0) {
                        break;
                    }
                }
                // 2) Cruzando días: si el par sigue pegado (p. ej. el maestro está ocupado el resto
                //    de ese día), se mueve UNA de las dos clases a otro día legal. Las horas no cambian.
                for (int pasada = 1; pasada <= 3; pasada++) {
                    if (agotado()) {
                        break;
                    }
                    int enPasada = 0;
                    for (Grupo g : grupos) {
                        for (int d : dias) {
                            List<Sesion> par = primerParAdyacente(g.getGrupoId(), d);
                            if (par == null) {
                                continue;
                            }
                            if (separarPar(par.get(0), par.get(1)) || separarPar(par.get(1), par.get(0))) {
                                enPasada++;
                            }
                        }
                    }
                    corregidas += enPasada;
                    if (enPasada == 0) {
                        break;
                    }
                }
            } finally {
                pesoAdyacencia = pesoOriginal;
            }
            int restantes = 0;
            List<String> detalle = new ArrayList<>();
            for (Grupo g : grupos) {
                for (int d : dias) {
                    List<Sesion> par = primerParAdyacente(g.getGrupoId(), d);
                    if (par != null) {
                        restantes++;
                        detalle.add(etiqueta(par.get(0)) + "+" + etiqueta(par.get(1))
                                + " en " + g.getNombre());
                    }
                }
            }
            if (corregidas > 0 || restantes > 0) {
                log.accept("  separación de materias: " + corregidas + " grupo-día(s) corregido(s) · "
                        + restantes + " adyacencia(s) restante(s)");
                for (String linea : detalle) {
                    log.accept("    pegadas (sin otro día legal): " + linea);
                }
            }
        }

        /** Primer par pegado de materias distintas del mismo maestro en ese grupo-día (o null). */
        List<Sesion> primerParAdyacente(long gid, int dia) {
            List<TurnoHorario> arr = dia(gid, dia);
            if (arr.isEmpty()) {
                return null;
            }
            Sesion[] occ = new Sesion[arr.size()];
            for (int p = 0; p < arr.size(); p++) {
                occ[p] = ocupG.get(clave(gid, arr.get(p).getId()));
            }
            for (int p = 0; p + 1 < arr.size(); p++) {
                if (minutos(arr.get(p + 1).getHoraInicio()) - minutos(arr.get(p).getHoraFin())
                        > ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                    continue;
                }
                Sesion a = occ[p];
                Sesion b = occ[p + 1];
                if (a != null && b != null && a.asigId != b.asigId && a.mid == b.mid) {
                    return List.of(a, b);
                }
            }
            return null;
        }

        /**
         * Separa un par pegado moviendo {@code s1} a OTRO día legal (donde grupo, maestro y aula
         * estén libres y no se cree otra adyacencia). Las horas no cambian. Si no hay dónde, se
         * revierte y devuelve false.
         */
        boolean separarPar(Sesion s1, Sesion s2) {
            List<int[]> snap = snapshot();
            int[] orig = quitar(s1);
            long aulaOrig = s1.aid;
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s1.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s1, d)) {
                    if (!disponiblePorDisponibilidad(s1, v)) {
                        continue;
                    }
                    // Además del bloque, se prueban los TALLERES del stock de la materia: en grupos
                    // amarrados a un aula compartida (p. ej. G1), el aula es justo lo que bloquea el
                    // movimiento aunque el maestro tenga toda la disponibilidad del mundo.
                    for (long al : s1.stockAulas) {
                        s1.aid = al;
                        boolean ok = true;
                        for (long bid : v.ids()) {
                            if (ocupG.containsKey(clave(s1.gid, bid)) || ocupM.containsKey(clave(s1.mid, bid))
                                    || (s1.aid != 0 && ocupA.containsKey(clave(s1.aid, bid)))) {
                                ok = false;
                                break;
                            }
                        }
                        if (!ok) {
                            continue;
                        }
                        if (creaAdyacencia(s1, s1.mid, d, v.pos())) {
                            continue;
                        }
                        colocar(s1, d, v);
                        return true;
                    }
                }
            }
            s1.aid = aulaOrig;
            restaurar(s1, orig);
            if (orig == null) {
                // no debería pasar (s1 estaba colocada), pero por si acaso se restaura todo
                restaurarTodo(snap);
            }
            return false;
        }

        /** Pares pegados de materias distintas del mismo maestro en ese grupo-día. */
        int adyacenciasEn(long gid, int dia) {
            List<TurnoHorario> arr = dia(gid, dia);
            if (arr.isEmpty()) {
                return 0;
            }
            Sesion[] occ = new Sesion[arr.size()];
            for (int p = 0; p < arr.size(); p++) {
                occ[p] = ocupG.get(clave(gid, arr.get(p).getId()));
            }
            int n = 0;
            for (int p = 0; p + 1 < arr.size(); p++) {
                if (minutos(arr.get(p + 1).getHoraInicio()) - minutos(arr.get(p).getHoraFin())
                        > ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                    continue;
                }
                Sesion a = occ[p];
                Sesion b = occ[p + 1];
                if (a != null && b != null && a.asigId != b.asigId && a.mid == b.mid) {
                    n++;
                }
            }
            return n;
        }

        /** ¿Colocar s con maestro m en (d, p) crearía dos materias del mismo maestro pegadas? */
        boolean creaAdyacencia(Sesion s, long m, int d, int p) {
            List<TurnoHorario> arr = dia(s.gid, d);
            if (arr.isEmpty()) {
                return false;
            }
            if (p > 0 && minutos(arr.get(p).getHoraInicio()) - minutos(arr.get(p - 1).getHoraFin())
                    <= ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                Sesion x = ocupG.get(clave(s.gid, arr.get(p - 1).getId()));
                if (x != null && x != s && x.mid == m && x.asigId != s.asigId) {
                    return true;
                }
            }
            if (p + s.dur < arr.size()
                    && minutos(arr.get(p + s.dur).getHoraInicio())
                    - minutos(arr.get(p + s.dur - 1).getHoraFin()) <= ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                Sesion y = ocupG.get(clave(s.gid, arr.get(p + s.dur).getId()));
                if (y != null && y != s && y.mid == m && y.asigId != s.asigId) {
                    return true;
                }
            }
            return false;
        }

        // ───────── 5) resultado y comprobación dura ─────────
        IntentoIA aIntento(int numero) {
            IntentoIA it = new IntentoIA();
            it.setNumero(numero);
            it.setHorasDemandadas(demanda);

            it.setProblemas(reconstruirMapas());

            int horas = 0;
            int largas = 0;
            int largasPend = 0;
            Set<Long> todas = new LinkedHashSet<>();
            Set<Long> incompletas = new LinkedHashSet<>();
            for (Sesion s : sesiones) {
                todas.add(s.asigId);
                if (s.colocada()) {
                    horas += s.dur;
                    if (s.dur >= 2) {
                        largas++;
                    }
                } else {
                    incompletas.add(s.asigId);
                    if (s.dur >= 2) {
                        largasPend++;
                    }
                }
            }
            int arranques = 0;
            int castigoHuecos = 0;
            int ady = 0;
            for (Grupo g : grupos) {
                for (int d : dias) {
                    List<TurnoHorario> arr = dia(g.getGrupoId(), d);
                    if (arr.isEmpty()) {
                        continue;
                    }
                    Sesion[] occ = new Sesion[arr.size()];
                    int primera = -1;
                    int ultima = -1;
                    for (int p = 0; p < arr.size(); p++) {
                        occ[p] = ocupG.get(clave(g.getGrupoId(), arr.get(p).getId()));
                        if (occ[p] != null) {
                            if (primera < 0) {
                                primera = p;
                            }
                            ultima = p;
                        }
                    }
                    if (primera < 0) {
                        continue;
                    }
                    if (primera != 0) {
                        arranques++;
                    }
                    int huecos = 0;
                    for (int p = primera; p < ultima; p++) {
                        if (occ[p] == null) {
                            huecos++;
                        }
                    }
                    castigoHuecos += Math.min(ReglasIA.PESO_HUECO * huecos, ReglasIA.TOPE_HUECOS_DIA);
                    for (int p = 0; p + 1 < arr.size(); p++) {
                        if (minutos(arr.get(p + 1).getHoraInicio()) - minutos(arr.get(p).getHoraFin())
                                > ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                            continue;
                        }
                        Sesion a = occ[p];
                        Sesion b = occ[p + 1];
                        if (a != null && b != null && a.asigId != b.asigId && a.mid == b.mid) {
                            ady++;
                        }
                    }
                }
            }
            it.setHoras(horas);
            it.setSesionesLargas(largas);
            it.setSesionesLargasPendientes(largasPend);
            it.setArranquesTarde(arranques);
            it.setCastigoHuecos(castigoHuecos);
            it.setAdyacencias(ady);
            it.setMedium(ReglasIA.medium(horas, demanda, largasPend, arranques, castigoHuecos, ady));
            it.setMateriasTotales(todas.size());
            it.setMateriasCompletas(todas.size() - incompletas.size());

            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    for (long bid : s.ids) {
                        it.getFilas().add(new IntentoIA.Fila(s.asigId, bid, s.mid,
                            s.aid != 0 ? s.aid : null));
                    }
                } else {
                    it.getPendientes().add(pendiente(s));
                }
            }
            return it;
        }

        /** Sesión pendiente con el motivo y sus ventanas legales, diciendo quién las ocupa. */
        private IntentoIA.Pendiente pendiente(Sesion s) {
            IntentoIA.Pendiente p = new IntentoIA.Pendiente();
            p.setAsignacionId(s.asigId);
            p.setGrupoId(s.gid);
            p.setGrupoNombre(s.asig.getGrupo() != null ? s.asig.getGrupo().getNombre() : "?");
            p.setMateriaClave(s.asig.getMateria() != null ? s.asig.getMateria().getClave() : "?");
            p.setMateriaNombre(s.asig.getMateria() != null ? s.asig.getMateria().getNombre() : "?");
            p.setMaestroId(s.mid);
            p.setMaestroNombre(s.asig.getMaestro() != null
                    ? s.asig.getMaestro().getTituloNombreCompleto() : "?");
            p.setDuracion(s.dur);

            Set<Long> g = dispG.getOrDefault(s.gid, Set.of());
            Set<Long> m = dispM.getOrDefault(s.mid, Set.of());
            List<String> detalles = new ArrayList<>();
            for (int d : dias) {
                boolean diaUsado = asigDia.containsKey(claveDia(s.asigId, d));
                for (Ventana v : ventanasDe(s, d)) {
                    boolean disponible = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)) {
                            disponible = false;
                            break;
                        }
                    }
                    if (!disponible) {
                        continue;
                    }
                    List<String> bloqueos = new ArrayList<>();
                    if (diaUsado) {
                        bloqueos.add("la materia ya tiene sesión ese día");
                    }
                    for (long bid : v.ids()) {
                        Sesion x = ocupG.get(clave(s.gid, bid));
                        if (x != null && x != s) {
                            bloqueos.add("el grupo tiene " + etiqueta(x));
                        }
                        Sesion y = ocupM.get(clave(s.mid, bid));
                        if (y != null && y != s && y.asig.getGrupo() != null) {
                            bloqueos.add("el maestro está con " + y.asig.getGrupo().getNombre());
                        }
                    }
                    detalles.add(diaNombre(d) + " " + hora(s, v) + ": "
                            + (bloqueos.isEmpty() ? "LIBRE" : String.join(" + ", bloqueos)));
                }
            }
            p.setMotivo(detalles.isEmpty()
                    ? "Sin ventana legal: la disponibilidad del grupo y del maestro no coinciden en ningún tramo."
                    : "Sus " + detalles.size() + " ventanas legales están ocupadas.");
            p.setVentanas(detalles.size() > 12 ? new ArrayList<>(detalles.subList(0, 12)) : detalles);
            return p;
        }

        private String etiqueta(Sesion s) {
            return s.asig.getMateria() != null ? s.asig.getMateria().getClave() : ("asignación " + s.asigId);
        }

        private String hora(Sesion s, Ventana v) {
            for (int d : dias) {
                List<TurnoHorario> arr = dia(s.gid, d);
                for (int p = 0; p < arr.size(); p++) {
                    if (arr.get(p).getId().equals(v.ids().get(0))) {
                        TurnoHorario fin = arr.get(Math.min(arr.size() - 1, p + v.ids().size() - 1));
                        return arr.get(p).getHoraInicio() + "-" + fin.getHoraFin();
                    }
                }
            }
            return "?";
        }

        private String diaNombre(int d) {
            return switch (d) {
                case 1 -> "lunes";
                case 2 -> "martes";
                case 3 -> "miércoles";
                case 4 -> "jueves";
                case 5 -> "viernes";
                case 6 -> "sábado";
                default -> "día " + d;
            };
        }
    }
}
