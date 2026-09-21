package mx.sih.servicio;

import java.io.IOException;
import java.util.List;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.EscuelaCrearDTO;
import mx.sih.modelo.dto.EscuelaDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.repositorio.EscuelaRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de escuelas.
 *
 * Toda operación se valida contra las escuelas del usuario autenticado
 * (claims.escuelaIds() del JWT, que a su vez se emiten desde usuario_escuela_rol).
 */
@Service
public class EscuelaServicio {

    private final EscuelaRepositorio escuelaRepositorio;
    private final ArchivoServicio archivoServicio;

    public EscuelaServicio(EscuelaRepositorio escuelaRepositorio,
                           ArchivoServicio archivoServicio) {
        this.escuelaRepositorio = escuelaRepositorio;
        this.archivoServicio = archivoServicio;
    }

    /** Claims del usuario autenticado (identidad + escuelas permitidas). */
    private ClaimsUsuario claims() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof ClaimsUsuario claims)) {
            throw new NegocioExcepcion("no_autenticado", "Se requiere autenticación para esta operación");
        }
        return claims;
    }

    /** Falla si el usuario autenticado no tiene acceso a la escuela indicada. */
    private void exigirAcceso(Long escuelaId) {
        if (escuelaId == null || !claims().tieneAccesoA(escuelaId)) {
            throw new NegocioExcepcion("escuela_no_autorizada",
                    "No tienes acceso a la escuela solicitada");
        }
    }

    public Page<EscuelaDTO> listarEscuelas(Pageable pageable, String busqueda) {
        List<Long> ids = claims().escuelaIds();
        if (ids == null || ids.isEmpty()) {
            return Page.empty(pageable);
        }
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        return escuelaRepositorio.buscarPorIds(ids, busquedaNormalizada, pageable).map(this::toDTO);
    }

    public EscuelaDTO obtenerEscuela(Long id) {
        exigirAcceso(id);
        Escuela escuela = escuelaRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada con ID: " + id));
        return toDTO(escuela);
    }

    @Transactional
    public EscuelaDTO crearEscuela(EscuelaCrearDTO dto) {
        // Verificar clave única
        if (dto.getClave() != null && !dto.getClave().isBlank()) {
            if (escuelaRepositorio.existsByClave(dto.getClave())) {
                throw new NegocioExcepcion("Ya existe una escuela con la clave: " + dto.getClave());
            }
        }

        // Guardar logo si viene archivo
        String logoUrl = dto.getLogoUrl();
        if (dto.getLogoArchivo() != null && !dto.getLogoArchivo().isEmpty()) {
            try {
                logoUrl = archivoServicio.guardarArchivo(dto.getLogoArchivo(), "escuela_");
            } catch (IOException e) {
                throw new NegocioExcepcion("error_logo",
                        "Error al guardar el logo: " + e.getMessage());
            }
        }

        Escuela escuela = new Escuela();
        escuela.setNombre(dto.getNombre());
        escuela.setNombreLargo(dto.getNombreLargo());
        escuela.setDireccion(dto.getDireccion());
        escuela.setTelefono(dto.getTelefono());
        escuela.setClave(dto.getClave());
        escuela.setLogoUrl(logoUrl);
        escuela.setActivo(true);

        Escuela guardada = escuelaRepositorio.save(escuela);
        return toDTO(guardada);
    }

    @Transactional
    public EscuelaDTO actualizarEscuela(Long id, EscuelaCrearDTO dto) {
        exigirAcceso(id);

        Escuela escuela = escuelaRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada con ID: " + id));

        // Verificar clave única (excluyendo el mismo registro)
        if (dto.getClave() != null && !dto.getClave().isBlank()) {
            if (escuelaRepositorio.existsByClaveAndIdNot(dto.getClave(), id)) {
                throw new NegocioExcepcion("Ya existe otra escuela con la clave: " + dto.getClave());
            }
        }

        // ------------------------------------------------------------
        // LOGO
        // Reglas:
        //   · Si viene archivo nuevo → eliminar el logo anterior y guardar el nuevo.
        //   · Si no viene archivo pero sí logoUrl → usar la URL tal cual.
        //     (cadena vacía limpia el logo; null significa "no tocar").
        // ------------------------------------------------------------
        if (dto.getLogoArchivo() != null && !dto.getLogoArchivo().isEmpty()) {
            if (escuela.getLogoUrl() != null) {
                archivoServicio.eliminarArchivo(escuela.getLogoUrl());
            }
            try {
                String nuevoLogoUrl = archivoServicio.guardarArchivo(dto.getLogoArchivo(), "escuela_");
                escuela.setLogoUrl(nuevoLogoUrl);
            } catch (IOException e) {
                throw new NegocioExcepcion("error_logo",
                        "Error al guardar el logo: " + e.getMessage());
            }
        } else if (dto.getLogoUrl() != null) {
            if (dto.getLogoUrl().isBlank()) {
                if (escuela.getLogoUrl() != null) {
                    archivoServicio.eliminarArchivo(escuela.getLogoUrl());
                }
                escuela.setLogoUrl(null);
            } else {
                escuela.setLogoUrl(dto.getLogoUrl());
            }
        }

        escuela.setNombre(dto.getNombre());
        escuela.setNombreLargo(dto.getNombreLargo());
        escuela.setDireccion(dto.getDireccion());
        escuela.setTelefono(dto.getTelefono());
        escuela.setClave(dto.getClave());

        Escuela actualizada = escuelaRepositorio.save(escuela);
        return toDTO(actualizada);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        exigirAcceso(id);
        Escuela escuela = escuelaRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada con ID: " + id));
        escuela.setActivo(activo);
        escuelaRepositorio.save(escuela);
    }

    @Transactional
    public void eliminarEscuela(Long id) {
        exigirAcceso(id);
        Escuela escuela = escuelaRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada con ID: " + id));

        // Eliminar el logo del disco antes de borrar la entidad
        if (escuela.getLogoUrl() != null) {
            archivoServicio.eliminarArchivo(escuela.getLogoUrl());
        }

        escuelaRepositorio.delete(escuela);
    }

    private EscuelaDTO toDTO(Escuela escuela) {
        return new EscuelaDTO(
                escuela.getEscuelaId(),
                escuela.getNombre(),
                escuela.getNombreLargo(),
                escuela.getDireccion(),
                escuela.getTelefono(),
                escuela.getLogoUrl(),
                escuela.getClave(),
                escuela.getActivo()
        );
    }
}