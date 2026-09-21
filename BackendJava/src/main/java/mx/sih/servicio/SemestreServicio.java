// mx.sih.servicio.SemestreServicio.java
package mx.sih.servicio;

import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.SemestreCrearDTO;
import mx.sih.modelo.dto.SemestreDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SemestreServicio {

    private final SemestreRepositorio semestreRepositorio;

    public SemestreServicio(SemestreRepositorio semestreRepositorio) {
        this.semestreRepositorio = semestreRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    /**
     * Listar semestres con paginación y búsqueda.
     */
    public Page<SemestreDTO> listarSemestres(Pageable pageable, String busqueda) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = busqueda == null ? "" : busqueda.trim();

        Page<Semestre> pagina = semestreRepositorio.findByEscuelaIdAndBusqueda(
                escuelaId, busquedaNormalizada, pageable
        );

        return pagina.map(this::toDTO);
    }

    /**
     * Listar todos los semestres (sin paginación).
     */
    public List<SemestreDTO> listarTodosSemestres() {
        Long escuelaId = getEscuelaId();
        return semestreRepositorio.findByEscuelaId(escuelaId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Listar semestres activos.
     */
    public List<SemestreDTO> listarSemestresActivos() {
        Long escuelaId = getEscuelaId();
        return semestreRepositorio.findActivosByEscuelaId(escuelaId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtener semestre actual (activo).
     */
    public SemestreDTO obtenerSemestreActual() {
        Long escuelaId = getEscuelaId();
        List<Semestre> semestres = semestreRepositorio.findActivosByEscuelaId(escuelaId);
        if (semestres.isEmpty()) {
            throw new NegocioExcepcion("No hay semestres activos");
        }
        // Si hay varios, ordenar por ID descendente y tomar el primero.
        semestres.sort((a, b) -> b.getSemestreId().compareTo(a.getSemestreId()));
        return toDTO(semestres.get(0));
    }

    /**
     * Obtener semestre por ID.
     */
    public SemestreDTO obtenerSemestre(Long id) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));
        return toDTO(semestre);
    }

    /**
     * Crear un nuevo semestre.
     */
    public SemestreDTO crearSemestre(SemestreCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        // Verificar duplicado.
        if (semestreRepositorio.existsByNombreAndEscuelaIdAndIdNot(dto.getNombre(), escuelaId, 0L)) {
            throw new NegocioExcepcion("Ya existe un semestre con el nombre: " + dto.getNombre());
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Semestre semestre = new Semestre();
        semestre.setEscuela(escuela);
        semestre.setNombre(dto.getNombre());
        semestre.setDescripcion(dto.getDescripcion());
        semestre.setActivo(dto.getActivo() != null ? dto.getActivo() : true);

        Semestre guardado = semestreRepositorio.save(semestre);
        return toDTO(guardado);
    }

    /**
     * Actualizar un semestre existente.
     */
    public SemestreDTO actualizarSemestre(Long id, SemestreCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        // Verificar duplicado (excluyendo el actual).
        if (semestreRepositorio.existsByNombreAndEscuelaIdAndIdNot(dto.getNombre(), escuelaId, id)) {
            throw new NegocioExcepcion("Ya existe un semestre con el nombre: " + dto.getNombre());
        }

        semestre.setNombre(dto.getNombre());
        semestre.setDescripcion(dto.getDescripcion());
        if (dto.getActivo() != null) {
            semestre.setActivo(dto.getActivo());
        }

        Semestre actualizado = semestreRepositorio.save(semestre);
        return toDTO(actualizado);
    }

    /**
     * Cambiar estado (activo/inactivo) de un semestre.
     */
    public SemestreDTO cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        semestre.setActivo(activo);
        Semestre actualizado = semestreRepositorio.save(semestre);
        return toDTO(actualizado);
    }

    /**
     * Eliminar un semestre.
     *
     * Si la BD rechaza el DELETE por dependencias (FK), se delega el formato
     * del mensaje a MensajeErrorUtil, que extrae la tabla referenciada del
     * error de PostgreSQL. Antes este servicio duplicaba los 3 regex de
     * MensajeErrorUtil en un método privado.
     */
    public void eliminarSemestre(Long id) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        try {
            semestreRepositorio.delete(semestre);
            semestreRepositorio.flush(); // Fuerza el flush para que la excepción se dispare aquí
        } catch (DataIntegrityViolationException e) {
            throw MensajeErrorUtil.crearExcepcionDependencia("el semestre", e);
        }
    }

    /**
     * Convertir entidad a DTO.
     */
    private SemestreDTO toDTO(Semestre semestre) {
        return new SemestreDTO(
                semestre.getSemestreId(),
                semestre.getEscuela().getEscuelaId(),
                semestre.getEscuela().getNombre(),
                semestre.getNombre(),
                semestre.getDescripcion(),
                semestre.getActivo(),
                semestre.getCreado()
        );
    }
}