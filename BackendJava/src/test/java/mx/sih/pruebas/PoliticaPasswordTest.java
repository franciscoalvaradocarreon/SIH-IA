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
 * PRUEBAS DE LA POLITICA DE CONTRASENAS (hallazgos H-06 y H-07).
 *
 * <p>Son pruebas UNITARIAS a proposito: no extienden {@link BasePruebas}, no levantan contenedores y
 * por tanto SI se ejecutan aunque no haya Docker. Es justo el contrapunto del hallazgo H-02: la
 * politica es una regla pura y se puede -y se debe- probar sin base de datos.
 *
 * <p>Las contrasenas que se usan aqui son ejemplos genericos y publicos (aparecen en cualquier lista
 * de seguridad). En ningun caso son credenciales reales de nadie.
 */
class PoliticaPasswordTest {

    /** Una contrasena que cumple todo. Se reutiliza para no repetir el literal. */
    private static final String VALIDA = "Tr4bajo#Fino";

    @Test
    @DisplayName("acepta una contrasena que cumple todo")
    void aceptaContrasenaValida() {
        assertDoesNotThrow(() -> PoliticaPassword.validar(VALIDA));
        assertTrue(PoliticaPassword.esValida(VALIDA));
        assertDoesNotThrow(() -> PoliticaPassword.validar("Xk9#mNpQ2vRt"));
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
        // Exactamente 12 caracteres, con letra y digito y sin secuencias: pasa. Uno menos: no.
        assertDoesNotThrow(() -> PoliticaPassword.validar("Tr4bajo#Fino"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Tr4bajo#Fin"));
    }

    // ------------------------------------------------------------------------
    // H-07 (16/10/2026): la contrasena del admin cayo a un ataque de diccionario
    // porque era "palabra + secuencia de numeros". Estas pruebas fijan las tres
    // reglas nuevas, para que nadie las pueda quitar sin que se note.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("H-07: rechaza palabra + secuencia de numeros (el patron que cayo)")
    void rechazaPalabraConSecuencia() {
        // El patron real, con otra palabra para no dejar la credencial en el codigo.
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Root1234567"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Verano12345x"));
    }

    @Test
    @DisplayName("H-07: rechaza secuencias de numeros y de letras, en los dos sentidos")
    void rechazaSecuencias() {
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Mesa1234#Ruta"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Ruta#Mesa4321"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Mesa#Rutaabcd"));
        // Cuatro seguidos ya no; tres si se permiten (no queremos volverla imposible).
        assertDoesNotThrow(() -> PoliticaPassword.validar("Mesa#Ruta987x"));
    }

    @Test
    @DisplayName("H-07: rechaza las palabras del sistema dentro de la contrasena")
    void rechazaPalabrasDelSistema() {
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Escuela2026!X"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("MiHorario2o26"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Clave#Sih2026"));
        // Y tambien con acentos y en mayusculas: se normaliza antes de comparar.
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Contraseña2026x"));
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("ADMIN1234567"));
    }

    @Test
    @DisplayName("H-07: rechaza las que estan en la lista de filtradas, con o sin los numeros")
    void rechazaFiltradas() {
        // Palabra comun con numeros pegados: el nucleo ("superman") esta en la lista.
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("Superman2026!"));
        // Y una cuyo nucleo esta tal cual en la lista.
        assertThrows(NegocioExcepcion.class, () -> PoliticaPassword.validar("iloveyou2026"));
    }

    @Test
    @DisplayName("la lista de filtradas esta empaquetada y completa (si no, la regla no existe)")
    void listadoEmpaquetado() {
        // Si alguien borra el archivo de recursos o lo deja a medias, esto lo dice aqui y no en
        // produccion. Es la leccion de H-02: un verde tiene que probar algo.
        assertTrue(PoliticaPassword.totalFiltradas() >= 9000,
                "deberian cargarse al menos 9000 contrasenas filtradas, hay "
                        + PoliticaPassword.totalFiltradas());
    }
}
