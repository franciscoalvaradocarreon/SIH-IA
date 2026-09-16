package mx.sih.excepcion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Respuestas de error uniformes para toda la API.
 *
 * Todas devuelven la MISMA forma  {"success":false,"error":"codigo","message":"texto"},
 * porque el frontend muestra siempre error.response.data.message. Sin estos manejadores,
 * los errores de validación (@Valid) y de JSON mal formado salían con el cuerpo por
 * defecto de Spring ({timestamp,status,error,path}), que no tiene "message".
 *
 * ATENCIÓN (esto causó un fallo real durante la verificación):
 * un @ExceptionHandler(Exception.class) captura TAMBIÉN las excepciones de autorización
 * que lanza @PreAuthorize (AuthorizationDeniedException extiende AccessDeniedException)
 * y las convertía en 500 en lugar de 403. Por eso hay un manejador explícito que las
 * re-lanza para que las gestione el filtro de seguridad (accessDeniedHandler -> 403).
 * Del mismo modo, sin manejadores específicos, un 404 o un 405 se convertían en 500.
 *
 * Los detalles técnicos van al log (SLF4J), nunca al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Error de negocio controlado: el mensaje SÍ se muestra al usuario (sin el prefijo del código). */
    @ExceptionHandler(NegocioExcepcion.class)
    public ResponseEntity<Map<String, Object>> manejarNegocio(NegocioExcepcion e) {
        logger.warn("NegocioExcepcion [{}]: {}", e.getCodigoError(), e.getMensajeCrudo());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", e.getCodigoError() != null ? e.getCodigoError() : "negocio_error");
        body.put("message", e.getMensajeCrudo());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Acceso denegado por @PreAuthorize.
     * NO se responde aquí: se re-lanza para que lo maneje ExceptionTranslationFilter y
     * devuelva el 403 JSON del accessDeniedHandler (si no, este manejador genérico lo
     * convertiría en un 500 "error interno").
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void manejarAccesoDenegado(AccessDeniedException e) throws AccessDeniedException {
        logger.warn("Acceso denegado: {}", e.getMessage());
        throw e;
    }

    /** Datos de entrada inválidos (@Valid): se enumeran los campos que fallan. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(". "));

        logger.warn("Validación fallida: {}", detalle);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "datos_invalidos");
        body.put("message", detalle.isBlank() ? "Los datos enviados no son válidos" : detalle);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Cuerpo que no es JSON válido o con tipos incompatibles. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoIlegible(HttpMessageNotReadableException e) {
        logger.warn("Cuerpo de petición ilegible: {}", e.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "cuerpo_invalido");
        body.put("message", "El cuerpo de la petición no tiene el formato esperado");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Parámetro de URL o query con tipo incorrecto (por ejemplo, un id no numérico). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoParametro(MethodArgumentTypeMismatchException e) {
        logger.warn("Parámetro con tipo inválido: {} = {}", e.getName(), e.getValue());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "parametro_invalido");
        body.put("message", "El valor del parámetro '" + e.getName() + "' no es válido");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Ruta inexistente: 404 (no 500). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrado(NoResourceFoundException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "recurso_no_encontrado");
        body.put("message", "El recurso solicitado no existe");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** Método HTTP no soportado por el endpoint: 405 (no 500). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodoNoSoportado(HttpRequestMethodNotSupportedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "metodo_no_permitido");
        body.put("message", "El método " + e.getMethod() + " no está permitido en esta dirección");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /** Violación de integridad en base de datos: mensaje genérico, detalle solo en el log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarIntegridad(DataIntegrityViolationException e) {
        logger.error("DataIntegrityViolationException: {}",
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "integridad_datos");
        body.put("message", "No se puede completar la operación porque hay registros relacionados.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Cualquier error no controlado: 500 sin exponer detalles internos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarGenerico(Exception e) {
        logger.error("Error no controlado", e);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "error_interno");
        body.put("message", "Ocurrió un error inesperado. Inténtalo de nuevo o contacta al administrador.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
