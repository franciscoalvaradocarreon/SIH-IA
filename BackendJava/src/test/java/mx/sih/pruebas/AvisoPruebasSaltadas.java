package mx.sih.pruebas;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * AVISO DE PRUEBAS SALTADAS (hallazgo H-02 de QA).
 *
 * <p>Problema que resuelve: {@link BasePruebas} usa
 * {@code @Testcontainers(disabledWithoutDocker = true)} para que compilar no exija Docker. El
 * efecto secundario es peligroso: en una maquina sin Docker, Surefire informa
 * {@code Tests run: 9, Skipped: 9} y termina con <b>BUILD SUCCESS</b>. Quien lea solo el verde
 * -o el paso en GitHub Actions- cree que el backend esta probado cuando no se ejecuto nada.
 *
 * <p>Este listener se registra por {@code ServiceLoader} (ver
 * {@code src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener}),
 * asi que corre SIEMPRE, incluso cuando todas las clases de prueba se saltan. Al terminar el plan
 * de pruebas imprime el resumen y, si todo se salto, un aviso imposible de pasar por alto.
 *
 * <p>El listener solo AVISA. Quien falla la compilacion es la comprobacion del CI
 * ({@code .github/workflows/ci.yml}) y el guion {@code herramientas/revisar-pruebas.ps1}, que leen
 * los reportes de Surefire y salen con error si el total de pruebas es igual al de saltadas.
 */
public class AvisoPruebasSaltadas implements TestExecutionListener {

    // ESTATICOS a proposito: el launcher puede crear mas de una instancia del listener, y los
    // contadores de instancia se quedan a medias (paso: el resumen salio en 0 y 0 mientras Surefire
    // informaba 9 saltadas). Con campos estaticos, todas las instancias suman al mismo sitio.
    private static int pruebasEnElPlan;
    private static int ejecutadas;
    private static int fallidas;

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        // El plan SI trae todas las pruebas, incluidas las de las clases que luego se saltan.
        // De aqui sale el total; lo ejecutado se cuenta en executionFinished, y la diferencia son
        // las saltadas. Asi no hace falta depender de executionSkipped, que en una clase
        // deshabilitada entera no siempre llega prueba por prueba.
        pruebasEnElPlan = (int) plan.countTestIdentifiers(TestIdentifier::isTest);
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult resultado) {
        if (!id.isTest()) {
            return;
        }
        ejecutadas++;
        if (resultado.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
            fallidas++;
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        int total = pruebasEnElPlan;
        int saltadas = Math.max(0, total - ejecutadas);
        System.out.println();
        System.out.println("================= RESUMEN DE PRUEBAS DEL BACKEND =================");
        System.out.println("  pruebas en el plan: " + total);
        System.out.println("  ejecutadas:         " + ejecutadas + " (con fallo: " + fallidas + ")");
        System.out.println("  saltadas:           " + saltadas);

        if (total == 0) {
            aviso("NO SE EJECUTO NINGUNA PRUEBA",
                    "No se encontro ni una prueba. Revisa que existan y que se esten descubriendo.");
        } else if (saltadas == total) {
            aviso("LAS " + total + " PRUEBAS SE SALTARON: ESTE 'BUILD SUCCESS' NO PRUEBA NADA",
                    "Casi siempre es que Docker no esta corriendo. Las pruebas de integracion levantan un"
                            + " PostgreSQL 17 de verdad y, sin Docker, Testcontainers las salta en silencio."
                            + " Enciende Docker y vuelve a correr 'mvn test'.");
        } else if (saltadas > 0) {
            System.out.println("  OJO: hay pruebas saltadas. Revisa por que antes de dar por bueno el resultado.");
        } else {
            System.out.println("  Todo ejecutado.");
        }
        System.out.println("=================================================================");
        System.out.println();
    }

    private void aviso(String titulo, String detalle) {
        String linea = "!".repeat(78);
        System.out.println(linea);
        System.out.println("!!  " + titulo);
        System.out.println("!!  " + detalle);
        System.out.println(linea);
    }
}
