package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.AsesorIA;
import mx.sih.ia.DatosIA;
import mx.sih.ia.IntentoIA;
import mx.sih.modelo.dto.TrabajoIADTO;
import mx.sih.modelo.dto.ValidacionIADTO;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ejecuta el GENERADOR IA en segundo plano: varios intentos, y el mejor se queda.
 *
 * <p>Cada intento es independiente y parte de cero (no hereda el anterior): así se exploran
 * soluciones distintas. El usuario puede ver cómo avanza cada intento con sus horas pendientes, y
 * puede terminar cuando alguno le sirva.
 *
 * <h2>Concurrencia: hay dos pools, y la diferencia importa</h2>
 *
 * <ul>
 *   <li><b>{@code cocineros}</b>: un pool COMPARTIDO por todo el servidor, con {@code app.ia.hilos}
 *       hilos (3 por defecto). Es el recurso escaso —los núcleos— y por eso es uno solo: así tres
 *       escuelas generando a la vez no crean tres pools de tres hilos cada uno (nueve intentos en
 *       memoria), sino que comparten los mismos tres. La memoria queda acotada por el tamaño del
 *       pool, no por el número de usuarios.</li>
 *   <li><b>{@code coordinadores}</b>: un hilo barato por trabajo, que solo carga los datos, suelta
 *       intentos y los recoge. Es lo que permite que varios trabajos estén EN_PROCESO a la vez en
 *       lugar de hacer fila.</li>
 * </ul>
 *
 * <p>Los intentos se sueltan en TANDAS y el tamaño de la tanda se reparte entre los trabajos que
 * están compitiendo en ese momento: una escuela sola se lleva los 3 hilos (6 intentos = 2 tandas
 * ≈ 400 s en vez de ~1200 s), y tres escuelas usan una tanda de 1 cada una, de modo que las tres
 * avanzan y terminan más o menos juntas en vez de que la última espere una hora.
 *
 * <p>GENERAR NO ESCRIBE EN LA BASE. El motor calcula y guarda los intentos en memoria; el horario
 * real solo se toca al REGISTRAR un intento, que es una acción aparte del usuario. Por eso varios
 * trabajos pueden generar a la vez sin pisarse.
 *
 * <p>Si ya hay un trabajo EN_COLA o EN_PROCESO con el mismo alcance (escuela, semestre, turno), se
 * devuelve ese en vez de encolar otro.
 */
@Service
public class HorarioIATrabajoServicio {

    private static final Logger logger = LoggerFactory.getLogger(HorarioIATrabajoServicio.class);

    public static final String EN_COLA = "EN_COLA";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String ERROR = "ERROR";

    private static final int MAX_TRABAJOS = 20;
    private static final Duration RETENCION = Duration.ofHours(2);

    /** Estado de un trabajo. */
    private static final class Trabajo {
        private final String id = UUID.randomUUID().toString();
        private final Long escuelaId;
        private final Long semestreId;
        private final Long turnoId;
        private final String modo;
        private final int intentosPlaneados;
        private final int segundosPorIntento;
        private final int maxPasos;
        /** Modo "asignar maestros desde el stock" (el motor elige el maestro por disponibilidad). */
        private final boolean asignarMaestros;
        /** Modo "asignar talleres desde el stock" (el motor elige el taller de la materia). Independiente. */
        private final boolean asignarAulas;
        private final String solicitadoPor;
        private final LocalDateTime encoladoEn = LocalDateTime.now();

        private volatile String estado = EN_COLA;
        private volatile String mensaje = "En cola";
        private volatile LocalDateTime iniciadoEn;
        private volatile LocalDateTime finalizadoEn;
        private volatile String error;

        private volatile int intentoActual;
        private volatile int horasDemandadas;
        private volatile Integer mejorNumero;
        private volatile boolean cancelado;

        private volatile Integer registrado;
        private volatile LocalDateTime registradoEn;
        private volatile int filasRegistradas;
        private volatile int horasRegistradas;

        /** Se van quedando los intentos para poder verlos y registrar el que interese. */
        private final Map<Integer, IntentoIA> intentos = new ConcurrentHashMap<>();

        /** La validación previa, para poder mostrarla junto al trabajo. */
        private volatile ValidacionIADTO validacion;

        /**
         * Asesor de ESTE trabajo (la heurística o un LLM con la clave que mandó el usuario).
         *
         * <p>La clave vive dentro de este objeto y solo durante la generación: al terminar el trabajo
         * se pone a null y se pierde. No se guarda, no se registra y no se devuelve en ninguna
         * respuesta.
         */
        private volatile AsesorIA asesor;

        private Trabajo(Long escuelaId, Long semestreId, Long turnoId, String modo,
                        int intentosPlaneados, int segundosPorIntento, int maxPasos,
                        boolean asignarMaestros, boolean asignarAulas, String solicitadoPor) {
            this.escuelaId = escuelaId;
            this.semestreId = semestreId;
            this.turnoId = turnoId;
            this.modo = modo;
            this.intentosPlaneados = intentosPlaneados;
            this.segundosPorIntento = segundosPorIntento;
            this.maxPasos = maxPasos;
            this.asignarMaestros = asignarMaestros;
            this.asignarAulas = asignarAulas;
            this.solicitadoPor = solicitadoPor;
        }
    }

    /** Cuántos trabajos de escuelas distintas pueden estar generando a la vez. */
    private static final int TRABAJOS_A_LA_VEZ = 4;

    private final Map<String, Trabajo> trabajos = new ConcurrentHashMap<>();
    private final HorarioIAServicio servicio;

    /** Pool COMPARTIDO de intentos: aquí está el recurso escaso, los núcleos. */
    private final ExecutorService cocineros;

    /** Un hilo barato por trabajo: carga datos, suelta intentos y los recoge. */
    private final ExecutorService coordinadores;

    /** Valores por defecto, configurables. */
    private final int intentosPorDefecto;
    private final int segundosPorIntentoDefecto;
    private final int maxPasosDefecto;
    private final int hilos;

    public HorarioIATrabajoServicio(HorarioIAServicio servicio,
                                    @Value("${app.ia.intentos:6}") int intentosPorDefecto,
                                    @Value("${app.ia.segundos-por-intento:60}") int segundosPorIntentoDefecto,
                                    @Value("${app.ia.max-pasos:60000}") int maxPasosDefecto,
                                    @Value("${app.ia.hilos:3}") int hilos) {
        this.servicio = servicio;
        this.intentosPorDefecto = intentosPorDefecto <= 0 ? 6 : intentosPorDefecto;
        this.segundosPorIntentoDefecto = segundosPorIntentoDefecto <= 0 ? 60 : segundosPorIntentoDefecto;
        this.maxPasosDefecto = maxPasosDefecto <= 0 ? 60000 : maxPasosDefecto;
        // Tope de 16 por si alguien escribe un número absurdo en la configuración: más hilos que
        // núcleos no acelera nada, solo reparte el mismo tiempo entre más intentos a medias.
        this.hilos = hilos <= 0 ? 3 : Math.min(hilos, 16);
        this.cocineros = Executors.newFixedThreadPool(this.hilos, daemon("ia-intento"));
        this.coordinadores = Executors.newFixedThreadPool(TRABAJOS_A_LA_VEZ, daemon("ia-trabajo"));
        logger.info("Generador IA listo: {} hilo(s) de intentos, hasta {} trabajo(s) a la vez",
                this.hilos, TRABAJOS_A_LA_VEZ);
    }

    /** Hilos daemon y con nombre: se distinguen en un volcado y no frenan el apagado. */
    private static ThreadFactory daemon(String prefijo) {
        AtomicInteger contador = new AtomicInteger();
        return tarea -> {
            Thread hilo = new Thread(tarea, prefijo + "-" + contador.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        };
    }

    public int getIntentosPorDefecto() {
        return intentosPorDefecto;
    }

    public int getSegundosPorIntentoDefecto() {
        return segundosPorIntentoDefecto;
    }

    public int getMaxPasosDefecto() {
        return maxPasosDefecto;
    }

    /**
     * Cuántos intentos se calculan en paralelo ({@code app.ia.hilos}).
     *
     * <p>Lo necesita la interfaz para estimar el tiempo: con N intentos a la vez, el trabajo dura
     * ceil(intentos / hilos) tandas, no 'intentos' veces. Sin este dato la barra de progreso se
     * quedaría en un tercio al terminar.
     */
    public int getHilos() {
        return hilos;
    }

    public boolean llmConfigurado() {
        return servicio.llmConfigurado();
    }

    /** Modelo configurado, para proponerlo en el diálogo de la clave. */
    public String getModeloPorDefecto() {
        return servicio.getModeloPorDefecto();
    }

    /** Encola una generación IA. Si ya hay una igual en curso, devuelve esa. */
    public synchronized TrabajoIADTO iniciar(Long escuelaId, Long semestreId, Long turnoId, String modo,
                                             Integer intentos, Integer segundosPorIntento,
                                             Integer maxPasos, String apiKey, String url, String modelo,
                                             Boolean asignarMaestros, Boolean asignarAulas,
                                             String solicitadoPor) {
        Optional<Trabajo> enCurso = trabajos.values().stream()
                .filter(t -> EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado))
                .filter(t -> Objects.equals(t.escuelaId, escuelaId)
                        && Objects.equals(t.semestreId, semestreId)
                        && Objects.equals(t.turnoId, turnoId))
                .findFirst();
        if (enCurso.isPresent()) {
            logger.info("Ya hay una generación IA en curso para escuela={} semestre={} turno={}: {}",
                    escuelaId, semestreId, turnoId, enCurso.get().id);
            return aDTO(enCurso.get());
        }

        // El asesor se crea AQUÍ: si el modo es LLM y no hay clave válida, la petición falla con un
        // mensaje claro antes de encolar nada.
        AsesorIA asesor = servicio.asesor(modo, apiKey, url, modelo);

        limpiarAntiguos();
        Trabajo trabajo = new Trabajo(escuelaId, semestreId, turnoId,
                modo == null || modo.isBlank() ? "heuristica" : modo.trim(),
                intentos == null || intentos <= 0 ? intentosPorDefecto : Math.min(intentos, 50),
                segundosPorIntento == null || segundosPorIntento <= 0
                        ? segundosPorIntentoDefecto : Math.min(segundosPorIntento, 1800),
                maxPasos == null || maxPasos <= 0 ? maxPasosDefecto : Math.min(maxPasos, 20_000_000),
                Boolean.TRUE.equals(asignarMaestros),
                Boolean.TRUE.equals(asignarAulas),
                solicitadoPor);
        trabajo.asesor = asesor;
        trabajos.put(trabajo.id, trabajo);
        logger.info("Trabajo IA {} encolado (semestre={}, turno={}, modo={}, intentos={}, {}s por intento)",
                trabajo.id, semestreId, turnoId, trabajo.modo, trabajo.intentosPlaneados,
                trabajo.segundosPorIntento);

        coordinadores.submit(() -> ejecutar(trabajo));
        return aDTO(trabajo);
    }

    public Optional<TrabajoIADTO> consultar(String trabajoId) {
        return Optional.ofNullable(trabajos.get(trabajoId)).map(this::aDTO);
    }

    /** Un intento concreto, completo (con sus filas) para poder registrarlo. */
    public IntentoIA intento(String trabajoId, int numero) {
        Trabajo t = trabajos.get(trabajoId);
        if (t == null) {
            throw new NegocioExcepcion("trabajo_no_encontrado",
                    "El trabajo de generación IA ya no existe. Vuelve a generarlo.");
        }
        IntentoIA intento = t.intentos.get(numero);
        if (intento == null) {
            throw new NegocioExcepcion("intento_no_encontrado",
                    "El intento " + numero + " no existe o aún no ha terminado.");
        }
        return intento;
    }

    /**
     * Termina la generación: no se lanzan más intentos y se conserva lo ya hecho. El intento en curso
     * acaba (tiene su propio tope de tiempo) y queda disponible como cualquier otro.
     */
    public Optional<TrabajoIADTO> terminar(String trabajoId) {
        Trabajo t = trabajos.get(trabajoId);
        if (t == null) {
            return Optional.empty();
        }
        if (EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado)) {
            t.cancelado = true;
            t.mensaje = "Terminando: no se lanzan más intentos y se conserva el mejor";
            logger.info("Trabajo IA {} marcado para terminar", t.id);
        }
        return Optional.of(aDTO(t));
    }

    /** Registra el intento indicado en el horario real. */
    public TrabajoIADTO registrar(String trabajoId, int numero) {
        Trabajo t = trabajos.get(trabajoId);
        if (t == null) {
            throw new NegocioExcepcion("trabajo_no_encontrado",
                    "El trabajo de generación IA ya no existe. Vuelve a generarlo.");
        }
        if (EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado)) {
            throw new NegocioExcepcion("generacion_en_curso",
                    "La generación sigue en marcha: espera a que termine el intento o pulsa Terminar.");
        }
        IntentoIA intento = intento(trabajoId, numero);

        HorarioIAServicio.RegistroIA registro =
                servicio.registrar(t.semestreId, t.turnoId, intento);

        t.registrado = numero;
        t.registradoEn = LocalDateTime.now();
        t.filasRegistradas = registro.filas();
        t.horasRegistradas = registro.horas();
        logger.info("Trabajo IA {}: intento {} registrado ({} filas, {} h)",
                t.id, numero, registro.filas(), registro.horas());
        return aDTO(t);
    }

    /** Intento con mejor resultado del trabajo (el que se ofrecerá por defecto para registrar). */
    public IntentoIA mejor(String trabajoId) {
        Trabajo t = trabajos.get(trabajoId);
        if (t == null || t.intentos.isEmpty()) {
            return null;
        }
        Integer numero = t.mejorNumero;
        if (numero != null && t.intentos.get(numero) != null) {
            return t.intentos.get(numero);
        }
        return servicio.mejor(new ArrayList<>(t.intentos.values()));
    }

    // ───────── worker ─────────

    private void ejecutar(Trabajo trabajo) {
        trabajo.estado = EN_PROCESO;
        trabajo.iniciadoEn = LocalDateTime.now();
        trabajo.mensaje = "Preparando los datos";

        try {
            // El hilo del worker no hereda el ThreadLocal del hilo HTTP.
            EscuelaContexto.setEscuelaId(trabajo.escuelaId);

            DatosIA datos = servicio.cargarDatos(trabajo.semestreId, trabajo.turnoId);
            trabajo.horasDemandadas = datos.asignaciones().stream()
                    .mapToInt(a -> a.getHoras() == null ? 0 : a.getHoras())
                    .sum();

            trabajo.mensaje = "Pre-validando la información";
            ValidacionIADTO validacion = servicio.validar(trabajo.escuelaId, trabajo.semestreId,
                    trabajo.turnoId, datos);
            trabajo.validacion = validacion;
            if (!validacion.aptoParaGenerar()) {
                logger.warn("Trabajo IA {}: la pre-validación encontró errores, se genera igualmente",
                        trabajo.id);
            }

            AsesorIA asesor = trabajo.asesor != null ? trabajo.asesor : servicio.asesor(trabajo.modo);

            List<IntentoIA> hechos = new ArrayList<>();
            int lanzados = 0;
            while (lanzados < trabajo.intentosPlaneados) {
                if (trabajo.cancelado) {
                    logger.info("Trabajo IA {}: terminado por el usuario tras {} intento(s)",
                            trabajo.id, lanzados);
                    break;
                }

                // Tamaño de esta tanda: los hilos del pool repartidos entre los trabajos que están
                // compitiendo AHORA MISMO. Una escuela sola se lleva los 3 hilos; tres escuelas, uno
                // cada una. Así todas avanzan, en vez de que la última espere a que las otras acaben.
                int tanda = Math.max(1, hilos / trabajosEnProceso());
                int hasta = Math.min(lanzados + tanda, trabajo.intentosPlaneados);

                List<Future<IntentoIA>> futuros = new ArrayList<>();
                for (int n = lanzados + 1; n <= hasta; n++) {
                    final int numero = n;
                    futuros.add(cocineros.submit(() -> intentoDe(trabajo, datos, numero, asesor)));
                }
                trabajo.intentoActual = hasta;
                trabajo.mensaje = tanda == 1
                        ? "Intento " + hasta + " de " + trabajo.intentosPlaneados + " en marcha"
                        : "Intentos " + (lanzados + 1) + "-" + hasta + " de "
                                + trabajo.intentosPlaneados + " en marcha";

                // NO hay corte anticipado: se lanzan SIEMPRE los intentos planeados y al final se elige
                // el mejor. Antes se paraba en cuanto un intento colocaba todas las horas, y eso dejaba
                // la busqueda a medias con huecos, arranques tarde y adyacencias encima de la mesa
                // (medido: 183/188 materias, 20 de castigo de huecos, 9 adyacencias, medium -111, y con
                // las horas TODAS colocadas). Decidir "esto ya es suficientemente bueno" a mitad de
                // camino es justo lo que impide encontrar algo mejor, y ninguna condicion intermedia
                // acierta: lo que parece bueno en el intento 2 puede quedar tercero en el 6.
                // El unico corte es el del usuario, con el boton Terminar.
                for (Future<IntentoIA> futuro : futuros) {
                    IntentoIA intento = recoger(futuro, trabajo);
                    if (intento == null) {
                        continue;   // cancelado o falló: los demás de la tanda siguen valiendo
                    }
                    trabajo.intentos.put(intento.getNumero(), intento);
                    hechos.add(intento);

                    IntentoIA mejor = servicio.mejor(hechos);
                    trabajo.mejorNumero = mejor != null ? mejor.getNumero() : null;

                    trabajo.mensaje = "Intento " + intento.getNumero() + " listo: "
                            + intento.getHoras() + "/" + intento.getHorasDemandadas() + " h";
                    logger.info("Trabajo IA {} intento {}: {}/{} h, {} pendientes, {} problemas, medium {}",
                            trabajo.id, intento.getNumero(), intento.getHoras(),
                            intento.getHorasDemandadas(), intento.getPendientes().size(),
                            intento.getProblemas().size(), intento.getMedium());
                }

                lanzados = hasta;
            }

            if (trabajo.intentos.isEmpty()) {
                trabajo.estado = ERROR;
                trabajo.error = "No se completó ningún intento.";
                trabajo.mensaje = "La generación no produjo ningún intento";
            } else {
                trabajo.estado = COMPLETADO;
                if (trabajo.cancelado) {
                    trabajo.mensaje = "Terminado por el usuario con " + trabajo.intentos.size()
                            + " intento(s)";
                } else {
                    IntentoIA mejor = servicio.mejor(new ArrayList<>(trabajo.intentos.values()));
                    // Se lanzaron TODOS los intentos planeados (ya no hay corte anticipado): el mensaje
                    // resume el mejor de todos, con su medium, que es la estadistica que se compara.
                    trabajo.mensaje = "Listo: " + trabajo.intentos.size() + " intento(s), el mejor coloca "
                            + (mejor != null ? mejor.getHoras() : 0) + " h"
                            + (mejor != null ? " (medium " + mejor.getMedium() + ")" : "")
                            + (mejor != null && !mejor.getPendientes().isEmpty()
                            ? " y deja " + mejor.getPendientes().size() + " pendientes" : "");
                }
            }

        } catch (NegocioExcepcion e) {
            trabajo.estado = ERROR;
            trabajo.mensaje = "La generación IA no pudo completarse";
            trabajo.error = e.getMensajeCrudo();
            logger.warn("Trabajo IA {} terminó con error de negocio: {}", trabajo.id, e.getMensajeCrudo());
        } catch (Exception e) {
            trabajo.estado = ERROR;
            trabajo.mensaje = "La generación IA no pudo completarse";
            trabajo.error = "Error inesperado durante la generación: " + e.getClass().getSimpleName();
            logger.error("Trabajo IA {} falló", trabajo.id, e);
        } finally {
            EscuelaContexto.limpiar();
            // La clave de esta generación se descarta aquí: el asesor (y con él la clave) deja de
            // existir en cuanto el trabajo termina.
            trabajo.asesor = null;
            trabajo.finalizadoEn = LocalDateTime.now();
        }
    }

    /**
     * Un intento, ya dentro de un hilo del pool de {@code cocineros}.
     *
     * <p>Un hilo del pool NO hereda el {@code ThreadLocal} del hilo HTTP ni el del coordinador, así
     * que hay que fijar aquí la escuela a mano; y se limpia al salir para no dejar el dato pegado a
     * un hilo que después atenderá a otra escuela.
     */
    private IntentoIA intentoDe(Trabajo trabajo, DatosIA datos, int numero, AsesorIA asesor) {
        EscuelaContexto.setEscuelaId(trabajo.escuelaId);
        try {
            if (trabajo.cancelado) {
                return null;
            }
            return servicio.intento(datos, numero, System.nanoTime(), trabajo.segundosPorIntento,
                    trabajo.maxPasos, trabajo.asignarMaestros, trabajo.asignarAulas, asesor,
                    linea -> logger.debug("IA[{}] {}", trabajo.id, linea));
        } finally {
            EscuelaContexto.limpiar();
        }
    }

    /**
     * Recoge el resultado de un intento sin que un fallo suelto tumbe el trabajo entero: si uno de
     * los seis falla, los otros cinco siguen valiendo. Si fallan todos, el trabajo termina en ERROR
     * por la comprobación de {@code trabajo.intentos.isEmpty()}, así que nada se pierde en silencio.
     */
    private IntentoIA recoger(Future<IntentoIA> futuro, Trabajo trabajo) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            // Están apagando la aplicación: se recupera la marca y se deja de lanzar trabajo.
            Thread.currentThread().interrupt();
            trabajo.cancelado = true;
            return null;
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            logger.warn("Trabajo IA {}: un intento falló y se descarta: {}",
                    trabajo.id, causa.toString());
            return null;
        }
    }

    /** Cuántos trabajos están consumiendo CPU ahora mismo (incluido el que pregunta). */
    private int trabajosEnProceso() {
        int n = 0;
        for (Trabajo t : trabajos.values()) {
            if (EN_PROCESO.equals(t.estado)) {
                n++;
            }
        }
        return Math.max(1, n);
    }

    private void limpiarAntiguos() {
        LocalDateTime limite = LocalDateTime.now().minus(RETENCION);
        trabajos.values().removeIf(t -> t.finalizadoEn != null && t.finalizadoEn.isBefore(limite));
        if (trabajos.size() <= MAX_TRABAJOS) {
            return;
        }
        trabajos.values().stream()
                .filter(t -> t.finalizadoEn != null)
                .sorted(Comparator.comparing(t -> t.finalizadoEn))
                .limit(trabajos.size() - MAX_TRABAJOS)
                .map(t -> t.id)
                .toList()
                .forEach(trabajos::remove);
    }

    private TrabajoIADTO aDTO(Trabajo t) {
        TrabajoIADTO dto = new TrabajoIADTO();
        dto.setId(t.id);
        dto.setEstado(t.estado);
        dto.setMensaje(t.mensaje);
        dto.setModo(t.modo);
        dto.setEncoladoEn(t.encoladoEn);
        dto.setIniciadoEn(t.iniciadoEn);
        dto.setFinalizadoEn(t.finalizadoEn);
        dto.setSegundosPorIntento((long) t.segundosPorIntento);
        dto.setSemestreId(t.semestreId);
        dto.setTurnoId(t.turnoId);
        dto.setSolicitadoPor(t.solicitadoPor);
        dto.setIntentosPlaneados(t.intentosPlaneados);
        dto.setIntentoActual(t.intentoActual);
        dto.setHorasDemandadas(t.horasDemandadas);
        // Las banderas viajan al cliente para que, al guardar la corrida, queden registradas con ella.
        dto.setAsignarMaestros(t.asignarMaestros);
        dto.setAsignarAulas(t.asignarAulas);
        dto.setMejorNumero(t.mejorNumero);
        dto.setRegistrado(t.registrado);
        dto.setRegistradoEn(t.registradoEn);
        dto.setFilasRegistradas(t.filasRegistradas);
        dto.setHorasRegistradas(t.horasRegistradas);
        dto.setTerminadoPorUsuario(t.cancelado);
        dto.setError(t.error);
        dto.setValidacion(t.validacion);

        LocalDateTime inicio = t.iniciadoEn != null ? t.iniciadoEn : t.encoladoEn;
        LocalDateTime fin = t.finalizadoEn != null ? t.finalizadoEn : LocalDateTime.now();
        dto.setSegundosTranscurridos(Duration.between(inicio, fin).getSeconds());

        // Del más nuevo al más viejo: lo que el usuario quiere ver primero es lo último.
        Map<Integer, IntentoIA> ordenados = new LinkedHashMap<>();
        t.intentos.keySet().stream().sorted(Comparator.reverseOrder())
                .forEach(n -> ordenados.put(n, t.intentos.get(n)));
        dto.setIntentos(new ArrayList<>(ordenados.values()));
        return dto;
    }

    @PreDestroy
    public void cerrar() {
        coordinadores.shutdownNow();
        cocineros.shutdownNow();
        logger.info("Generador IA detenido");
    }
}
