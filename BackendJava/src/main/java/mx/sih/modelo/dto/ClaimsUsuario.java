package mx.sih.modelo.dto;

import java.util.List;

/**
 * Datos que viajan dentro del JWT y que la aplicacion usa en cada peticion.
 *
 * <p>{@code tokenVersion} es el contador de sesiones (reclamo "tv"): si no coincide con el que hay en
 * la base, el token ya no vale. Se incrementa al cerrar sesion y al cambiar la contrasena.
 *
 * <p>Los tokens emitidos antes de que existiera este mecanismo no traen el reclamo: se interpretan
 * como version 0 -que es el valor por defecto de la columna- para no cerrar todas las sesiones el dia
 * del despliegue. En cuanto el usuario cierre sesion o cambie su contrasena, entra en vigor.
 */
public record ClaimsUsuario(
    Long usuarioId,
    String correo,
    List<String> roles,
    Long escuelaId,
    List<Long> escuelaIds,
    Integer tokenVersion
){
    /** Constructor de retrocompatibilidad con tokens viejos. */
    public ClaimsUsuario(Long usuarioId, String correo, List<String> roles, Long escuelaId) {
        this(usuarioId, correo, roles, escuelaId,
             escuelaId != null ? List.of(escuelaId) : List.of(), null);
    }

    /** Igual, indicando varias escuelas, para tokens sin version. */
    public ClaimsUsuario(Long usuarioId, String correo, List<String> roles, Long escuelaId,
                         List<Long> escuelaIds) {
        this(usuarioId, correo, roles, escuelaId, escuelaIds, null);
    }

    /** Version del token, tratando "sin reclamo" como 0 (tokens anteriores al mecanismo). */
    public int versionToken() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public boolean tieneAccesoA(Long otraEscuelaId) {
        return otraEscuelaId != null && escuelaIds.contains(otraEscuelaId);
    }
}
