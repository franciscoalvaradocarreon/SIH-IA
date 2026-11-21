package mx.sih.repositorio;

import java.util.List;
import java.util.Optional;
import mx.sih.modelo.entidad.TipoEspacio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipoEspacioRepositorio extends JpaRepository<TipoEspacio, Long> {

    /** Todos los tipos de un turno, activos e inactivos, para la pantalla de administracion. */
    List<TipoEspacio> findByTurnoIdOrderByClaseAscNombreAsc(Long turnoId);

    /** Solo los activos, para el desplegable del aula y para el motor. */
    List<TipoEspacio> findByTurnoIdAndActivoTrueOrderByClaseAscNombreAsc(Long turnoId);

    /** Busca el tipo comprobando que pertenece al turno indicado. */
    Optional<TipoEspacio> findByTipoEspacioIdAndTurnoId(Long tipoEspacioId, Long turnoId);

    /** Evita repetir el mismo nombre dentro de la misma clase y turno. */
    boolean existsByTurnoIdAndClaseAndNombreIgnoreCase(Long turnoId, String clase, String nombre);

    long countByTurnoId(Long turnoId);
}
