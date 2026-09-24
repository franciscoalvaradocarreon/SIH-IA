// mx.sih.repositorio.CorridaIaRepositorio.java
package mx.sih.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import mx.sih.modelo.entidad.CorridaIa;

/**
 * Acceso a las corridas guardadas del generador IA.
 *
 * <p>No se filtra por turno a proposito: un mismo semestre puede tener corridas de varios turnos y
 * verlas todas juntas es lo que permite comparar. El turno va en la respuesta para poder etiquetar
 * cada fila. Ademas el turno puede ser null (si se borro del catalogo), asi que un filtro por turno
 * esconderia esas corridas.
 */
@Repository
public interface CorridaIaRepositorio extends JpaRepository<CorridaIa, Long> {

    /** Las corridas de una escuela y un semestre, de la mas reciente a la mas antigua. */
    @Query("SELECT c FROM CorridaIa c " +
           "WHERE c.escuela.escuelaId = :escuelaId " +
           "AND c.semestre.semestreId = :semestreId " +
           "ORDER BY c.creado DESC, c.corridaIaId DESC")
    List<CorridaIa> listar(@Param("escuelaId") Long escuelaId,
                           @Param("semestreId") Long semestreId);

    /**
     * Una corrida concreta, exigiendo que sea de la escuela activa. El filtro por escuela no es
     * decorativo: sin el, un identificador adivinado dejaria aplicar la corrida de otra escuela.
     */
    @Query("SELECT c FROM CorridaIa c " +
           "WHERE c.corridaIaId = :id " +
           "AND c.escuela.escuelaId = :escuelaId")
    Optional<CorridaIa> buscarPorIdYEscuela(@Param("id") Long id,
                                            @Param("escuelaId") Long escuelaId);
}
