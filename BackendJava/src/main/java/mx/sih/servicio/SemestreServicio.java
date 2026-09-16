// mx.sih.servicio.SemestreServicio.java
package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.SemestreCrearDTO;
import mx.sih.modelo.dto.SemestreDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;

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
     * Listar semestres con paginación y búsqueda
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
     * Listar todos los semestres (sin paginación)
     */
    public List<SemestreDTO> listarTodosSemestres() {
        Long escuelaId = getEscuelaId();
        return semestreRepositorio.findByEscuelaId(escuelaId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Listar semestres activos
     */
    public List<SemestreDTO> listarSemestresActivos() {
        Long escuelaId = getEscuelaId();
        return semestreRepositorio.findActivosByEscuelaId(escuelaId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Obtener semestre actual (activo)
     */
    public SemestreDTO obtenerSemestreActual() {
        Long escuelaId = getEscuelaId();
        List<Semestre> semestres = semestreRepositorio.findActivosByEscuelaId(escuelaId);
        if (semestres.isEmpty()) {
            throw new NegocioExcepcion("No hay semestres activos");
        }
        // Si hay varios, ordenar por ID descendente y tomar el primero
        semestres.sort((a, b) -> b.getSemestreId().compareTo(a.getSemestreId()));
        return toDTO(semestres.get(0));
    }

    /**
     * Obtener semestre por ID
     */
    public SemestreDTO obtenerSemestre(Long id) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));
        return toDTO(semestre);
    }

    /**
     * Crear un nuevo semestre
     */
    public SemestreDTO crearSemestre(SemestreCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        // Verificar duplicado
        if (semestreRepositorio.existsByNombreAndEscuelaIdAndIdNot(dto.getNombre(), escuelaId, 0L)) {
            throw new NegocioExcepcion("Ya existe un semestre con el nombre: " + dto.getNombre());
        }

        // Si se está creando como activo, desactivar otros semestres
        //if (dto.getActivo() != null && dto.getActivo()) {
        //    desactivarTodosLosSemestres(escuelaId);
        //}

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
     * Actualizar un semestre existente
     */
    public SemestreDTO actualizarSemestre(Long id, SemestreCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        // Verificar duplicado (excluyendo el actual)
        if (semestreRepositorio.existsByNombreAndEscuelaIdAndIdNot(dto.getNombre(), escuelaId, id)) {
            throw new NegocioExcepcion("Ya existe un semestre con el nombre: " + dto.getNombre());
        }

        // Si se está activando, desactivar otros semestres
        //if (dto.getActivo() != null && dto.getActivo() && !semestre.getActivo()) {
        //    desactivarTodosLosSemestres(escuelaId);
        //}

        semestre.setNombre(dto.getNombre());
        semestre.setDescripcion(dto.getDescripcion());
        if (dto.getActivo() != null) {
            semestre.setActivo(dto.getActivo());
        }

        Semestre actualizado = semestreRepositorio.save(semestre);
        return toDTO(actualizado);
    }

    /**
     * Cambiar estado (activo/inactivo) de un semestre
     */
    public SemestreDTO cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        // Si se está activando, desactivar otros semestres
        //if (activo && !semestre.getActivo()) {
        //    desactivarTodosLosSemestres(escuelaId);
        //}

        semestre.setActivo(activo);
        Semestre actualizado = semestreRepositorio.save(semestre);
        return toDTO(actualizado);
    }

    /**
     * Eliminar un semestre
     */
    @Transactional
    public void eliminarSemestre(Long id) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(id, escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado con ID: " + id));

        try {
            semestreRepositorio.delete(semestre);
            semestreRepositorio.flush(); // 🔥 Forzar el flush para que se dispare la excepción aquí
        } catch (DataIntegrityViolationException e) {
            String mensaje = e.getMostSpecificCause().getMessage();

            // 🔥 Extraer la tabla referenciada del mensaje de PostgreSQL
            String tablaReferenciada = extraerTablaDelMensaje(mensaje);

            throw new NegocioExcepcion(
                "DEPENDENCIAS",
                "No se puede eliminar el semestre porque está siendo usado en la tabla: "
                + tablaReferenciada + ". Elimina primero esos registros o desactiva el semestre."
            );
        }
    }

    /**
     * Desactivar todos los semestres de una escuela
     */
    private void desactivarTodosLosSemestres(Long escuelaId) {
        List<Semestre> semestres = semestreRepositorio.findByEscuelaId(escuelaId);
        for (Semestre s : semestres) {
            if (s.getActivo()) {
                s.setActivo(false);
                semestreRepositorio.save(s);
            }
        }
    }
    
    /**
    * Extrae el nombre de la tabla del mensaje de PostgreSQL.
    * Ejemplo: "todavía es referida desde la tabla «turno»" → "turno"
    */
   private String extraerTablaDelMensaje(String mensaje) {
       if (mensaje == null) return "desconocida";

       // Patrón 1: "...referida desde la tabla «turno»"
       java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(
           "referida desde la tabla «([^»]+)»");
       java.util.regex.Matcher m1 = p1.matcher(mensaje);
       if (m1.find()) {
           return m1.group(1);
       }

       // Patrón 2: "...viola la llave foránea «...» en la tabla «turno»"
       java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
           "en la tabla «([^»]+)»");
       java.util.regex.Matcher m2 = p2.matcher(mensaje);
       if (m2.find()) {
           return m2.group(1);
       }

       // Patrón 3: "...constraint \"...\" of relation \"turno\""
       java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
           "of relation \"([^\"]+)\"");
       java.util.regex.Matcher m3 = p3.matcher(mensaje);
       if (m3.find()) {
           return m3.group(1);
       }

       return "desconocida";
   }


    /**
     * Convertir entidad a DTO
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