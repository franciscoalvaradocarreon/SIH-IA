package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de archivos del SIH.
 *
 * CAMBIOS CRÍTICOS respecto a la versión anterior:
 *  1. El tipo de archivo se decide por los BYTES REALES (magic bytes/decodificación),
 *     nunca por el Content-Type ni por la extensión que envía el cliente.
 *  2. El nombre en disco lo genera ÍNTEGRAMENTE el servidor: el cliente no aporta
 *     ni la extensión (antes `filename="x./../../evil.html"` permitía escribir fuera
 *     de uploads/ — path traversal).
 *  3. La imagen se RE-CODIFICA: se descarta el archivo original y se escribe una
 *     imagen nueva, lo que destruye cualquier carga útil incrustada (polyglot) y
 *     cierra el XSS almacenado servido desde /uploads/**.
 *  4. Se comprueba que el destino siga dentro del directorio permitido (normalize +
 *     startsWith) tanto al guardar como al borrar.
 *  5. Límite de píxeles para evitar "bombas de descompresión".
 *
 * NOTA: SVG queda prohibido a propósito: es un formato que ejecuta scripts.
 */
@Service
public class ArchivoServicio {

    /** Formatos aceptados (los que devuelve ImageIO al leer los bytes reales). */
    private static final Set<String> FORMATOS_PERMITIDOS = Set.of("jpeg", "jpg", "png", "gif");

    /** Formato detectado -> extensión con la que se guarda en disco. */
    private static final Map<String, String> EXTENSION_POR_FORMATO = Map.of(
            "jpeg", "jpg",
            "jpg", "jpg",
            "png", "png",
            "gif", "gif");

    /** Máximo de píxeles (ancho * alto) para evitar consumo desmedido de memoria. */
    private static final long MAX_PIXELES = 30_000_000L;

    /** Máximo de bytes del archivo recibido. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.url}")
    private String uploadUrl;

    /**
     * Guarda una imagen y devuelve su URL pública.
     * Lanza NegocioExcepcion si el archivo no es una imagen permitida.
     */
    public String guardarArchivo(MultipartFile archivo, String prefijo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }

        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioExcepcion("archivo_muy_grande",
                    "El archivo no puede exceder los 5MB");
        }

        byte[] contenido = archivo.getBytes();

        // 1) Formato REAL, leído de los bytes (no del Content-Type ni del nombre)
        String formato = detectarFormato(contenido);
        if (formato == null || !FORMATOS_PERMITIDOS.contains(formato)) {
            throw new NegocioExcepcion("formato_no_permitido",
                    "Solo se permiten imágenes JPG, PNG o GIF");
        }

        // 2) Decodificar y validar dimensiones (descarta archivos corruptos o maliciosos)
        BufferedImage imagen = leerYValidar(contenido);

        // 3) Nombre generado solo por el servidor
        String extension = EXTENSION_POR_FORMATO.get(formato);
        String prefijoLimpio = (prefijo == null || prefijo.isBlank()) ? "archivo_" : prefijo;
        String nombreArchivo = prefijoLimpio
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension;

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path destino = base.resolve(nombreArchivo).normalize();

        // 4) El destino debe seguir dentro de uploads/ (path traversal)
        if (!destino.startsWith(base)) {
            throw new NegocioExcepcion("ruta_invalida", "Ruta de destino no permitida");
        }

        Files.createDirectories(base);
        escribirImagen(imagen, destino, extension);

        String urlBase = uploadUrl.startsWith("/") ? uploadUrl : "/" + uploadUrl;
        if (!urlBase.endsWith("/")) {
            urlBase = urlBase + "/";
        }
        return urlBase + nombreArchivo;
    }

    /** Elimina el archivo asociado a una URL pública. Devuelve false si no existe o la URL no es válida. */
    public boolean eliminarArchivo(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            String nombreArchivo = url.substring(url.lastIndexOf("/") + 1);
            if (nombreArchivo.isBlank() || nombreArchivo.contains("..")) {
                return false;
            }
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path destino = base.resolve(nombreArchivo).normalize();
            if (!destino.startsWith(base)) {
                return false;
            }
            return Files.deleteIfExists(destino);
        } catch (IOException e) {
            return false;
        }
    }

    // ============================================================
    // Internos
    // ============================================================

    /** Lee el formato real del archivo a partir de sus bytes. */
    private String detectarFormato(byte[] contenido) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            if (entrada == null) {
                return null;
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                return lector.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                lector.dispose();
            }
        }
    }

    /** Decodifica la imagen y valida sus dimensiones antes de materializarla en memoria. */
    private BufferedImage leerYValidar(byte[] contenido) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            if (entrada == null) {
                throw new NegocioExcepcion("formato_no_permitido", "El archivo no es una imagen válida");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new NegocioExcepcion("formato_no_permitido", "El archivo no es una imagen válida");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                long ancho = lector.getWidth(0);
                long alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || ancho * alto > MAX_PIXELES) {
                    throw new NegocioExcepcion("imagen_demasiado_grande",
                            "Las dimensiones de la imagen no están permitidas");
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null) {
                    throw new NegocioExcepcion("formato_no_permitido", "El archivo no es una imagen válida");
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        }
    }

    /** Escribe la imagen RE-CODIFICADA en disco (no se copian los bytes originales). */
    private void escribirImagen(BufferedImage imagen, Path destino, String extension) throws IOException {
        BufferedImage salida = imagen;
        String formatoSalida = extension;

        if ("jpg".equals(extension)) {
            formatoSalida = "jpeg";
            // JPEG no admite canal alfa: se aplana sobre blanco.
            if (imagen.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.drawImage(imagen, 0, 0, null);
                } finally {
                    g.dispose();
                }
                salida = rgb;
            }
        }

        boolean escrito = ImageIO.write(salida, formatoSalida, destino.toFile());
        if (!escrito) {
            throw new NegocioExcepcion("error_guardado", "No se pudo procesar la imagen");
        }
    }
}
