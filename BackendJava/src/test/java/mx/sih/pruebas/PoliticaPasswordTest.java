package mx.sih.pruebas;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.seguridad.PoliticaPassword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRUEBAS DE LA POLITICA DE CONTRASENAS (hallazgo H-06).
 *
 * <p>Son pruebas UNITARIAS a proposito: no extienden {@link BasePruebas}, no levantan contenedores y
 * por tanto SI se ejecutan aunque no haya Docker. Es justo el contrapunto del hallazgo H-02: la
 * politica es una regla pura y se puede -y se debe- probar sin base de datos.
 */
class PoliticaPasswordTest {

    @Test
    @DisplayName("acepta una contrasena que cumple todo")
    void aceptaContrasenaValida() {
        assertDoesNotThrow(() -> PoliticaPassword.validar("Vespertino2026"));
        assertTrue(PoliticaPassword.esValida("Vespertino2026"));
    }

    @Test
    @DisplayName("rechaza las que son demasiado cortas")
    void rechazaCortas() {
        NegocioExcepcion e = assertThrows(NegocioExcepcion.class,
                () -> PoliticaPassword.validar("Abc12345"));
        assertEquals("password_debil", e.getCodigoError());
        assertTrue(e.getMensajeCrudo().contains(String.valueOf(PoliticaPassword.LONGITUD_MINIMA)),
                "el mensaje debe decir cual es el minimo");
    }

    @Test
    @DisplayName("el caso que reporto QA: seis digitos ya no pasan")
    void rechazaSoloDigitos() {
        // "123456" era lo que tenia el admin del piloto; y "123456789012" tampoco: son 12 pero
        // siguen siendo solo digitos.
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("123456"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("123456789012"));
        assertFalse(PoliticaPassword.esValida("123456789012"));
    }

    @Test
    @DisplayName("rechaza solo letras y el mismo caracter repetido")
    void rechazaSoloLetrasYRepetidas() {
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("abcdefghijkl"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("aaaaaaaaaaaa"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("111111111111"));
    }

    @Test
    @DisplayName("rechaza las obvias y las vacias")
    void rechazaObviasYVacias() {
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("qwertyuiop12"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Password1234"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("   "));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar(null));
    }

    @Test
    @DisplayName("rechaza las que exceden el maximo")
    void rechazaLargas() {
        String larga = "Abcdefghij12".repeat(12); // 144 caracteres
        NegocioExcepcion e = assertThrows(NegocioExcepcion.class,
                () -> PoliticaPassword.validar(larga));
        assertEquals("password_debil", e.getCodigoError());
    }

    @Test
    @DisplayName("los limites declarados son los que se anuncian (12 y 128)")
    void limitesCoherentes() {
        assertEquals(12, PoliticaPassword.LONGITUD_MINIMA);
        assertEquals(128, PoliticaPassword.LONGITUD_MAXIMA);
        // Exactamente 12 caracteres, con letra y digito: pasa. Uno menos: no.
        assertDoesNotThrow(() -> PoliticaPassword.validar("Abcdefghi123"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Abcdefgh123"));
    }
}
