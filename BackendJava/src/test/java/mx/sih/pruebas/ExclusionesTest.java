package mx.sih.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La red de seguridad contra solapes: que EXISTA y que MUERDA.
 *
 * <p>Este proyecto ya vivio el caso contrario. Durante meses se creyo que habia
 * tres "constraints de exclusion" protegiendo el horario, y la realidad era que
 * nunca existieron (faltaba la extension btree_gist y el error paso inadvertido).
 * Mientras tanto, la aplicacion permitio solapes que nadie detecto.
 *
 * <p>Por eso no basta con comprobar que los indices estan: hay que intentar
 * colar un solape y exigir que la base lo rechace. Una prueba que solo mira el
 * catalogo pasaria aunque el indice no sirviera para nada.
 */
@SpringBootTest
class ExclusionesTest extends BasePruebas {

    /**
     * Inserta una clase en el horario. Se usa un grupo y un bloque que no existen
     * en ningun catalogo a proposito: con session_replication_role = replica las
     * claves foraneas no se comprueban, y asi la prueba se centra en el indice de
     * solape sin tener que montar escuelas, grupos, aulas, maestros y asignaciones.
     */
    private static final String COLUMNAS =
            "INSERT INTO sih.horario (escuela_id, grupo_id, asignacion_id, turno_horario_id, "
          + "aula_id, version, semestre_id, maestro_id) VALUES ";

    /** Clase vigente (version = 1): SI cuenta para los indices de solape. */
    private static final String CLASE_VIGENTE = COLUMNAS + "(1, 777777, 1, 888888, 1, 1, 1, 1)";

    /** La misma clase en otra version: los indices son parciales y la ignoran. */
    private static final String CLASE_VERSION_2 = COLUMNAS + "(1, 777777, 1, 888888, 1, 2, 1, 1)";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Los 3 indices UNIQUE parciales de solape existen sobre sih.horario")
    void losTresIndicesExisten() throws Exception {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(
                     "SELECT count(*) FROM pg_indexes "
                   + "WHERE schemaname = 'sih' AND tablename = 'horario' "
                   + "  AND indexname IN ('no_solape_grupo_bloque', "
                   + "                    'no_solape_maestro_bloque', "
                   + "                    'no_solape_aula_bloque') "
                   + "  AND indexdef ILIKE '%UNIQUE%'")) {
            filas.next();
            assertThat(filas.getInt(1))
                    .as("grupo, maestro y aula deben tener su indice UNIQUE parcial")
                    .isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Dos clases del mismo grupo en el mismo bloque son RECHAZADAS por la base")
    void unSolapeDeGrupoEsRechazado() throws Exception {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.createStatement()) {

            sentencia.execute("SET session_replication_role = replica");

            // La primera entra sin problema.
            sentencia.execute(CLASE_VIGENTE);

            // La segunda, con el mismo grupo y el mismo bloque, DEBE fallar.
            assertThatThrownBy(() -> sentencia.execute(CLASE_VIGENTE))
                    .as("la base debe rechazar el solape, no aceptarlo en silencio")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("no_solape_grupo_bloque");

            sentencia.execute("DELETE FROM sih.horario WHERE grupo_id = 777777");
        }
    }

    @Test
    @DisplayName("Una clase con version distinta de 1 no cuenta para el solape (los indices son parciales)")
    void laVersionFueraDelIndiceNoBloquea() throws Exception {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.createStatement()) {

            sentencia.execute("SET session_replication_role = replica");
            sentencia.execute(CLASE_VIGENTE);

            // Mismo grupo y bloque, pero version = 2: el indice es parcial
            // (WHERE version = 1), asi que no debe estorbar. Esto documenta el
            // comportamiento del que depende la lectura de la aplicacion, que
            // trata version NULL o 1 como la version vigente.
            sentencia.execute(CLASE_VERSION_2);

            sentencia.execute("DELETE FROM sih.horario WHERE grupo_id = 777777");
        }
    }
}
