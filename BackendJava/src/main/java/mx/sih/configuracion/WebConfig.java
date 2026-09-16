package mx.sih.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    }
}