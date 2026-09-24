// mx.sih.repositorio.CorridaIaDetalleRepositorio.java
package mx.sih.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import mx.sih.modelo.entidad.CorridaIaDetalle;

/** Filas (bloques ocupados) de las corridas guardadas. */
@Repository
public interface CorridaIaDetalleRepositorio extends JpaRepository<CorridaIaDetalle, Long> {

    /** Todas las filas de una corrida, en orden estable para que dos lecturas den lo mismo. */
    @Query("SELECT d FROM CorridaIaDetalle d " +
           "WHERE d.corrida.corridaIaId = :corridaId " +
           "ORDER BY d.asignacionId ASC, d.turnoHorarioId ASC")
    List<CorridaIaDetalle> listarPorCorrida(@Param("corridaId") Long corridaId);

    /** Cuantas filas tiene guardadas una corrida. */
    @Query("SELECT COUNT(d) FROM CorridaIaDetalle d WHERE d.corrida.corridaIaId = :corridaId")
    long contarPorCorrida(@Param("corridaId") Long corridaId);

    /**
     * Cuantas filas tiene cada corrida de la lista, en UNA consulta.
     *
     * <p>Se usa para comparar con {@code CorridaIa.totalFilas} y detectar corridas incompletas: si
     * alguna asignacion o bloque del catalogo se borro despues de guardar, la clave foranea en cascada
     * se llevo filas por delante y la opcion ya no representa lo que era. Aplicarla tal cual daria un
     * horario a medias, asi que mas vale marcarla en la lista.
     *
     * @return pares {@code [corridaIaId, numeroDeFilas]}
     */
    @Query("SELECT d.corrida.corridaIaId, COUNT(d) FROM CorridaIaDetalle d " +
           "WHERE d.corrida.corridaIaId IN :ids " +
           "GROUP BY d.corrida.corridaIaId")
    List<Object[]> contarPorCorridas(@Param("ids") Collection<Long> ids);
}
