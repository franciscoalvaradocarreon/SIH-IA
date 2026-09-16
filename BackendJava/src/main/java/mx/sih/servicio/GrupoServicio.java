package mx.sih.servicio;

import java.util.List;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.GrupoCrearDTO;
import mx.sih.modelo.dto.GrupoDTO;
import mx.sih.modelo.dto.GrupoDetalleDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrupoServicio {

    private final GrupoRepositorio grupoRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final EspecialidadRepositorio especialidadRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadRepositorio;


    public GrupoServicio(GrupoRepositorio grupoRepositorio,
                         TurnoRepositorio turnoRepositorio,
                         EspecialidadRepositorio especialidadRepositorio,
                         SemestreRepositorio semestreRepositorio,
                         AsignacionRepositorio asignacionRepositorio,
                         DisponibilidadGrupoRepositorio disponibilidadRepositorio) {
        this.grupoRepositorio = grupoRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.especialidadRepositorio = especialidadRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.asignacionRepositorio = asignacionRepositorio;
        this.disponibilidadRepositorio = disponibilidadRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<GrupoDTO> listarGrupos(Pageable pageable,
                                       String busqueda,
                                       Long especialidadId,
                                       Long turnoId,
                                       Long semestreId) {
        Long escuelaId = getEscuelaId();
        System.out.println("📌 especialidadId: " + especialidadId + " | semestreId: " + semestreId);

        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        // Si no hay semestreId, usar el semestre activo
        if (semestreId == null || semestreId == 0) {
            List<Semestre> semestresActivos = semestreRepositorio.findActivosByEscuelaId(escuelaId);
            if (semestresActivos.isEmpty()) {
                // Si no hay semestres activos, devolver página vacía
                return Page.empty(pageable);
            }
            semestreId = semestresActivos.get(0).getSemestreId();
        }

        // Validar que el semestre pertenece a la escuela
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(semestreId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado o no pertenece a esta escuela"));

        Page<Grupo> pagina;

        // Si hay especialidad y es válida (> 0)
        if (especialidadId != null && especialidadId > 0) {
            // Verificar que la especialidad existe y pertenece a la escuela
            Especialidad especialidad = especialidadRepositorio.findByIdAndEscuelaId(especialidadId, escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("Especialidad no encontrada"));
            
            pagina = grupoRepositorio.findByEscuelaYFiltros( escuelaId, busquedaNormalizada, especialidadId, turnoId, semestreId, pageable);
        } else {        
            pagina = grupoRepositorio.findByEscuelaYFiltros( escuelaId, busquedaNormalizada, especialidadId, turnoId, semestreId, pageable);
            //pagina = grupoRepositorio.findByEscuelaIdAndBusquedaAndSemestreId(
              //      escuelaId, busquedaNormalizada, semestreId, pageable);
        }

        return pagina.map(this::toDTO);
    }

    public GrupoDetalleDTO obtenerGrupo(Long id) {
        Long escuelaId = getEscuelaId();
        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + id));
        return toDetalleDTO(grupo);
    }

    @Transactional
    public GrupoDTO crearGrupo(GrupoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        
        // Verificar turno
        Turno turno = turnoRepositorio.findById(dto.getTurnoId())
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + dto.getTurnoId()));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("El turno seleccionado no pertenece a esta escuela");
        }

        // Verificar especialidad (opcional)
        Especialidad especialidad = null;
        Long especialidadId = null;
        if (dto.getEspecialidadId() != null && dto.getEspecialidadId() > 0) {
            especialidad = especialidadRepositorio.findByIdAndEscuelaId(dto.getEspecialidadId(), escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("Especialidad no encontrada o no pertenece a esta escuela: " + dto.getEspecialidadId()));
            especialidadId = especialidad.getEspecialidadId();
        }

        // 🔥 VALIDAR NOMBRE ÚNICO POR ESCUELA Y ESPECIALIDAD
        if (grupoRepositorio.existsByEscuelaIdAndNombreAndEspecialidad(
                escuelaId, dto.getNombre(), especialidadId)) {
            String mensaje = especialidad != null 
                ? "Ya existe un grupo con el nombre: " + dto.getNombre() + " en la especialidad: " + especialidad.getNombre()
                : "Ya existe un grupo con el nombre: " + dto.getNombre() + " sin especialidad";
            throw new NegocioExcepcion(mensaje);
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Grupo grupo = new Grupo();
        grupo.setEscuela(escuela);
        grupo.setNombre(dto.getNombre().toUpperCase());
        grupo.setGrado(dto.getGrado());
        grupo.setTurno(turno);
        grupo.setEspecialidad(especialidad);
        grupo.setCapacidad(dto.getCapacidad() != null ? dto.getCapacidad() : 0);
        grupo.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        grupo.setSemestre(semestre);
        Grupo guardado = grupoRepositorio.save(grupo);
        return toDTO(guardado);
    }

    @Transactional
    public GrupoDTO actualizarGrupo(Long id, GrupoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));
        
        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + id));

        // Verificar turno
        Turno turno = turnoRepositorio.findById(dto.getTurnoId())
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + dto.getTurnoId()));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("El turno seleccionado no pertenece a esta escuela");
        }

        // Verificar especialidad (opcional)
        Especialidad especialidad = null;
        Long especialidadId = null;
        if (dto.getEspecialidadId() != null && dto.getEspecialidadId() > 0) {
            especialidad = especialidadRepositorio.findByIdAndEscuelaId(dto.getEspecialidadId(), escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("Especialidad no encontrada o no pertenece a esta escuela: " + dto.getEspecialidadId()));
            especialidadId = especialidad.getEspecialidadId();
        }

        // VALIDAR NOMBRE ÚNICO POR ESCUELA Y ESPECIALIDAD (excluyendo el mismo registro)
        if (!grupo.getNombre().equalsIgnoreCase(dto.getNombre()) ||
            !isSameEspecialidad(grupo.getEspecialidad(), especialidad)) {

            if (grupoRepositorio.existsByEscuelaIdAndNombreAndEspecialidadAndIdNot(
                    escuelaId, dto.getNombre(), especialidadId, id)) {
                String mensaje = especialidad != null 
                    ? "Ya existe otro grupo con el nombre: " + dto.getNombre() + " en la especialidad: " + especialidad.getNombre()
                    : "Ya existe otro grupo con el nombre: " + dto.getNombre() + " sin especialidad";
                throw new NegocioExcepcion(mensaje);
            }
        }

        grupo.setNombre(dto.getNombre().toUpperCase());
        grupo.setGrado(dto.getGrado());
        grupo.setTurno(turno);
        grupo.setEspecialidad(especialidad);
        grupo.setCapacidad(dto.getCapacidad() != null ? dto.getCapacidad() : 0);
        if (dto.getActivo() != null) {
            grupo.setActivo(dto.getActivo());
        }
        grupo.setSemestre(semestre); 

        Grupo actualizado = grupoRepositorio.save(grupo);
        return toDTO(actualizado);
    }
    
    private boolean isSameEspecialidad(Especialidad actual, Especialidad nueva) {
    if (actual == null && nueva == null) return true;
    if (actual == null || nueva == null) return false;
    return actual.getEspecialidadId().equals(nueva.getEspecialidadId());
    }
    
    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        System.out.println("📌 Buscando grupo ID: " + id + " en escuela: " + escuelaId);

        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + id));

        grupo.setActivo(activo);
        grupoRepositorio.save(grupo);
    }

    @Transactional
    public void eliminarGrupo(Long id) {
        Long escuelaId = getEscuelaId();

        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Grupo no encontrado con ID: " + id));

        // 1. Verificar asignaciones
        long asignaciones = asignacionRepositorio.countByGrupoId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "GRUPO_EN_USO",
                    "No se puede eliminar el grupo '" + grupo.getNombre() +
                    "' porque tiene " + asignaciones +
                    " asignación(es) activa(s). Elimina primero las asignaciones " +
                    "o desactiva el grupo en lugar de eliminarlo."
            );
        }

        // 2. Verificar disponibilidad configurada
        long disponibilidad = disponibilidadRepositorio.countByGrupoId(id);
        if (disponibilidad > 0) {
            throw new NegocioExcepcion(
                    "GRUPO_EN_USO",
                    "No se puede eliminar el grupo '" + grupo.getNombre() +
                    "' porque tiene " + disponibilidad +
                    " bloque(s) de disponibilidad configurado(s). Elimínalos primero " +
                    "desde la pantalla de Disponibilidad de Grupos o desactiva el grupo."
            );
        }

        // 3. Sin dependencias: eliminar
        grupoRepositorio.delete(grupo);
    }

    private GrupoDTO toDTO(Grupo grupo) {
        GrupoDTO dto = new GrupoDTO();
        dto.setId(grupo.getGrupoId());
        dto.setNombre(grupo.getNombre());
        dto.setGrado(grupo.getGrado());
        dto.setTurnoId(grupo.getTurno() != null ? grupo.getTurno().getTurnoId() : null);
        dto.setTurno(grupo.getTurno() != null ? grupo.getTurno().getNombre() : null);        dto.setEspecialidad(grupo.getEspecialidad() != null ? grupo.getEspecialidad().getNombre() : null);
        dto.setCapacidad(grupo.getCapacidad());
        dto.setActivo(grupo.getActivo());

        // Agregar datos del semestre
        if (grupo.getSemestre() != null) {
            dto.setSemestreId(grupo.getSemestre().getSemestreId());
            dto.setSemestreNombre(grupo.getSemestre().getNombre());
        }

        return dto;
    }

    private GrupoDetalleDTO toDetalleDTO(Grupo grupo) {
        GrupoDetalleDTO dto = new GrupoDetalleDTO();
        dto.setId(grupo.getGrupoId());
        dto.setNombre(grupo.getNombre());
        dto.setGrado(grupo.getGrado());
        dto.setTurnoId(grupo.getTurno() != null ? grupo.getTurno().getTurnoId() : null);
        dto.setTurnoNombre(grupo.getTurno() != null ? grupo.getTurno().getNombre() : null);
        dto.setEspecialidadId(grupo.getEspecialidad() != null ? grupo.getEspecialidad().getEspecialidadId() : null);
        dto.setEspecialidadNombre(grupo.getEspecialidad() != null ? grupo.getEspecialidad().getNombre() : null);
        dto.setCapacidad(grupo.getCapacidad());
        dto.setActivo(grupo.getActivo());

        // 🔥 Agregar datos del semestre
        if (grupo.getSemestre() != null) {
            dto.setSemestreId(grupo.getSemestre().getSemestreId());
        }

        return dto;
    }
}
