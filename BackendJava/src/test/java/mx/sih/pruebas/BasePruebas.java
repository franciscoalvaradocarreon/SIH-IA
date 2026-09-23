package mx.sih.pruebas;

import java.nio.file.Path;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Base de todas las pruebas de integracion.
 *
 * <p>Levanta un <b>PostgreSQL 17 de verdad</b> en un contenedor y le carga el
 * MISMO bootstrap que produccion: {@code db/esquema.sql} y {@code db/02_solape.sql}.
 * Eso es deliberado: si las pruebas usaran su propio SQL, podrian pasar mientras
 * produccion esta rota (o al reves), que es la peor clase de prueba.
 *
 * <p>Por que no H2: este proyecto depende de cosas que H2 no tiene (el esquema
 * {@code sih}, el tipo {@code "char"} de {@code pg_constraint}, los indices
 * UNIQUE parciales que son la red de seguridad contra solapes, y la sintaxis de
 * {@code pg_dump}). Una prueba contra H2 no demuestra nada sobre produccion.
 *
 * <p>{@code disabledWithoutDocker = true}: en una maquina sin Docker las pruebas
 * se SALTAN en lugar de fallar, para que compilar no exija tener Docker.
 * En el CI siempre hay Docker, asi que ahi se ejecutan.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class BasePruebas {

    /**
     * Carpeta con el bootstrap de la base. La define el pom (maven-surefire-plugin)
     * como propiedad del sistema, apuntando a {@code <raiz>/db}, para no depender
     * del directorio desde el que se lance Maven.
     */
    private static final Path DIR_BD = Path.of(System.getProperty("sih.db.dir", "../db"));

    /**
     * Sin {@code @Container} a proposito.
     *
     * <p>Con {@code @Container} en un campo estatico, Testcontainers arranca el
     * contenedor al empezar la PRIMERA clase de prueba y lo PARA al terminarla:
     * las clases siguientes heredan el mismo objeto apuntando a un contenedor
     * muerto y fallan con "Connection refused". Paso en el CI: ExclusionesTest
     * y SeguridadTest pasaban (eran las primeras) y ContextoTest fallaba.
     *
     * <p>Arrancandolo una sola vez en el bloque estatico de abajo, el contenedor
     * vive lo que vive la JVM. Lo recoge Ryuk al terminar.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(DIR_BD.resolve("esquema.sql")),
                    "/docker-entrypoint-initdb.d/01_esquema.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(DIR_BD.resolve("02_solape.sql")),
                    "/docker-entrypoint-initdb.d/02_solape.sql");

    static {
        POSTGRES.start();
    }
}
