package mx.sih.ia;

import mx.sih.modelo.entidad.Asignacion;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.Grupo;
import mx.sih.modelo.entidad.TurnoHorario;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>EL REPARTO POR DÍAS (patrón {@code sih.asignacion.distribucion}) se arma con tres piezas que van
 * juntas:
 * <ul>
 *   <li>las sesiones NACEN del patrón (ver {@link #duracionesDe}): "1,1,1,1,2" son cuatro sesiones de
 *       1 h y una de 2 h, y el motor decide en qué día cae cada una y cuál lleva la doble;</li>
 *   <li>la regla dura es una SESIÓN por materia y día (mapa {@code asigDia}), nunca una hora por día:
 *       la sesión de 2 h ocupa dos bloques del MISMO día y eso no la viola;</li>
 *   <li>el acercamiento al patrón se puntúa como regla blanda ({@link ReglasIA#PESO_DISTRIBUCION}),
 *       así que si el patrón no cabe entero el motor prefiere desviarse antes que dejar horas
 *       pendientes.</li>
 * </ul>
 */
public class GeneradorIA {

    /** Cuántos ciclos extra seguidos sin mejora se toleran antes de declarar convergencia. */
    private static final int MAX_CICLOS_SIN_MEJORA = 5;

    /** Un intento completo. */
    public IntentoIA generarIntento(DatosIA datos, int numero, int maxPasos, long semilla,
                                    int segundosMax, boolean asignarMaestros, boolean asignarAulas,
                                    AsesorIA asesor, Consumer<String> log) {
        long inicio = System.currentTimeMillis();
        long limite = segundosMax > 0 ? inicio + segundosMax * 1000L : 0L;
        Corrida c = new Corrida(datos, semilla, limite);
        // DOS DECISIONES INDEPENDIENTES, que antes eran una sola bandera:
        //   - asignarMaestros: el motor reparte los maestros de cada materia (fase del final).
        //   - asignarAulas: el motor elige el TALLER de cada sesion entre los del stock de su materia,
        //     desde la construccion y en todas las fases, no solo como respaldo.
        // Se combinan libremente. Con las dos apagadas el motor es exactamente el de siempre.
        c.modoAulas = asignarAulas;

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
        c.logDistribucion(registro, "tras construir");
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
            List<long[]> snap = c.snapshot();
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

        // Reparto de MAESTROS desde el stock: al final, para no interferir con las fases normales.
        // Depende SOLO de su bandera: el reparto es el mismo con las aulas libres o fijas.
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

        // Medición final de las AULAS: la separación de adyacencias todavía puede mover una sesión a
        // otro taller del stock, así que el recuento bueno es el de aquí, con el intento ya cerrado.
        // Va con la bandera de AULAS, no con la de maestros: si el aula es fija, no hay nada que medir.
        if (asignarAulas) {
            c.logAulas(registro, "final", true);
        }

        // MEDICIÓN DE LA REGLA DE DISTRIBUCIÓN sobre el horario ya cerrado (incluye lo que hayan
        // podido mover la compactación y la separación de adyacencias): es la línea con la que se
        // comprueba a mano si el motor respeta el patrón.
        c.logDistribucion(registro, "final");

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
     * una de 1 h).
     *
     * <p>El patrón es un CONJUNTO de tamaños de sesión, sin orden ni día: "1,1,1,1,2" son cuatro
     * sesiones de 1 h y una de 2 h, y el motor decide en qué día cae cada una y cuál lleva la doble.
     * Se generan TODAS las sesiones del patrón (aquí nacen las sesiones del motor, ver el constructor
     * de {@link Corrida}), que es lo que después permite exigir una sesión por día sin exigir una hora
     * por día.
     *
     * <p>DATO SUCIO: si la asignación no tiene patrón (nulo, vacío o con formato inválido, criterio de
     * {@link ReglasIA#distribucionValida}) o si el patrón no cuadra con las horas de la asignación, se
     * reparte de una en una y no se le exige distribución (mismo comportamiento que el solver, donde
     * {@code distribucionValida} deja la asignación fuera de la regla). Con los datos del cliente
     * (189/189 asignaciones) todos los patrones suman exactamente las horas, así que este respaldo no
     * se usa.
     */
    public static List<Integer> duracionesDe(Asignacion a) {
        int horas = a.getHoras() == null ? 0 : a.getHoras();
        List<Integer> partes = new ArrayList<>();
        String patron = a.getDistribucion() == null ? "" : a.getDistribucion().trim();
        if (ReglasIA.distribucionValida(patron)) {
            int suma = 0;
            for (int k : ReglasIA.parsearDistribucion(patron)) {
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

    /**
     * Destino: día + ventana + AULA elegida.
     *
     * <p>El aula forma parte del destino porque en modo aulas también la decide el motor: la elige
     * entre las del stock de la materia (ver {@code Corrida.aulaElegida}). Sin modo aulas, o sin stock,
     * siempre es el aula de la asignación y el destino es el de antes.
     */
    private record Destino(int dia, Ventana ventana, long aula) {
    }

    /** Una sesión (trozo del patrón de una asignatura). */
    private static final class Sesion {
        final Asignacion asig;
        final long asigId;
        final int dur;
        final long gid;
        /** Maestro asignado. MUTABLE en el modo "maestros": el motor puede cambiarlo por otro del pool. */
        long mid;
        /** Aula asignada. MUTABLE en el modo aulas: el motor puede elegir otro taller de la materia. */
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

        /** Aula que la tabla le da a esta sesión (la de su asignación); 0 si su asignación no tiene. */
        long aulaDeLaTabla() {
            return asig.getAula() != null ? asig.getAula().getAulaId() : 0L;
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

        /**
         * NOMBRE DE CADA MAESTRO (id → nombre), tomado de las asignaciones ya cargadas.
         *
         * <p>En modo maestros el motor puede cambiar el maestro de una sesión, así que el nombre de una
         * sesión NO se puede leer de su asignación: hay que resolverlo por el id que la sesión tiene
         * ahora. Este mapa es esa fuente única y no consulta nada nuevo.
         */
        final Map<Long, String> nombreDeMaestroPorId = new HashMap<>();

        /**
         * NOMBRE DE CADA AULA (id → nombre), tomado de las asignaciones ya cargadas. Solo lo usa el
         * resumen del modo aulas en la bitácora; no consulta nada nuevo.
         */
        final Map<Long, String> nombreDeAulaPorId = new HashMap<>();

        /**
         * MODO AULAS (asignarAulas == true): el motor elige libremente el AULA de cada sesión entre
         * las del stock de su materia, en todas las fases y no solo como respaldo. Lo enciende
         * {@code generarIntento} antes de construir.
         *
         * <p>Apagado, el aula de una sesión es SIEMPRE la de su asignación: no la cambia ninguna fase,
         * ni siquiera la de separar adyacencias (ver {@code separarPar}). Antes esta decisión venía
         * pegada a la de maestros y aquella fase se saltaba el filtro, así que "stock apagado" no
         * garantizaba que el aula no se moviera.
         */
        boolean modoAulas;

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
        /**
         * Sesiones de cada asignación (id → sus trozos del patrón). Se llena UNA vez en el
         * constructor y no cambia nunca: la usa la regla de distribución, que necesita mirar sólo las
         * sesiones de una asignación (horas que tiene colocadas cada día) sin recorrerlas todas.
         */
        final Map<Long, List<Sesion>> sesionesPorAsig = new LinkedHashMap<>();
        /**
         * Patrón de cada asignación ya parseado (id → tamaños de sesión; array VACÍO si la
         * distribución es dato sucio, criterio {@link ReglasIA#distribucionValida}). Se parsea una
         * sola vez en el constructor: la regla de distribución se evalúa en cada paso de la búsqueda y
         * volver a partir el texto allí sería tirar el presupuesto de tiempo.
         */
        final Map<Long, int[]> patronPorAsig = new LinkedHashMap<>();
        final Map<Long, Sesion> ocupG = new HashMap<>();
        final Map<Long, Sesion> ocupM = new HashMap<>();
        final Map<Long, Sesion> ocupA = new HashMap<>();
        /**
         * REGLA DURA "una SESIÓN por día y materia": clave (asignación, día) → sesión colocada ese
         * día. Se mide por SESIONES, no por horas: una sesión de 2 h ocupa dos bloques del mismo día
         * (es lo que pide un patrón "1,1,1,1,2") y aquí deja UNA sola entrada. Es lo que impide dos
         * sesiones del mismo patrón el mismo día, no dos bloques del mismo día.
         */
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
            // Nombre de cada maestro por id: se lee de las asignaciones ya cargadas.
            for (Asignacion a : asignaciones) {
                if (a.getMaestro() != null) {
                    nombreDeMaestroPorId.putIfAbsent(a.getMaestro().getMaestroId(),
                            a.getMaestro().getTituloNombreCompleto());
                }
            }
            // Stock de talleres por materia: las aulas distintas que ya usa.
            for (Asignacion a : asignaciones) {
                if (a.getMateria() == null || a.getAula() == null) {
                    continue;
                }
                stockAulasPorMateria.computeIfAbsent(a.getMateria().getMateriaId(), k -> new LinkedHashSet<>())
                        .add(a.getAula().getAulaId());
            }
            // Nombre de cada aula por id: se lee de las asignaciones ya cargadas (solo para la bitácora).
            for (Asignacion a : asignaciones) {
                if (a.getAula() != null) {
                    nombreDeAulaPorId.putIfAbsent(a.getAula().getAulaId(), a.getAula().getNombre());
                }
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
                    Sesion sesion = new Sesion(a, dur, stock, stockAulas, jovenes);
                    sesiones.add(sesion);
                    sesionesPorAsig.computeIfAbsent(a.getAsignacionId(), k -> new ArrayList<>()).add(sesion);
                    total += dur;
                }
                patronPorAsig.put(a.getAsignacionId(), ReglasIA.parsearDistribucion(a.getDistribucion()));
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

        // ───────── elección del AULA al colocar (modo aulas) ─────────

        /**
         * AULAS CANDIDATAS de una sesión: el STOCK de su materia, es decir, las aulas que esa materia
         * ya usa en las asignaciones (el universo de candidatos). Si el stock está vacío -o el modo
         * aulas está apagado- el universo es solo el aula de la asignación, y todo queda como estaba.
         */
        List<Long> aulasCandidatas(Sesion s) {
            if (!modoAulas || s.stockAulas.isEmpty()) {
                return List.of(s.aid);
            }
            return new ArrayList<>(s.stockAulas);
        }

        /** ¿Esa aula la ocupa OTRA sesión en alguno de esos bloques? (choque de aula: regla dura) */
        boolean aulaOcupada(long aula, List<Long> ids, Sesion misma) {
            if (aula == 0) {
                return false;
            }
            for (long bid : ids) {
                Sesion x = ocupA.get(clave(aula, bid));
                if (x != null && x != misma) {
                    return true;
                }
            }
            return false;
        }

        /**
         * CARGA de cada aula candidata: bloques que ya ocupa dentro del horario del turno de este
         * grupo. Es el dato del desempate "mejor encaje"; se calcula una vez por colocación (no por
         * ventana) y solo con las claves de {@code ocupA}, sin mantener ningún contador nuevo.
         */
        Map<Long, Integer> cargaDeAulas(List<Long> candidatas, Sesion s) {
            Map<Long, Integer> out = new LinkedHashMap<>();
            for (long al : candidatas) {
                int n = 0;
                if (al != 0) {
                    for (int d : dias) {
                        for (TurnoHorario b : dia(s.gid, d)) {
                            if (ocupA.containsKey(clave(al, b.getId()))) {
                                n++;
                            }
                        }
                    }
                }
                out.put(al, n);
            }
            return out;
        }

        /**
         * ELECCIÓN DEL AULA AL COLOCAR (modo aulas): devuelve el aula elegida para esa sesión en esa
         * ventana, o {@code null} si NINGUNA candidata está libre (entonces la ventana no sirve).
         *
         * <p>El aula ya no viene fijada por la asignación: se elige entre las candidatas cada vez que
         * la sesión se coloca, no solo cuando el aula de la asignación falla.
         *
         * <p>CRITERIO Y DESEMPATES:
         * <ol>
         *   <li>Regla dura: el aula tiene que estar libre en TODOS los bloques de la ventana (el choque
         *       de aula de siempre).</li>
         *   <li>Desempate 1 (tabla): se prefiere el aula que la sesión ya traía -que al principio es la
         *       de su asignación- y, si no, el aula de la asignación. Así no se cambia de taller sin
         *       motivo y el horario se queda cerca de lo que dice la tabla.</li>
         *   <li>Desempate 2 (encaje): entre las demás libres, la que menos bloques tiene ocupados en el
         *       turno de este grupo, que es la que deja más hueco a las clases que faltan por colocar.</li>
         *   <li>Desempate 3: id menor, para que la elección sea determinista y reproducible.</li>
         * </ol>
         */
        Long aulaElegida(Sesion s, List<Long> candidatas, List<Long> ids, long aulaActual,
                         Map<Long, Integer> carga) {
            long tabla = s.aulaDeLaTabla();
            Long mejor = null;
            int mejorPref = 0;
            int mejorCarga = 0;
            for (long al : candidatas) {
                if (aulaOcupada(al, ids, s)) {
                    continue;
                }
                int pref = (al == aulaActual || al == tabla) ? 0 : 1;
                int c = carga.getOrDefault(al, 0);
                if (mejor == null || pref < mejorPref
                        || (pref == mejorPref && (c < mejorCarga || (c == mejorCarga && al < mejor)))) {
                    mejor = al;
                    mejorPref = pref;
                    mejorCarga = c;
                }
            }
            return mejor;
        }

        /** Coloca la sesión en el destino elegido, con el aula que decidió el motor (no la que traía). */
        void colocarDestino(Sesion s, Destino d) {
            s.aid = d.aula();
            colocar(s, d.dia(), d.ventana());
        }

        /**
         * Destinos posibles ahora mismo (día + ventana + aula) respetando disponibilidad y ocupación.
         *
         * <p>En modo aulas el aula se elige aquí, ventana a ventana; sin stock el destino lleva el aula
         * de la asignación y la lista es exactamente la de antes.
         */
        List<Destino> candidatas(Sesion s) {
            List<Destino> out = new ArrayList<>();
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(s.mid);
            if (g == null || m == null) {
                return out;
            }
            List<Long> cands = aulasCandidatas(s);
            Map<Long, Integer> carga = cands.size() > 1 ? cargaDeAulas(cands, s) : Map.of();
            long aulaActual = s.aid;
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid) || !m.contains(bid)
                                || ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    Long al = aulaElegida(s, cands, v.ids(), aulaActual, carga);
                    if (al != null) {
                        out.add(new Destino(d, v, al));
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

        /**
         * Quita la sesión y devuelve su colocación anterior (null si no estaba colocada).
         *
         * <p>Guarda también el AULA: en modo aulas el aula es una decisión del motor y forma parte del
         * estado, así que deshacer una colocación tiene que devolverla a la de antes.
         */
        long[] quitar(Sesion s) {
            if (!s.colocada()) {
                return null;
            }
            long[] antes = {s.dia, s.pos, s.aid};
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

        void restaurar(Sesion s, long[] antes) {
            if (antes == null) {
                return;
            }
            s.aid = antes[2];
            colocar(s, (int) antes[0], new Ventana((int) antes[1], bloquesDe(s, (int) antes[0], (int) antes[1])));
        }

        List<Long> bloquesDe(Sesion s, int dia, int pos) {
            List<TurnoHorario> arr = dia(s.gid, dia);
            List<Long> ids = new ArrayList<>();
            for (int x = pos; x < pos + s.dur; x++) {
                ids.add(arr.get(x).getId());
            }
            return List.copyOf(ids);
        }

        /** Colocación de cada sesión: {día, posición, aula}, o null si está pendiente. */
        List<long[]> snapshot() {
            List<long[]> snap = new ArrayList<>(sesiones.size());
            for (Sesion s : sesiones) {
                snap.add(s.colocada() ? new long[]{s.dia, s.pos, s.aid} : null);
            }
            return snap;
        }

        /**
         * Devuelve TODAS las sesiones al estado guardado en el snapshot (día, posición y aula).
         *
         * <p>Se quitan todas primero y se colocan después. Hacerlo de una en una (quitar y colocar la
         * misma) dejaba ocupados los bloques de las sesiones que aún no se habían restaurado, así que
         * la colocación restaurada de una podía caer encima de la actual de otra: de ahí salían los
         * "choque en el bloque N" y las materias con dos sesiones el mismo día que detectaba la
         * comprobación dura.
         */
        void restaurarTodo(List<long[]> snap) {
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    quitar(s);
                }
            }
            for (int i = 0; i < sesiones.size(); i++) {
                long[] w = snap.get(i);
                if (w != null) {
                    Sesion s = sesiones.get(i);
                    s.aid = w[2];
                    colocar(s, (int) w[0], new Ventana((int) w[1], bloquesDe(s, (int) w[0], (int) w[1])));
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
            // Regla blanda de distribución: se resta igual que el resto de castigos de forma. Es el
            // mismo término que suma ReglasIA.medium, así que el "score" de la bitácora y el medium
            // del intento cuentan siempre lo mismo.
            total -= ReglasIA.PESO_DISTRIBUCION * desvioDistribucionTotal();
            return total;
        }

        // ───────── regla blanda: respetar la distribución (patrón) ─────────

        /**
         * DESVÍO DEL PATRÓN de una asignación: cuántas horas habría que cambiar de día para que el
         * reparto de lo que lleva colocado coincida con su {@code distribucion}.
         *
         * <p>Cuenta HORAS por día (la sesión de ese día, porque la regla dura sólo admite una sesión
         * por materia y día) y las compara —como multiset ordenado— con el patrón recortado a las
         * horas colocadas: ver {@link ReglasIA#desvioDistribucion}. Como el patrón no dice en qué día
         * va cada sesión, colocar la doble en lunes o en jueves desvía lo mismo: el motor elige el día
         * libremente.
         *
         * <p>Devuelve 0 si la asignación no tiene patrón (dato sucio, criterio
         * {@link ReglasIA#distribucionValida}), si no tiene ninguna hora colocada (de las que faltan
         * se encarga PESO_HORA) o si el reparto ya coincide.
         */
        int desvioPatron(long asigId) {
            int[] patron = patronPorAsig.get(asigId);
            if (patron == null || patron.length == 0) {
                return 0;
            }
            List<Sesion> suyas = sesionesPorAsig.get(asigId);
            if (suyas == null || suyas.isEmpty()) {
                return 0;
            }
            int colocadas = 0;
            for (Sesion s : suyas) {
                if (s.colocada()) {
                    colocadas++;
                }
            }
            if (colocadas == 0) {
                return 0;
            }
            // Horas colocadas en cada día. Como la regla dura sólo admite una sesión de la materia por
            // día, normalmente son tantas entradas como sesiones colocadas; se agrupa por día igualmente
            // porque en mitad de una prueba (o de una reversión) pueden coincidir dos.
            int[] dias = new int[colocadas];
            int[] horas = new int[colocadas];
            int k = 0;
            for (Sesion s : suyas) {
                if (!s.colocada()) {
                    continue;
                }
                int i = 0;
                while (i < k && dias[i] != s.dia) {
                    i++;
                }
                if (i == k) {
                    dias[k] = s.dia;
                    horas[k] = s.dur;
                    k++;
                } else {
                    horas[i] += s.dur;
                }
            }
            return ReglasIA.desvioDistribucion(patron, k == horas.length ? horas
                    : Arrays.copyOf(horas, k));
        }

        /** Suma del desvío de todas las asignaciones (el término que entra en el score). */
        int desvioDistribucionTotal() {
            int total = 0;
            for (long asigId : sesionesPorAsig.keySet()) {
                total += desvioPatron(asigId);
            }
            return total;
        }

        /**
         * Línea de bitácora de la regla de distribución: cuántas asignaciones con patrón se desvían,
         * cuánto suman y las peores (para poder verificar el efecto a mano).
         */
        void logDistribucion(Consumer<String> log, String momento) {
            int conPatron = 0;
            int desviadas = 0;
            int total = 0;
            List<Map.Entry<String, Integer>> peores = new ArrayList<>();
            for (Map.Entry<Long, int[]> e : patronPorAsig.entrySet()) {
                if (e.getValue() == null || e.getValue().length == 0) {
                    continue;               // sin patrón (dato sucio): no se le exige nada
                }
                conPatron++;
                int desvio = desvioPatron(e.getKey());
                if (desvio > 0) {
                    desviadas++;
                    total += desvio;
                    List<Sesion> suyas = sesionesPorAsig.get(e.getKey());
                    Sesion cualquiera = suyas != null && !suyas.isEmpty() ? suyas.get(0) : null;
                    String nombre = cualquiera == null ? ("asignación " + e.getKey())
                            : etiqueta(cualquiera)
                                    + (cualquiera.asig.getGrupo() != null
                                            ? " (" + cualquiera.asig.getGrupo().getNombre() + ")" : "");
                    peores.add(Map.entry(nombre, desvio));
                }
            }
            log.accept("  distribución " + momento + ": " + desviadas + " de " + conPatron
                    + " asignaciones con patrón se desvían · desvío total " + total);
            if (!peores.isEmpty()) {
                peores.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                StringBuilder linea = new StringBuilder();
                for (int i = 0; i < Math.min(3, peores.size()); i++) {
                    if (i > 0) {
                        linea.append(" · ");
                    }
                    linea.append(peores.get(i).getKey()).append(" desvía ").append(peores.get(i).getValue());
                }
                log.accept("    peores: " + linea);
            }
        }

        /**
         * Mejor destino para una sesión SIN colocarla (no toca el estado salvo el temporal de la
         * medición, que se deshace): mayor ganancia neta respecto al estado actual. El destino incluye
         * el AULA cuando el modo aulas está encendido (ver {@link #aulaElegida}).
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
            List<Long> cands = aulasCandidatas(s);
            Map<Long, Integer> carga = cands.size() > 1 ? cargaDeAulas(cands, s) : Map.of();
            long aulaActual = s.aid;
            Destino mejor = null;
            int mejorGan = Integer.MIN_VALUE;
            // Desvío del patrón de la asignación tal como está ahora (con s fuera): la ganancia de
            // cada destino lleva también lo que ese destino acerca o aleja del patrón.
            int patronAntes = desvioPatron(s.asigId);
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
                                || ocupG.containsKey(clave(s.gid, bid)) || ocupM.containsKey(clave(s.mid, bid))) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    Long al = aulaElegida(s, cands, v.ids(), aulaActual, carga);
                    if (al == null) {
                        continue;
                    }
                    // La prueba se hace con el aula elegida (si no, se escribiría la ocupación del aula
                    // de la asignación, que puede estar cogida por otra sesión) y se deshace entera: la
                    // sesión queda SIN colocar y con el aula que traía; la fija el llamador al colocar.
                    s.aid = al;
                    colocar(s, d, v);
                    int despues = costeDia(s.gid, d);
                    int patron = desvioPatron(s.asigId);
                    quitar(s);                                  // se deja SIN colocar
                    s.aid = aulaActual;
                    int gan = ganancia(s) - (despues - antes.get(d))
                            - ReglasIA.PESO_DISTRIBUCION * (patron - patronAntes);
                    if (gan > mejorGan) {
                        mejorGan = gan;
                        mejor = new Destino(d, v, al);
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
                List<long[]> hechoAsesor = snapshot();
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
                    colocarDestino(s, d);
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
                    long aulaAntes = s.aid;
                    int antes = costeDia(s.gid, d.dia());
                    int patronAntes = desvioPatron(s.asigId);
                    colocarDestino(s, d);
                    int delta = ganancia(s) - (costeDia(s.gid, d.dia()) - antes)
                            - ReglasIA.PESO_DISTRIBUCION * (desvioPatron(s.asigId) - patronAntes);
                    if (!(delta > 0 || (delta == 0 && rnd.nextBoolean()))) {
                        quitar(s);
                        s.aid = aulaAntes;      // el aula elegida solo se queda si la colocación se acepta
                    }
                    continue;
                }

                if (rnd.nextInt(100) < 8) {                     // dejarla pendiente
                    int dia = s.dia;
                    int antes = costeDia(s.gid, dia);
                    int patronAntes = desvioPatron(s.asigId);
                    long[] orig = quitar(s);
                    int delta = -ganancia(s) + (costeDia(s.gid, dia) - antes)
                            - ReglasIA.PESO_DISTRIBUCION * (desvioPatron(s.asigId) - patronAntes);
                    if (!(delta > 0 || (delta == 0 && rnd.nextBoolean()))) {
                        restaurar(s, orig);
                    }
                    continue;
                }

                int diaOrig = s.dia;
                int posOrig = s.pos;
                int antesA = costeDia(s.gid, diaOrig);
                int patronAntes = desvioPatron(s.asigId);
                long[] orig = quitar(s);
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
                colocarDestino(s, d);
                int delta = ganancia(s)
                        - ((costeDia(s.gid, d.dia()) - antesB) + (costeDia(s.gid, diaOrig) - antesA))
                        - ReglasIA.PESO_DISTRIBUCION * (desvioPatron(s.asigId) - patronAntes);
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
            List<long[]> orig = new ArrayList<>();
            for (Sesion s : suyas) {
                orig.add(s.colocada() ? new long[]{s.dia, s.pos, s.aid} : null);
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
            List<long[]> orig = new ArrayList<>();
            for (Sesion s : suyas) {
                orig.add(s.colocada() ? new long[]{s.dia, s.pos, s.aid} : null);
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
                            colocarDestino(s, d);
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
                        List<long[]> snap = snapshot();
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
                                    colocarDestino(x, destino);
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
                List<long[]> orig = new ArrayList<>();
                for (Sesion s : grupo) {
                    orig.add(s.colocada() ? new long[]{s.dia, s.pos, s.aid} : null);
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
                    colocarDestino(s, d);
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
                List<long[]> orig = snapshot();
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
                    colocarDestino(s, d);
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
                    List<long[]> snap = snapshot();
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
                            List<long[]> snap = snapshot();

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
                                    colocarDestino(x, destino);
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
                                            colocarDestino(x, destino);
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
        //
        // MODELO NUEVO (sin Timefold): el stock de una materia es la LISTA DE MAESTROS HABILITADOS
        // (los que la tienen asignada en algun grupo de sih.asignacion) y ese es el universo de
        // candidatos; el maestro ya no viene fijado por la asignacion. El motor elige quien cubre cada
        // (materia, grupo) -un LOTE- con una condicion dura: cada maestro debe cubrir EXACTAMENTE los
        // grupos que la tabla le asigna. Como las horas de una materia son las mismas en todos los
        // grupos (salen del catalogo), cambiar de maestro un grupo no mueve horas: solo decide quien da
        // que. El Jovenes se resuelve DENTRO del reparto (sigue a la materia del grupo), no despues.

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

        /**
         * La mejor ventana de UN dia para colocar s con el maestro t, o null si no hay ninguna legal.
         *
         * <p>{@code antes} es el coste del dia antes de colocar (lo pasa el llamador, que lo midio con
         * la sesion fuera del tablero): se recibe para no volver a medirlo en cada ventana probada.
         */
        Ventana mejorVentanaConMaestro(Sesion s, long t, int d, int antes) {
            if (asigDia.containsKey(claveDia(s.asigId, d))) {
                return null;
            }
            Set<Long> g = dispG.get(s.gid);
            Set<Long> m = dispM.get(t);
            if (g == null || m == null) {
                return null;
            }
            Ventana mejor = null;
            int mejorGan = Integer.MIN_VALUE;
            for (Ventana v : ventanasDe(s, d)) {
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
                int gan = ganancia(s) - (costeDia(s.gid, d) - antes);
                quitar(s);
                if (gan > mejorGan) {
                    mejorGan = gan;
                    mejor = v;
                }
            }
            return mejor;
        }

        /** Maestro que la tabla le asigna a esa sesion: el punto de partida del reparto (0 si no tiene). */
        long maestroDeLaTabla(Sesion s) {
            return s.asig.getMaestro() != null ? s.asig.getMaestro().getMaestroId() : 0L;
        }

        /** El maestro tiene disponible y libre todos esos bloques (sin choque con otra clase). */
        boolean bloqueado(long mid, List<Long> ids, Sesion misma) {
            Set<Long> m = dispM.get(mid);
            if (m == null) {
                return true;
            }
            for (long bid : ids) {
                if (!m.contains(bid)) {
                    return true;
                }
                Sesion x = ocupM.get(clave(mid, bid));
                if (x != null && x != misma) {
                    return true;
                }
            }
            return false;
        }

        /** El maestro puede recibir esa sesion tal como esta colocada. */
        boolean puedeCubrir(long t, Sesion s) {
            return !bloqueado(t, s.ids, s);
        }

        /**
         * Cambia el maestro de una sesion YA colocada, rehaciendo la ocupacion de sus bloques.
         *
         * <p>Si la sesion esta pendiente no hay nada que rehacer: tenia los bloques vacios
         * ({@code ids == List.of()} y {@code dia == null}, ver {@link #quitar}), asi que el unico
         * efecto de seguir seria cambiarle el maestro, que es justo lo que NO debe hacer un metodo
         * que dice "mover una sesion colocada". Se devuelve sin tocar nada.
         */
        void moveCon(Sesion s, long de, long a) {
            if (!s.colocada()) {
                return;
            }
            for (long bid : s.ids) {
                ocupM.remove(clave(de, bid));
            }
            s.mid = a;
            for (long bid : s.ids) {
                ocupM.put(clave(a, bid), s);
            }
        }

        /**
         * Coloca (o recoloca) la sesion con ese maestro en la mejor ventana legal del dia {@code dia}.
         *
         * <p>Se prueba ademas cada TALLER del stock de la materia. Si no hay destino, deja la sesion
         * tal como estaba: el aula y el maestro anteriores se restauran, para no dejar estado a medias.
         */
        boolean colocarConMaestro(Sesion s, long t, List<Long> aulas, int dia) {
            long prevAula = s.aid;
            // El coste del dia no depende del aula (solo de la ocupacion del grupo), asi que se mide
            // una vez y se reutiliza en cada taller candidato.
            int antes = costeDia(s.gid, dia);
            for (long al : aulas) {
                s.aid = al;
                Ventana v = mejorVentanaConMaestro(s, t, dia, antes);
                if (v == null) {
                    continue;
                }
                if (s.colocada()) {
                    // Ya colocada: se libera antes de recolocarla, si no chocaria consigo misma.
                    quitar(s);
                }
                colocarCon(s, t, dia, v);
                return true;
            }
            s.aid = prevAula;
            return false;
        }

        /**
         * Intenta colocar la sesion con ese maestro en cualquiera de sus talleres del stock.
         *
         * <p>El orden de los talleres es el desempate de la elección de aula (ver {@link #aulaElegida}):
         * primero el aula que la sesión ya traía -que al principio es la de su asignación-, después el
         * resto del stock. Así el motor no cambia de taller sin motivo, pero ya no se queda clavado en
         * el de la asignación: si está ocupado, prueba los demás del stock.
         */
        boolean intentarColocar(Sesion s, long t) {
            List<Long> aulas = new ArrayList<>();
            if (s.aid != 0) {
                aulas.add(s.aid);
            }
            for (long al : aulasCandidatas(s)) {
                if (!aulas.contains(al)) {
                    aulas.add(al);
                }
            }
            if (aulas.isEmpty()) {
                aulas.add(0L);          // sin aula (stock vacío y asignación sin taller): como siempre
            }
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                if (colocarConMaestro(s, t, aulas, d)) {
                    return true;
                }
            }
            return false;
        }

        // ───────── 4d.1) LOTES, CUOTAS Y REPARTO EXACTO ─────────

        /**
         * LOTE: todas las sesiones de UNA materia en UN grupo que la tabla le asigna al mismo maestro.
         *
         * <p>Es la unidad del reparto. El tope se cuenta por GRUPOS: dos filas de la misma (materia,
         * grupo, maestro) -los datos reales traen asignaciones partidas, DSAUPOO 9+4- son UN lote y por
         * tanto UN grupo, aunque sus horas se sumen. Y como las horas de una materia son las mismas en
         * todos los grupos, pasar el lote a otro grupo no cambia las horas de nadie: solo decide QUIEN
         * da esa materia a ese grupo, que es justo lo que el motor elige.
         *
         * <p>Los lotes de Jovenes no entran en este reparto: su maestro se deduce del reparto de las
         * OTRAS materias del grupo (ver {@link #repartirJovenes}).
         */
        private static final class Lote {
            final long materiaId;
            final long gid;
            /** Maestro que la tabla le asigna a esta materia en este grupo. */
            final long maestroOriginal;
            final boolean jovenes;
            final List<Sesion> sesiones = new ArrayList<>();
            /** Maestro que cubre el lote AHORA MISMO. Es la decision que toma el motor. */
            long mid;

            Lote(long materiaId, long gid, long maestroOriginal, boolean jovenes) {
                this.materiaId = materiaId;
                this.gid = gid;
                this.maestroOriginal = maestroOriginal;
                this.jovenes = jovenes;
                this.mid = maestroOriginal;
            }

            /** Universo de candidatos: el stock de la materia (los maestros habilitados en la tabla). */
            Set<Long> stock() {
                return sesiones.isEmpty() ? Set.of() : sesiones.get(0).stock;
            }
        }

        /**
         * Lotes del horario: (materia, grupo, maestro de la tabla). Las sesiones sin materia no tienen
         * stock ni cuota con la que repartir, asi que se quedan como esten (igual que antes).
         */
        List<Lote> lotes() {
            Map<String, Lote> porClave = new LinkedHashMap<>();
            for (Sesion s : sesiones) {
                if (s.asig.getMateria() == null) {
                    continue;
                }
                long mid = maestroDeLaTabla(s);
                String clave = s.asig.getMateria().getMateriaId() + "|" + s.gid + "|" + mid;
                Lote l = porClave.computeIfAbsent(clave, k -> new Lote(
                        s.asig.getMateria().getMateriaId(), s.gid, mid, s.jovenes));
                l.sesiones.add(s);
            }
            return new ArrayList<>(porClave.values());
        }

        /** Lotes agrupados por materia: el reparto es por materia, porque el stock es por materia. */
        Map<Long, List<Lote>> porMateria(List<Lote> lotes) {
            Map<Long, List<Lote>> out = new LinkedHashMap<>();
            for (Lote l : lotes) {
                out.computeIfAbsent(l.materiaId, k -> new ArrayList<>()).add(l);
            }
            return out;
        }

        /** Lotes que NO son de Jovenes, agrupados por grupo: son los que dan la otra materia del grupo. */
        Map<Long, List<Lote>> lotesPorGrupo(List<Lote> lotes) {
            Map<Long, List<Lote>> out = new LinkedHashMap<>();
            for (Lote l : lotes) {
                if (!l.jovenes) {
                    out.computeIfAbsent(l.gid, k -> new ArrayList<>()).add(l);
                }
            }
            return out;
        }

        /**
         * CUOTA EXACTA por (materia, maestro): grupos DISTINTOS que la tabla le asigna. Las filas
         * repetidas de la misma (materia, grupo, maestro) cuentan UNA vez: sus horas se suman, pero el
         * tope es de grupos.
         *
         * <p>La cuota sale de los LOTES que hay que repartir (que son los de la tabla menos las filas de
         * 0 h, que no dan clase), y se contrasta con la tabla: si un par materia-maestro no cuadra, se
         * avisa y manda el horario. Asi la suma de cuotas es exactamente el numero de lotes y el reparto
         * exacto siempre es alcanzable partiendo del reparto de la tabla.
         */
        Map<Long, Map<Long, Integer>> cuotaExacta(List<Lote> lotes, Consumer<String> log) {
            Map<Long, Map<Long, Set<Long>>> tabla = new LinkedHashMap<>();
            for (Asignacion a : asignaciones) {
                if (a.getMateria() == null || a.getMaestro() == null || a.getGrupo() == null) {
                    continue;
                }
                tabla.computeIfAbsent(a.getMateria().getMateriaId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(a.getMaestro().getMaestroId(), k -> new LinkedHashSet<>())
                        .add(a.getGrupo().getGrupoId());
            }
            Map<Long, Map<Long, Integer>> lotesPorPar = new LinkedHashMap<>();
            for (Lote l : lotes) {
                lotesPorPar.computeIfAbsent(l.materiaId, k -> new LinkedHashMap<>())
                        .merge(l.maestroOriginal, 1, Integer::sum);
            }
            Map<Long, Map<Long, Integer>> cuota = new LinkedHashMap<>();
            int ajustadas = 0;
            // La cuota se arma sobre los lotes que hay que repartir (nunca sobre las materias que no
            // entran, como Jovenes): asi la suma de cuotas es el numero de lotes y el reparto exacto es
            // alcanzable desde el reparto de la tabla.
            for (Map.Entry<Long, Map<Long, Integer>> e : lotesPorPar.entrySet()) {
                Map<Long, Set<Long>> enTabla = tabla.getOrDefault(e.getKey(), Map.of());
                Map<Long, Integer> fila = new LinkedHashMap<>();
                for (Map.Entry<Long, Integer> t : e.getValue().entrySet()) {
                    if (enTabla.getOrDefault(t.getKey(), Set.of()).size() != t.getValue()) {
                        ajustadas++;
                    }
                    fila.put(t.getKey(), t.getValue());
                }
                for (Map.Entry<Long, Set<Long>> t : enTabla.entrySet()) {
                    // La tabla lista ese par pero sin sesiones que repartir (fila de 0 h): cuota 0.
                    if (!fila.containsKey(t.getKey())) {
                        fila.put(t.getKey(), 0);
                        ajustadas++;
                    }
                }
                cuota.put(e.getKey(), fila);
            }
            if (ajustadas > 0) {
                log.accept("  cuota: " + ajustadas + " par(es) materia-maestro donde la tabla y el horario no "
                        + "cuadran (una fila de 0 h no da clase): manda el horario, no la fila");
            }
            return cuota;
        }

        /**
         * Lo bien que le cae el lote a un maestro del stock: primero que sus sesiones se queden donde
         * estan (sin mover dia, ventana ni taller) y despues que las pendientes tengan hueco por
         * disponibilidad. El desempate (+1) favorece al maestro de la tabla: si todo cabe igual, no se
         * mueve nada.
         */
        int compatibilidad(Lote l, long t) {
            int puntos = 1000 * sesionesEnSitio(l, t);
            for (Sesion s : l.sesiones) {
                if (!s.colocada()) {
                    puntos += 10 * Math.min(20, ventanasConDisponibilidad(s, t));
                }
            }
            return puntos + (t == l.maestroOriginal ? 1 : 0);
        }

        /** Sesiones del lote que seguirian tal cual con ese maestro (disponibilidad y sin choque). */
        int sesionesEnSitio(Lote l, long t) {
            int n = 0;
            for (Sesion s : l.sesiones) {
                if (s.colocada() && cubre(t, s.ids) && !chocaMaestro(t, s.ids, s)) {
                    n++;
                }
            }
            return n;
        }

        /** Ventanas que le quedan a una sesion pendiente por disponibilidad (grupo y maestro). */
        int ventanasConDisponibilidad(Sesion s, long t) {
            Set<Long> g = dispG.get(s.gid);
            if (g == null || dispM.get(t) == null) {
                return 0;
            }
            int n = 0;
            for (int d : dias) {
                if (asigDia.containsKey(claveDia(s.asigId, d))) {
                    continue;
                }
                for (Ventana v : ventanasDe(s, d)) {
                    boolean ok = true;
                    for (long bid : v.ids()) {
                        if (!g.contains(bid)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok && cubre(t, v.ids())) {
                        n++;
                    }
                }
            }
            return n;
        }

        /**
         * REPARTO LIBRE: decide que maestro del stock cubre cada lote de la materia.
         *
         * <p>Arranca del reparto de la tabla (que ya cumple la cuota exacta) y lo mejora con
         * INTERCAMBIOS entre lotes de la misma materia: cada maestro sigue con el mismo numero de
         * grupos, asi que la cuota exacta se mantiene por construccion y lo unico que cambia es QUIEN da
         * que grupo. Solo se acepta el intercambio si mejora la compatibilidad, de modo que el reparto de
         * la tabla se toca unicamente cuando de verdad ayuda (sobre todo a colocar pendientes).
         */
        void repartir(Map<Long, List<Lote>> porMateria) {
            for (List<Lote> lotes : porMateria.values()) {
                for (int pasada = 1; pasada <= 4; pasada++) {
                    boolean cambio = false;
                    for (int i = 0; i < lotes.size(); i++) {
                        for (int j = i + 1; j < lotes.size(); j++) {
                            Lote a = lotes.get(i);
                            Lote b = lotes.get(j);
                            if (a.mid == b.mid || !intercambioLegal(lotes, a, b)) {
                                continue;
                            }
                            int antes = compatibilidad(a, a.mid) + compatibilidad(b, b.mid);
                            int despues = compatibilidad(a, b.mid) + compatibilidad(b, a.mid);
                            if (despues > antes) {
                                long t = a.mid;
                                a.mid = b.mid;
                                b.mid = t;
                                cambio = true;
                            }
                        }
                    }
                    if (!cambio) {
                        break;
                    }
                }
            }
        }

        /**
         * Un intercambio de maestros entre dos lotes de la misma materia no puede dejar a un maestro con
         * DOS lotes en el mismo grupo: entonces cubriria menos grupos distintos de los que le tocan y la
         * cuota dejaria de ser exacta.
         */
        boolean intercambioLegal(List<Lote> lotes, Lote a, Lote b) {
            if (a.gid == b.gid) {
                // Mismo grupo: el juego de pares (grupo, maestro) no cambia, solo se dan la vuelta.
                return true;
            }
            // Tras el intercambio, a queda con b.mid en a.gid y b con a.mid en b.gid.
            for (Lote l : lotes) {
                if (l == a || l == b) {
                    continue;
                }
                if (l.mid == b.mid && l.gid == a.gid) {
                    return false;
                }
                if (l.mid == a.mid && l.gid == b.gid) {
                    return false;
                }
            }
            return true;
        }

        /** Estado de TODAS las sesiones (maestro, dia, posicion y taller), para probar y deshacer. */
        List<long[]> snapshotMaestros() {
            List<long[]> est = new ArrayList<>(sesiones.size());
            for (Sesion s : sesiones) {
                est.add(new long[]{s.mid, s.colocada() ? s.dia : -1L, s.pos, s.aid});
            }
            return est;
        }

        /** Devuelve todas las sesiones (maestro y taller incluidos) al estado guardado. */
        void restaurarMaestros(List<long[]> est) {
            for (Sesion s : sesiones) {
                if (s.colocada()) {
                    quitar(s);
                }
            }
            for (int i = 0; i < sesiones.size(); i++) {
                long[] e = est.get(i);
                Sesion s = sesiones.get(i);
                s.mid = e[0];
                s.aid = e[3];
                if (e[1] >= 0) {
                    colocar(s, (int) e[1], new Ventana((int) e[2], bloquesDe(s, (int) e[1], (int) e[2])));
                }
            }
        }

        /** Rehace la decision de cada lote a partir de sus sesiones (tras deshacer un intento). */
        void releerPlan(List<Lote> lotes) {
            for (Lote l : lotes) {
                if (!l.sesiones.isEmpty()) {
                    l.mid = l.sesiones.get(0).mid;
                }
            }
        }

        /**
         * Pone el lote ENTERO con un maestro del stock, sin perder nada de lo ya colocado.
         *
         * <p>Cada sesion colocada se queda en su ventana y su taller si ese maestro la puede cubrir; si
         * no, se recoloca con los talleres del stock. Las sesiones que ya venian PENDIENTES se intentan
         * colocar, pero no bloquean el cambio: puede que el unico hueco sea para las demas.
         *
         * <p>Requisito duro: si alguna sesion que estaba colocada se quedaria pendiente, se deshace TODO
         * (maestro incluido) y devuelve false. Es el invariante del modo maestros: el reparto nunca puede
         * costar cobertura.
         */
        boolean ponerLote(Lote l, long t) {
            List<long[]> antes = snapshotMaestros();
            for (Sesion s : l.sesiones) {
                if (s.mid == t) {
                    continue;
                }
                boolean estaba = s.colocada();
                if (estaba && puedeCubrir(t, s)) {
                    moveCon(s, s.mid, t);           // el maestro nuevo la cubre donde esta: no se mueve
                    continue;
                }
                if (estaba) {
                    quitar(s);
                }
                s.mid = t;
                if (!intentarColocar(s, t) && estaba) {
                    restaurarMaestros(antes);
                    return false;
                }
            }
            l.mid = t;
            return true;
        }

        /** Grupos distintos que cada maestro cubre AHORA en esos lotes. */
        Map<Long, Set<Long>> gruposCubiertos(List<Lote> lotes) {
            Map<Long, Set<Long>> out = new LinkedHashMap<>();
            for (Lote l : lotes) {
                out.computeIfAbsent(l.mid, k -> new LinkedHashSet<>()).add(l.gid);
            }
            return out;
        }

        /** ¿El reparto de esa materia cubre exactamente la cuota de grupos de cada maestro? */
        boolean repartoExacto(List<Lote> lotes, Map<Long, Integer> cuota) {
            Map<Long, Set<Long>> cubre = gruposCubiertos(lotes);
            for (Map.Entry<Long, Integer> e : cuota.entrySet()) {
                if (cubre.getOrDefault(e.getKey(), Set.of()).size() != e.getValue()) {
                    return false;
                }
            }
            for (Long t : cubre.keySet()) {
                if (!cuota.containsKey(t)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * CUADRA LA CUOTA: si un lote no cupo con el maestro que le toco en el plan y se quedo con el de
         * la tabla, ese maestro acaba con un grupo de mas y el otro con uno de menos. Aqui se le pasa el
         * lote sobrante a un maestro al que le falten grupos (probando antes los que mejor le caen).
         *
         * <p>Cada movimiento es un {@link #ponerLote}: si no cabe, se deshace y se prueba el siguiente,
         * asi que cuadrar la cuota tampoco puede costar cobertura. Devuelve cuantos lotes movio.
         */
        int cuadrarCuotas(List<Lote> lotes, Map<Long, Integer> cuota) {
            int movidos = 0;
            for (int vuelta = 0; vuelta < lotes.size() * 2 + 2; vuelta++) {
                Map<Long, Set<Long>> cubre = gruposCubiertos(lotes);
                boolean movido = false;
                for (Lote l : lotes) {
                    if (cubre.getOrDefault(l.mid, Set.of()).size() <= cuota.getOrDefault(l.mid, 0)) {
                        continue;
                    }
                    long mejor = 0;
                    int mejorPuntos = Integer.MIN_VALUE;
                    for (Map.Entry<Long, Integer> e : cuota.entrySet()) {
                        long t = e.getKey();
                        if (t == l.mid) {
                            continue;
                        }
                        Set<Long> suyos = cubre.getOrDefault(t, Set.of());
                        if (suyos.size() >= e.getValue() || suyos.contains(l.gid)) {
                            continue;
                        }
                        int puntos = compatibilidad(l, t);
                        if (puntos > mejorPuntos) {
                            mejorPuntos = puntos;
                            mejor = t;
                        }
                    }
                    if (mejor != 0 && ponerLote(l, mejor)) {
                        movidos++;
                        movido = true;
                        break;
                    }
                }
                if (!movido) {
                    break;
                }
            }
            return movidos;
        }

        /**
         * RESCATE DE PENDIENTES con el reparto ya exacto: una sesion que no cupo con el maestro de su
         * lote puede caber con otro del stock. Se prueban los candidatos de mejor a peor y, si el cambio
         * deja el reparto exacto y coloca mas horas que antes, se queda; si no, se deshace ENTERO
         * (reparto incluido).
         *
         * <p>Es el "intercambio" del modelo nuevo: mover al maestro de grupo para poder colocar. El
         * Jovenes no estorba aqui porque se resuelve despues, ya con el reparto cerrado.
         *
         * <p>Devuelve cuantas sesiones nuevas se colocaron.
         */
        int rescatarPendientes(List<Lote> lotes, Map<Long, Integer> cuota) {
            int rescatadas = 0;
            for (Lote l : lotes) {
                if (agotado()) {
                    break;
                }
                boolean pendiente = false;
                for (Sesion s : l.sesiones) {
                    if (!s.colocada()) {
                        pendiente = true;
                        break;
                    }
                }
                if (!pendiente) {
                    continue;
                }
                List<Long> candidatos = new ArrayList<>(l.stock());
                candidatos.remove(l.mid);
                candidatos.sort(Comparator.comparingInt((Long t) -> -compatibilidad(l, t))
                        .thenComparingLong(Long::longValue));
                for (long t : candidatos) {
                    if (agotado()) {
                        break;
                    }
                    List<long[]> antes = snapshotMaestros();
                    int horasAntes = horasColocadas(sesiones);
                    int sesionesAntes = sesiones.size() - pendientesCount();
                    if (!ponerLote(l, t)) {
                        restaurarMaestros(antes);
                        releerPlan(lotes);
                        continue;
                    }
                    cuadrarCuotas(lotes, cuota);
                    if (!repartoExacto(lotes, cuota) || horasColocadas(sesiones) <= horasAntes) {
                        restaurarMaestros(antes);
                        releerPlan(lotes);
                        continue;
                    }
                    rescatadas += (sesiones.size() - pendientesCount()) - sesionesAntes;
                    break;
                }
            }
            return rescatadas;
        }

        /**
         * REGLA DE JOVENES DENTRO DEL REPARTO (no despues).
         *
         * <p>Un maestro da Jovenes en UNA sola clase y en un grupo donde ademas da otra materia. En el
         * modelo nuevo el Jovenes SIGUE A LA MATERIA DEL GRUPO: los candidatos de un grupo son los
         * maestros que ya dan otra materia alli, y se elige uno que no tenga Jovenes en otro grupo. Como
         * el Jovenes se decide al final, con el reparto de las demas materias ya cerrado, un intercambio
         * nunca se bloquea por el ni deja la sesion huerfana: si su maestro se va del grupo, el Jovenes
         * pasa al que entra.
         *
         * <p>Se atacan primero los grupos con menos candidatos (los mas apretados). Si un grupo se queda
         * sin candidato libre, el Jovenes se queda con el maestro que traia -el horario base ya lo tenia
         * colocado- y se avisa: la cobertura no se toca.
         */
        void repartirJovenes(List<Lote> lotesJovenes, Map<Long, List<Lote>> porGrupo,
                             Map<Long, String> nombreGrupo, Map<Long, String> nombreMaestro,
                             Consumer<String> log) {
            List<Lote> orden = new ArrayList<>(lotesJovenes);
            orden.sort(Comparator.comparingInt((Lote l) -> candidatosJovenes(l, porGrupo, Set.of()).size())
                    .thenComparingLong(l -> l.gid));
            Set<Long> conJovenes = new LinkedHashSet<>();
            List<String> detalle = new ArrayList<>();
            List<String> avisos = new ArrayList<>();
            for (Lote l : orden) {
                long elegido = 0;
                for (long t : candidatosJovenes(l, porGrupo, conJovenes)) {
                    if (ponerLote(l, t)) {
                        elegido = t;
                        break;
                    }
                }
                if (elegido == 0 && l.mid != 0 && daOtraClaseEnElGrupo(l.mid, l.gid)
                        && !conJovenes.contains(l.mid)) {
                    elegido = l.mid;                    // se queda como estaba: ya cumplia la regla
                }
                if (elegido == 0) {
                    avisos.add(nombreGrupo.getOrDefault(l.gid, "grupo " + l.gid));
                    continue;
                }
                conJovenes.add(elegido);
                detalle.add(nombreGrupo.getOrDefault(l.gid, "grupo " + l.gid) + " → "
                        + nombreMaestro.getOrDefault(elegido, "#" + elegido));
            }
            log.accept("  jóvenes: " + detalle.size() + " de " + lotesJovenes.size()
                    + " grupo(s) con un maestro que también da otra materia allí (uno por maestro)");
            if (!detalle.isEmpty()) {
                log.accept("    jóvenes: " + String.join(" · ", detalle));
            }
            for (String a : avisos) {
                log.accept("    jóvenes sin candidato libre: " + a + " (se deja el maestro que traía)");
            }
        }

        /**
         * Maestros que pueden dar el Jovenes de ese grupo: los que ya dan OTRA materia (colocada) alli y
         * no tienen Jovenes en otro grupo. Primero el de la tabla, que es el que traia el horario base.
         */
        List<Long> candidatosJovenes(Lote l, Map<Long, List<Lote>> porGrupo, Set<Long> conJovenes) {
            LinkedHashSet<Long> out = new LinkedHashSet<>();
            if (l.maestroOriginal != 0 && daOtraClaseEnElGrupo(l.maestroOriginal, l.gid)) {
                out.add(l.maestroOriginal);
            }
            for (Lote otro : porGrupo.getOrDefault(l.gid, List.of())) {
                if (otro.mid != 0 && daOtraClaseEnElGrupo(otro.mid, l.gid)) {
                    out.add(otro.mid);
                }
            }
            out.removeAll(conJovenes);
            return new ArrayList<>(out);
        }

        /**
         * BITACORA DEL REPARTO: por materia, que grupos cubre cada maestro contra la cuota de la tabla.
         * Es la linea que permite comprobar a mano que el reparto es exacto sin abrir la base de datos.
         */
        void logReparto(List<Lote> lotes, Map<Long, Map<Long, Integer>> cuota,
                        Map<Long, String> nombreGrupo, Map<Long, String> nombreMaestro,
                        Map<Long, String> claveMateria, Consumer<String> log) {
            Map<Long, List<Lote>> porMateria = porMateria(lotes);
            int materias = 0;
            int exactas = 0;
            for (Map.Entry<Long, List<Lote>> e : porMateria.entrySet()) {
                Map<Long, Set<Long>> cubre = gruposCubiertos(e.getValue());
                Map<Long, Integer> tope = cuota.getOrDefault(e.getKey(), Map.of());
                boolean exacto = repartoExacto(e.getValue(), tope);
                materias++;
                if (exacto) {
                    exactas++;
                }
                List<Long> maestros = new ArrayList<>(cubre.keySet());
                maestros.sort(Comparator.comparingInt((Long t) -> -cubre.get(t).size())
                        .thenComparingLong(Long::longValue));
                List<String> trozos = new ArrayList<>();
                for (Long t : maestros) {
                    Set<Long> gs = cubre.get(t);
                    trozos.add(nombreMaestro.getOrDefault(t, "#" + t) + " → " + gs.size() + "/"
                            + tope.getOrDefault(t, 0) + " grupos ["
                            + nombresDeGrupos(gs, nombreGrupo) + "]");
                }
                log.accept("    " + claveMateria.getOrDefault(e.getKey(), "materia " + e.getKey())
                        + (exacto ? "" : " (!)") + ": " + String.join(" · ", trozos));
            }
            log.accept("  reparto exacto de grupos: " + exactas + " de " + materias
                    + " materia(s) cumplen la cuota de la tabla (maestro → grupos cubiertos/cuota)");
        }

        /** Nombres de grupo de un conjunto, acotados para que la bitacora se pueda leer. */
        private String nombresDeGrupos(Set<Long> gids, Map<Long, String> nombreGrupo) {
            List<String> out = new ArrayList<>();
            for (Long g : gids) {
                if (out.size() == 6) {
                    out.add("...");
                    break;
                }
                out.add(nombreGrupo.getOrDefault(g, "#" + g));
            }
            return String.join(", ", out);
        }

        /**
         * ASIGNAR MAESTROS DESDE EL STOCK (modo opcional, sin tablas nuevas).
         *
         * <p>EL MODELO: el stock de una materia es la lista de maestros HABILITADOS (los que la tienen
         * asignada en algun grupo de la tabla) y es el universo de candidatos. El motor elige quien
         * cubre cada (materia, grupo) -un LOTE- con una condicion dura: cada maestro debe cubrir
         * EXACTAMENTE los grupos que la tabla le asigna (si tiene Biologia en 2 grupos, da Biologia en 2
         * grupos, los que el motor quiera; varias filas de la misma (materia, grupo) suman horas pero
         * cuentan UN grupo). Como las horas de una materia son las mismas en todos los grupos, cambiar de
         * maestro un grupo no mueve horas: solo decide quien da que.
         *
         * <p>ORIGEN: la fase corre al final y parte del horario ya generado, asi que su invariante es no
         * perder cobertura. Cada movimiento se prueba entero y, si alguna sesion colocada se quedaria
         * pendiente, se deshace: el horario solo puede mejorar.
         *
         * <ol>
         *   <li>Lotes y cuota exacta de grupos por (materia, maestro).</li>
         *   <li>Reparto libre por materia: intercambios entre lotes de la misma materia (mantienen la
         *       cuota por construccion) que mejoran la compatibilidad, sobre todo la de las pendientes.</li>
         *   <li>Aplicacion del reparto lote a lote y cuadre de cuotas; si una materia no queda exacta, se
         *       devuelve al reparto de la tabla, que ya era exacto.</li>
         *   <li>Rescate de pendientes probando otros maestros del stock, re-cuadrando o deshaciendo.</li>
         *   <li>Jovenes desde el reparto ya cerrado de cada grupo.</li>
         * </ol>
         *
         * <p>La bitacora imprime, por materia, cuantos grupos cubre cada maestro contra su cuota: es lo
         * que permite verificar el reparto exacto a mano.
         */
        void asignarMaestros(Consumer<String> log) {
            Map<Long, String> nombreGrupo = new LinkedHashMap<>();
            for (Grupo g : grupos) {
                nombreGrupo.put(g.getGrupoId(), g.getNombre());
            }
            Map<Long, String> nombreMaestro = new LinkedHashMap<>();
            Map<Long, String> claveMateria = new LinkedHashMap<>();
            for (Asignacion a : asignaciones) {
                if (a.getMateria() != null) {
                    claveMateria.putIfAbsent(a.getMateria().getMateriaId(), a.getMateria().getClave());
                }
                if (a.getMaestro() != null) {
                    nombreMaestro.putIfAbsent(a.getMaestro().getMaestroId(),
                            a.getMaestro().getTituloNombreCompleto());
                }
            }

            List<long[]> estadoInicial = snapshotMaestros();
            int horasAntes = horasColocadas(sesiones);
            int pendientesAntes = pendientesCount();

            // 1) LOTES Y CUOTAS.
            List<Lote> lotes = new ArrayList<>();
            List<Lote> lotesJovenes = new ArrayList<>();
            for (Lote l : lotes()) {
                (l.jovenes ? lotesJovenes : lotes).add(l);
            }
            Map<Long, List<Lote>> porMateria = porMateria(lotes);
            Map<Long, Map<Long, Integer>> cuota = cuotaExacta(lotes, log);
            Map<Long, List<Lote>> porGrupo = lotesPorGrupo(lotes);

            // 2) EL MOTOR REPARTE: libre, pero con la cuota exacta de grupos de la tabla.
            repartir(porMateria);

            // 3) APLICAR EL REPARTO Y CUADRAR LAS CUOTAS.
            int aplicados = 0;
            int devueltas = 0;
            for (Map.Entry<Long, List<Lote>> e : porMateria.entrySet()) {
                List<Lote> suyos = e.getValue();
                Map<Long, Integer> tope = cuota.getOrDefault(e.getKey(), Map.of());
                List<long[]> antes = snapshotMaestros();
                for (Lote l : suyos) {
                    if (l.mid == l.maestroOriginal) {
                        continue;                       // el reparto de la tabla se respeta: no se toca
                    }
                    if (ponerLote(l, l.mid)) {
                        aplicados++;
                    } else {
                        l.mid = l.maestroOriginal;      // no cabe entero: se queda con el de la tabla
                    }
                }
                cuadrarCuotas(suyos, tope);
                if (!repartoExacto(suyos, tope)) {
                    // La exactitud manda: se devuelve ESA materia al reparto de la tabla, que el
                    // snapshot guarda tal cual y que ya cumplia la cuota.
                    restaurarMaestros(antes);
                    releerPlan(suyos);
                    devueltas++;
                }
            }

            // 4) RESCATE DE PENDIENTES.
            int rescatadas = 0;
            for (Map.Entry<Long, List<Lote>> e : porMateria.entrySet()) {
                rescatadas += rescatarPendientes(e.getValue(), cuota.getOrDefault(e.getKey(), Map.of()));
            }

            // 5) JOVENES: sigue a la materia del grupo, ya con el reparto cerrado.
            repartirJovenes(lotesJovenes, porGrupo, nombreGrupo, nombreMaestro, log);

            // 6) INVARIANTE DEL MODO: nunca menos horas colocadas que al entrar.
            if (horasColocadas(sesiones) < horasAntes) {
                restaurarMaestros(estadoInicial);
                log.accept("  asignación de maestros: se deshace el reparto (habría bajado la cobertura)");
            }

            logReparto(lotes, cuota, nombreGrupo, nombreMaestro, claveMateria, log);
            logAulas(log, "tras el reparto de maestros", false);
            log.accept("  asignación de maestros (stock): " + aplicados + " lote(s) con maestro nuevo · "
                    + devueltas + " materia(s) devuelta(s) al reparto de la tabla · rescate de pendientes: "
                    + rescatadas + " sesión(es) · cobertura " + horasAntes + " → "
                    + horasColocadas(sesiones) + " h (" + pendientesAntes + " → " + pendientesCount()
                    + " pendiente(s))");
        }

        /**
         * MEDICIÓN DEL EFECTO DE ELEGIR EL AULA LIBREMENTE (modo aulas).
         *
         * <p>Cuenta las sesiones colocadas que acabaron en un taller DISTINTO al de su asignación -que
         * es justo lo que puede cambiar este modo- y, si se pide detalle, resume por materia qué
         * talleres se usan y cuántas sesiones van a cada uno. Con eso el efecto del cambio se puede
         * atribuir desde la bitácora, sin abrir la base de datos.
         */
        void logAulas(Consumer<String> log, String etiqueta, boolean detalle) {
            int colocadas = 0;
            int distintas = 0;
            Map<Long, Map<Long, Integer>> porMateria = new LinkedHashMap<>();
            for (Sesion s : sesiones) {
                if (!s.colocada()) {
                    continue;
                }
                colocadas++;
                if (s.aid != s.aulaDeLaTabla()) {
                    distintas++;
                }
                long materiaId = s.asig.getMateria() != null ? s.asig.getMateria().getMateriaId() : 0L;
                porMateria.computeIfAbsent(materiaId, k -> new LinkedHashMap<>())
                        .merge(s.aid, 1, Integer::sum);
            }
            log.accept("  aulas (stock, " + etiqueta + "): " + distintas + " de " + colocadas
                    + " sesión(es) colocadas en un taller distinto al de su asignación");
            if (!detalle) {
                return;
            }
            List<Long> materias = new ArrayList<>(porMateria.keySet());
            materias.sort(Long::compareTo);
            for (Long materiaId : materias) {
                List<String> trozos = new ArrayList<>();
                for (Map.Entry<Long, Integer> a : porMateria.get(materiaId).entrySet()) {
                    trozos.add(nombreDeAula(a.getKey()) + " → " + a.getValue());
                }
                log.accept("    aulas " + claveDeMateria(materiaId) + ": " + String.join(" · ", trozos));
            }
        }

        /** Clave legible de una materia para la bitácora ("materia N" cuando no se conoce). */
        private String claveDeMateria(long materiaId) {
            for (Asignacion a : asignaciones) {
                if (a.getMateria() != null && a.getMateria().getMateriaId() == materiaId) {
                    return a.getMateria().getClave() != null ? a.getMateria().getClave() : "materia " + materiaId;
                }
            }
            return "materia " + materiaId;
        }

        /** Nombre del aula de un id para la bitácora ("sin aula" para el 0). */
        private String nombreDeAula(long aid) {
            if (aid == 0) {
                return "sin aula";
            }
            String nombre = nombreDeAulaPorId.get(aid);
            return nombre != null ? nombre : "aula " + aid;
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
            List<long[]> snap = snapshot();
            long[] orig = quitar(s1);
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
                    //
                    // Se prueban los del stock AUNQUE la bandera de talleres esté apagada, y es
                    // deliberado: esto es un rescate de ultimo recurso para despegar dos clases
                    // pegadas del mismo maestro, no la eleccion normal del aula. La bandera gobierna
                    // la eleccion normal (aulasCandidatas); si aqui se respetara, el par se quedaria
                    // pegado y la metrica de adyacencias subiria sin que el usuario hubiera pedido
                    // nada parecido.
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
            // Regla blanda del patrón: el desvío de todas las asignaciones entra en el medium igual
            // que en el solver, para que el intento que se elige sea también el que mejor reparte.
            int desvio = desvioDistribucionTotal();
            it.setMedium(ReglasIA.medium(horas, demanda, largasPend, arranques, castigoHuecos, ady, desvio));
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

        /** Nombre del maestro de un id, con "?" cuando no hay maestro (id 0 o desconocido). */
        private String nombreMaestro(long mid) {
            if (mid == 0) {
                return "?";
            }
            String nombre = nombreDeMaestroPorId.get(mid);
            return nombre != null ? nombre : "?";
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
            // El nombre TIENE que ser el del maestro de ESTA sesión (s.mid), no el de la asignación:
            // en modo maestros el motor puede haberle dado otro maestro al grupo.
            p.setMaestroNombre(nombreMaestro(s.mid));
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
