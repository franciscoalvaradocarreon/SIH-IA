import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Genera un hash bcrypt de una contrasena, con el MISMO formato que usa el
 * backend ({@code $2a$12$...}), para poder crear usuarios desde linea de
 * comandos sin pasar por la aplicacion.
 *
 * <p>Se usa la clase {@link BCrypt} y no {@code BCryptPasswordEncoder} a
 * proposito: {@code BCrypt} no depende de spring-core, asi que basta con un
 * unico jar en el classpath.
 *
 * <p>Uso (Java 21 ejecuta el archivo fuente directamente):
 * <pre>
 *   java -cp spring-security-crypto-7.1.0.jar db/_HashBcrypt.java "MiClave" 12
 * </pre>
 *
 * <p>El hash se imprime en la salida estandar y NUNCA se guarda en disco: el
 * llamante (db/crear_admin.ps1) lo pasa directo a psql.
 */
public class _HashBcrypt {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("uso: _HashBcrypt <contrasena> [coste=12]");
            System.exit(2);
        }
        int coste = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        if (coste < 10 || coste > 16) {
            System.err.println("coste fuera de rango razonable (10-16): " + coste);
            System.exit(2);
        }
        System.out.println(BCrypt.hashpw(args[0], BCrypt.gensalt(coste)));
    }
}
