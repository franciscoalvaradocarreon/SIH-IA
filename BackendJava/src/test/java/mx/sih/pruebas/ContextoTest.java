package mx.sih.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La prueba mas basica y la mas util: que la aplicacion ARRANQUE.
 *
 * <p>Parece trivial y no lo es: la mayoria de los fallos de despliegue son el
 * contexto que no levanta (una propiedad que falta, un bean mal cableado, un
 * repositorio con una consulta invalida). Aqui eso se detecta en el CI, no en
 * produccion a las 7 de la manana.
 */
@SpringBootTest
class ContextoTest extends BasePruebas {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("El contexto arranca y el esquema 'sih' tiene sus 19 tablas")
    void elContextoArrancaConElEsquemaCargado() throws Exception {
        try (var conexion = dataSource.getConnection()) {
            assertThat(conexion.isValid(5)).as("la conexion a PostgreSQL es valida").isTrue();

            try (var sentencia = conexion.createStatement();
                 var filas = sentencia.executeQuery(
                         "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sih'")) {
                filas.next();
                assertThat(filas.getInt(1))
                        .as("tablas creadas por db/esquema.sql")
                        .isEqualTo(19);
            }
        }
    }

    @Test
    @DisplayName("La tabla horario tiene la columna version con su valor por defecto")
    void laTablaHorarioEstaCompleta() throws Exception {
        try (var conexion = dataSource.getConnection();
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(
                     "SELECT column_default FROM information_schema.columns "
                   + "WHERE table_schema = 'sih' AND table_name = 'horario' AND column_name = 'version'")) {
            assertThat(filas.next()).as("existe horario.version").isTrue();
            // Sin este valor por defecto, un INSERT que no lo indique dejaria
            // version en NULL y el indice de solape (que filtra version = 1)
            // no protegeria nada.
            assertThat(filas.getString(1)).isEqualTo("1");
        }
    }
}
