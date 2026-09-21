package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.HorarioSolucionMasivaDTO;
import mx.sih.modelo.dto.TrabajoGeneracionDTO;
import mx.sih.modelo.solver.HorarioSolverService;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ejecuta la generación masiva de horarios EN SEGUNDO PLANO.
 *
 * <h2>Por qué</h2>
 * El solver tarda hasta 300 s. Antes, {@code POST /api/horarios/generar-todos} se
 * quedaba bloqueado todo ese tiempo en el hilo HTTP: el navegador (o cualquier proxy)
 * podía cortar la conexión, no había forma de mostrar progreso y el usuario no podía
 * hacer nada mientras tanto. Ahora la petición responde {@code 202} con un id y el
 * cliente consulta el estado.
 *
 * <h2>Hilo único a propósito</h2>
 * Se usa un solo hilo de trabajo: dos generaciones simultáneas competirían por la CPU
 * (el solver usa un núcleo al 100 %) y, peor, escribirían a la vez en las mismas tablas
 * de horarios. Si ya hay una generación en curso para la misma escuela/semestre/turno,
 * se devuelve ESA en lugar de encolar otra (también resuelve el doble clic en el botón).
 *
 * <h2>Contexto de escuela</h2>
 * {@link EscuelaContexto} es un ThreadLocal: el hilo del worker NO lo hereda del hilo
 * HTTP. Por eso el trabajo guarda la escuela y el worker la fija antes de delegar (y la
 * limpia en {@code finally}). Es exactamente el punto que el diseño original marcaba
 * como pendiente al mover el solver fuera del hilo de la petición.
 *
 * <h2>Limitación conocida</h2>
 * Los trabajos viven en memoria: si se reinicia el backend, se pierden (y la generación
 * en curso). Para producción con varias instancias habría que persistirlos y coordinarlos
 * (BD o Redis) y sacar el solver a un proceso aparte.
 */
@Service
public class GeneracionHorarioTrabajoServicio {

    private static final Logger logger = LoggerFactory.getLogger(GeneracionHorarioTrabajoServicio.class);

    public static final String EN_COLA = "EN_COLA";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String ERROR = "ERROR";

    /** Cuántos trabajos terminados se conservan para poder consultarlos. */
    private static final int MAX_TRABAJOS = 40;

    /** Antigüedad a partir de la cual un trabajo terminado se descarta. */
    private static final Duration RETENCION = Duration.ofHours(2);

    private static final class Trabajo {
        private final String id = UUID.randomUUID().toString();
        private final Long escuelaId;
        private final Long semestreId;
        private final Long turnoId;
        private final String solicitadoPor;
        private final LocalDateTime encoladoEn = LocalDateTime.now();

        // volatile: los escribe el hilo del worker y los lee el hilo HTTP
        private volatile String estado = EN_COLA;
        private volatile String mensaje = "En cola";
        private volatile LocalDateTime iniciadoEn;
        private volatile LocalDateTime finalizadoEn;
        private volatile HorarioSolucionMasivaDTO resultado;
        private volatile String error;

        /** true cuando el usuario pidió terminar antes de agotar los intentos. */
        private volatile boolean cancelado;

        private Trabajo(Long escuelaId, Long semestreId, Long turnoId, String solicitadoPor) {
            this.escuelaId = escuelaId;
            this.semestreId = semestreId;
            this.turnoId = turnoId;
            this.solicitadoPor = solicitadoPor;
        }
    }

    private final Map<String, Trabajo> trabajos = new ConcurrentHashMap<>();
    private final ExecutorService ejecutor;
    private final HorarioServicio horarioServicio;

    /** Para publicar el presupuesto total y poder terminar la corrida en curso. */
    private final HorarioSolverService solverService;

    public GeneracionHorarioTrabajoServicio(
            HorarioServicio horarioServicio,
            HorarioSolverService solverService) {
        this.horarioServicio = horarioServicio;
        this.solverService = solverService;
        this.ejecutor = Executors.newSingleThreadExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "generacion-horarios");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    /**
     * Encola una generación masiva. Si ya hay una en curso con el mismo alcance,
     * devuelve esa (idempotente ante dobles clics).
     */
    public synchronized TrabajoGeneracionDTO iniciar(Long escuelaId, Long semestreId,
                                                     Long turnoId, String solicitadoPor) {
        Optional<Trabajo> enCurso = trabajos.values().stream()
                .filter(t -> EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado))
                .filter(t -> Objects.equals(t.escuelaId, escuelaId)
                        && Objects.equals(t.semestreId, semestreId)
                        && Objects.equals(t.turnoId, turnoId))
                .findFirst();

        if (enCurso.isPresent()) {
            logger.info("Ya hay una generación en curso para escuela={} semestre={} turno={}; "
                    + "se reutiliza el trabajo {}", escuelaId, semestreId, turnoId, enCurso.get().id);
            return aDTO(enCurso.get());
        }

        limpiarAntiguos();

        Trabajo trabajo = new Trabajo(escuelaId, semestreId, turnoId, solicitadoPor);
        trabajos.put(trabajo.id, trabajo);
        logger.info("Trabajo de generación {} encolado (escuela={}, semestre={}, turno={}, usuario={})",
                trabajo.id, escuelaId, semestreId, turnoId, solicitadoPor);

        ejecutor.submit(() -> ejecutar(trabajo));
        return aDTO(trabajo);
    }

    public Optional<TrabajoGeneracionDTO> consultar(String trabajoId) {
        return Optional.ofNullable(trabajos.get(trabajoId)).map(this::aDTO);
    }

    /**
     * Termina la generación en curso quedándose con la MEJOR solución ya encontrada.
     *
     * <p>El bucle de intentos del solver mira la bandera de cancelación: deja de lanzar corridas
     * nuevas, la que está en curso devuelve lo mejor que tenga hasta ese momento, y HorarioServicio
     * persiste esa solución como si la generación hubiera terminado normalmente. Así el usuario
     * puede cortar una generación larga en cuanto vea un resultado que le sirva, sin perderlo.
     */
    public Optional<TrabajoGeneracionDTO> terminar(String trabajoId) {
        Trabajo t = trabajos.get(trabajoId);
        if (t == null) {
            return Optional.empty();
        }
        if (EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado)) {
            t.cancelado = true;
            t.mensaje = "Terminando: se guardará la mejor solución encontrada";
            boolean habiaCorrida = solverService.cancelarGeneracionEnCurso();
            logger.info("Trabajo {} marcado para terminar (¿corrida en curso? {})", t.id, habiaCorrida);
        }
        return Optional.of(aDTO(t));
    }

    /** Trabajos en curso (para diagnóstico). */
    public List<TrabajoGeneracionDTO> listarEnCurso() {
        return trabajos.values().stream()
                .filter(t -> EN_COLA.equals(t.estado) || EN_PROCESO.equals(t.estado))
                .map(this::aDTO)
                .toList();
    }

    private void ejecutar(Trabajo trabajo) {
        trabajo.estado = EN_PROCESO;
        trabajo.iniciadoEn = LocalDateTime.now();
        trabajo.mensaje = "Resolviendo el horario";
        logger.info("Trabajo {} EN_PROCESO en el hilo {}", trabajo.id, Thread.currentThread().getName());

        try {
            // El hilo del worker no hereda el ThreadLocal del hilo HTTP
            EscuelaContexto.setEscuelaId(trabajo.escuelaId);

            HorarioSolucionMasivaDTO resultado =
                    horarioServicio.generarTodos(trabajo.semestreId, trabajo.turnoId);

            trabajo.resultado = resultado;
            trabajo.estado = COMPLETADO;
            if (trabajo.cancelado) {
                trabajo.mensaje = "Terminado por el usuario: se guardó la mejor solución encontrada";
            } else {
                trabajo.mensaje = (resultado != null && Boolean.TRUE.equals(resultado.getFactible()))
                        ? "Generación completada"
                        : "Generación terminada sin horario factible";
            }
            logger.info("Trabajo {} COMPLETADO", trabajo.id);

        } catch (NegocioExcepcion e) {
            trabajo.estado = ERROR;
            trabajo.mensaje = "La generación no pudo completarse";
            trabajo.error = e.getMensajeCrudo();
            logger.warn("Trabajo {} terminó con error de negocio: {}", trabajo.id, e.getMensajeCrudo());

        } catch (Exception e) {
            trabajo.estado = ERROR;
            trabajo.mensaje = "La generación no pudo completarse";
            trabajo.error = "Error inesperado durante la generación del horario";
            logger.error("Trabajo {} falló", trabajo.id, e);

        } finally {
            EscuelaContexto.limpiar();
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

    private TrabajoGeneracionDTO aDTO(Trabajo t) {
        TrabajoGeneracionDTO dto = new TrabajoGeneracionDTO();
        dto.setId(t.id);
        dto.setEstado(t.estado);
        dto.setMensaje(t.mensaje);
        dto.setEncoladoEn(t.encoladoEn);
        dto.setIniciadoEn(t.iniciadoEn);
        dto.setFinalizadoEn(t.finalizadoEn);
        dto.setSemestreId(t.semestreId);
        dto.setTurnoId(t.turnoId);
        dto.setSolicitadoPor(t.solicitadoPor);
        dto.setError(t.error);
        dto.setResultado(t.resultado);

        LocalDateTime inicio = t.iniciadoEn != null ? t.iniciadoEn : t.encoladoEn;
        LocalDateTime fin = t.finalizadoEn != null ? t.finalizadoEn : LocalDateTime.now();
        dto.setSegundosTranscurridos(Duration.between(inicio, fin).getSeconds());
        dto.setLimiteSegundos(solverService.getLimiteSegundosMasiva());
        return dto;
    }

    @PreDestroy
    public void cerrar() {
        ejecutor.shutdownNow();
        logger.info("Ejecutor de generación de horarios detenido");
    }
}
