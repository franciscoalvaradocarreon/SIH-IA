package mx.sih.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.url}")
    private String uploadUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Calcular la ruta absoluta desde la relativa
        String rutaAbsoluta = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        if (!rutaAbsoluta.endsWith("/")) {
            rutaAbsoluta = rutaAbsoluta + "/";
        }

        String uploadUrlPath = uploadUrl.startsWith("/") ? uploadUrl : "/" + uploadUrl;
        if (!uploadUrlPath.endsWith("/")) {
            uploadUrlPath = uploadUrlPath + "/";
        }

        registry.addResourceHandler(uploadUrlPath + "**")
                .addResourceLocations("file:" + rutaAbsoluta);

        // ── FRONT REACT EMBEBIDO + fallback de SPA ──
        // El front compilado vive en classpath:/static (lo copia el script de publicación). Cualquier
        // ruta que no sea la API, ni /uploads, ni un archivo con extensión (assets, favicon…), devuelve
        // index.html: así recargar en /reportes/algo funciona en vez de dar 404. Los controladores
        // tienen prioridad sobre este handler y /uploads/** es más específico, así que no estorba.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource pedido = location.createRelative(resourcePath);
                        if (pedido.exists() && pedido.isReadable()) {
                            return pedido;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("uploads/")
                                || resourcePath.contains(".")) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}