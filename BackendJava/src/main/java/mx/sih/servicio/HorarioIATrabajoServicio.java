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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ejecuta el GENERADOR IA en segundo plano: varios intentos, uno detrás de otro.
 *
 * <p>Cada intento es independiente y parte de cero (no hereda el anterior): así se exploran
 * soluciones distintas y el mejor se queda. El usuario puede ver cómo avanza cada intento con sus
 * horas pendientes, y puede terminar cuando alguno le sirva.
 *
 * <p>Igual que la generación con Timefold, se usa UN solo hilo: dos generaciones a la vez competirían
 * por la CPU y escribirían en las mismas tablas. Si hay un trabajo en curso con el mismo alcance, se
 * devuelve ese.
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
                        boolean asignarMaestros, String solicitadoPor) {
            this.escuelaId = escuelaId;
            this.semestreId = semestreId;
            this.turnoId = turnoId;
            this.modo = modo;
            this.intentosPlaneados = intentosPlaneados;
            this.segundosPorIntento = segundosPorIntento;
            this.maxPasos = maxPasos;
            this.asignarMaestros = asignarMaestros;
            this.solicitadoPor = solicitadoPor;
        }
    }

    private final Map<String, Trabajo> trabajos = new ConcurrentHashMap<>();
    private final ExecutorService ejecutor;
    private final HorarioIAServicio servicio;

    /** Valores por defecto, configurables. */
    private final int intentosPorDefecto;
    private final int segundosPorIntentoDefecto;
    private final int maxPasosDefecto;

    public HorarioIATrabajoServicio(HorarioIAServicio servicio,
                                    @Value("${app.ia.intentos:6}") int intentosPorDefecto,
                                    @Value("${app.ia.segundos-por-intento:60}") int segundosPorIntentoDefecto,
                                    @Value("${app.ia.max-pasos:60000}") int maxPasosDefecto) {
        this.servicio = servicio;
        this.intentosPorDefecto = intentosPorDefecto <= 0 ? 6 : intentosPorDefecto;
        this.segundosPorIntentoDefecto = segundosPorIntentoDefecto <= 0 ? 60 : segundosPorIntentoDefecto;
        this.maxPasosDefecto = maxPasosDefecto <= 0 ? 60000 : maxPasosDefecto;
        this.ejecutor = Executors.newSingleThreadExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "generacion-horarios-ia");
            hilo.setDaemon(true);
            return hilo;
        });
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
                                             Boolean asignarMaestros, String solicitadoPor) {
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
                maxPasos == null || maxPasos <= 0 ? maxPasosDefecto : Math.min(maxPasos, 2_000_000),
                Boolean.TRUE.equals(asignarMaestros),
                solicitadoPor);
        trabajo.asesor = asesor;
        trabajos.put(trabajo.id, trabajo);
        logger.info("Trabajo IA {} encolado (semestre={}, turno={}, modo={}, intentos={}, {}s por intento)",
                trabajo.id, semestreId, turnoId, trabajo.modo, trabajo.intentosPlaneados,
                trabajo.segundosPorIntento);

        ejecutor.submit(() -> ejecutar(trabajo));
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
            ValidacionIADTO validacion = servicio.validar(trabajo.semestreId, trabajo.turnoId, datos);
            trabajo.validacion = validacion;
            if (!validacion.aptoParaGenerar()) {
                logger.warn("Trabajo IA {}: la pre-validación encontró errores, se genera igualmente",
                        trabajo.id);
            }

            AsesorIA asesor = trabajo.asesor != null ? trabajo.asesor : servicio.asesor(trabajo.modo);

            List<IntentoIA> hechos = new ArrayList<>();
            for (int i = 1; i <= trabajo.intentosPlaneados; i++) {
                if (trabajo.cancelado) {
                    logger.info("Trabajo IA {}: terminado por el usuario antes del intento {}", trabajo.id, i);
                    break;
                }
                trabajo.intentoActual = i;
                trabajo.mensaje = "Intento " + i + " de " + trabajo.intentosPlaneados;

                IntentoIA intento = servicio.intento(datos, i, System.nanoTime(), 
                        trabajo.segundosPorIntento, trabajo.maxPasos, trabajo.asignarMaestros, asesor,
                        linea -> logger.debug("IA[{}] {}", trabajo.id, linea));

                trabajo.intentos.put(i, intento);
                hechos.add(intento);

                IntentoIA mejor = servicio.mejor(hechos);
                trabajo.mejorNumero = mejor != null ? mejor.getNumero() : null;

                trabajo.mensaje = "Intento " + i + " listo: " + intento.getHoras() + "/"
                        + intento.getHorasDemandadas() + " h";
                logger.info("Trabajo IA {} intento {}: {}/{} h, {} pendientes, {} problemas",
                        trabajo.id, i, intento.getHoras(), intento.getHorasDemandadas(),
                        intento.getPendientes().size(), intento.getProblemas().size());

                // Si un intento coloca todo y sin problemas, no hay nada mejor que buscar.
                if (intento.getProblemas().isEmpty()
                        && intento.getHoras() >= intento.getHorasDemandadas()) {
                    trabajo.mensaje = "Intento " + i + " colocó todas las horas";
                    break;
                }
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
                    trabajo.mensaje = "Listo: " + trabajo.intentos.size() + " intento(s), el mejor coloca "
                            + (mejor != null ? mejor.getHoras() : 0) + " h"
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
        ejecutor.shutdownNow();
        logger.info("Ejecutor del generador IA detenido");
    }
}
