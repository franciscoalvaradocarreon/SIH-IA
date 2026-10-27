package mx.sih.pruebas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import mx.sih.seguridad.VigenciaUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRUEBAS DE LA VIGENCIA DEL USUARIO.
 *
 * <p>Unitarias y sin Docker: la regla es pura y recibe "hoy" como parametro, asi que no depende del
 * reloj ni de la base. El borde importante es el dia del vencimiento: la cuenta vale TODO ese dia.
 */
class VigenciaUsuarioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 27);

    @Test
    @DisplayName("sin fecha no hay vencimiento: es el estado de los usuarios que ya existen")
    void sinFechaNuncaVence() {
        assertFalse(VigenciaUsuario.vencida(null, HOY));
        assertFalse(VigenciaUsuario.vencida(null, null));
    }

    @Test
    @DisplayName("el dia del vencimiento la cuenta TODAVIA vale")
    void elDiaDelVencimientoVale() {
        assertFalse(VigenciaUsuario.vencida(HOY, HOY));
    }

    @Test
    @DisplayName("una fecha futura no vence")
    void fechaFuturaNoVence() {
        assertFalse(VigenciaUsuario.vencida(HOY.plusDays(1), HOY));
        assertFalse(VigenciaUsuario.vencida(LocalDate.of(2027, 12, 31), HOY));
    }

    @Test
    @DisplayName("al dia siguiente del vencimiento ya no vale")
    void alDiaSiguienteVence() {
        assertTrue(VigenciaUsuario.vencida(HOY.minusDays(1), HOY));
    }

    @Test
    @DisplayName("el mensaje dice la fecha en dd/MM/yyyy y a quien acudir")
    void elMensajeEsUtil() {
        String mensaje = VigenciaUsuario.mensaje(LocalDate.of(2026, 12, 31));
        assertTrue(mensaje.contains("31/12/2026"), "debe incluir la fecha: " + mensaje);
        assertTrue(mensaje.contains("vigencia"), mensaje);
        assertTrue(mensaje.contains("administrador"), mensaje);
    }

    @Test
    @DisplayName("el formato de fecha es el que se muestra en la interfaz")
    void formatoDeFecha() {
        assertEquals("31/12/2026", VigenciaUsuario.formatear(LocalDate.of(2026, 12, 31)));
        assertEquals("01/02/2027", VigenciaUsuario.formatear(LocalDate.of(2027, 2, 1)));
        assertEquals("", VigenciaUsuario.formatear(null));
    }
}
