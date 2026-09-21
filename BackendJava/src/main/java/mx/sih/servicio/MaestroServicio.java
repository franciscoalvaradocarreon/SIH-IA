package mx.sih.servicio;

import java.io.IOException;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.MaestroCrearDTO;
import mx.sih.modelo.dto.MaestroDTO;
import mx.sih.modelo.dto.MaestroDetalleDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Maestro;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.DisponibilidadMaestroRepositorio;
import mx.sih.repositorio.MaestroRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaestroServicio {

    private final MaestroRepositorio maestroRepositorio;
    private final ArchivoServicio archivoServicio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;

    public MaestroServicio(MaestroRepositorio maestroRepositorio,
                           ArchivoServicio archivoServicio,
                           SemestreRepositorio semestreRepositorio,
                           TurnoRepositorio turnoRepositorio,
                           AsignacionRepositorio asignacionRepositorio,
                           DisponibilidadMaestroRepositorio disponibilidadRepositorio) {
        this.maestroRepositorio = maestroRepositorio;
        this.archivoServicio = archivoServicio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
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

    public Page<MaestroDTO> listarMaestros(Pageable pageable, String busqueda,
                                            Long semestreId, Long turnoId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = busqueda == null ? "" : busqueda.trim();

        Page<Maestro> pagina = maestroRepositorio
                .findByEscuelaIdAndBusquedaAndSemestreId(
                        escuelaId, busquedaNormalizada, semestreId, turnoId, pageable);
        return pagina.map(this::toDTO);
    }

    public MaestroDTO obtenerMaestro(Long id) {
        Long escuelaId = getEscuelaId();
        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Maestro no encontrado con ID: " + id));
        return toDTO(maestro);
    }

    @Transactional
    public MaestroDTO crearMaestro(MaestroCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        // 🔥 COHERENCIA: el turno debe pertenecer al mismo semestre
        validarCoherenciaSemestreTurno(semestre, turno);

        // 🔥 UNICIDAD: nombre + apellidos por (semestre, turno)
        if (maestroRepositorio.existsByNombreApellidosAndSemestreIdAndTurnoId(
                dto.getNombre(), dto.getApellidos(),
                semestre.getSemestreId(), turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "MAESTRO_NOMBRE_DUPLICADO",
                    "Ya existe un maestro con el nombre '" +
                    dto.getNombre() + " " + dto.getApellidos() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        // Guardar la foto si se agregó
        String fotoUrl = null;
        if (dto.getFotoArchivo() != null && !dto.getFotoArchivo().isEmpty()) {
            try {
                fotoUrl = archivoServicio.guardarArchivo(dto.getFotoArchivo(), "maestro_");
            } catch (IOException e) {
                throw new NegocioExcepcion("Error al guardar la foto: " + e.getMessage());
            }
        }

        Maestro maestro = new Maestro();
        maestro.setNombre(dto.getNombre());
        maestro.setApellidos(dto.getApellidos());
        maestro.setEmail(dto.getEmail());
        maestro.setTelefono(dto.getTelefono());
        maestro.setTitulo(dto.getTitulo());
        maestro.setApodo(dto.getApodo());
        maestro.setFotoUrl(fotoUrl != null ? fotoUrl : dto.getFotoUrl());

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);
        maestro.setEscuela(escuela);
        maestro.setSemestre(semestre);
        maestro.setTurno(turno);   // 🔥 asignar turno
        maestro.setActivo(true);

        Maestro guardado = maestroRepositorio.save(maestro);
        return toDTO(guardado);
    }

    @Transactional
    public MaestroDTO actualizarMaestro(Long id, MaestroCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Maestro no encontrado con ID: " + id));

        validarCoherenciaSemestreTurno(semestre, turno);

        if (maestroRepositorio.existsByNombreApellidosAndSemestreIdAndTurnoIdAndIdNot(
                dto.getNombre(), dto.getApellidos(),
                semestre.getSemestreId(), turno.getTurnoId(), id)) {
            throw new NegocioExcepcion(
                    "MAESTRO_NOMBRE_DUPLICADO",
                    "Ya existe otro maestro con el nombre '" +
                    dto.getNombre() + " " + dto.getApellidos() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        // Foto
        if (dto.getFotoArchivo() != null && !dto.getFotoArchivo().isEmpty()) {
            if (maestro.getFotoUrl() != null) {
                archivoServicio.eliminarArchivo(maestro.getFotoUrl());
            }
            try {
                String nuevaFotoUrl = archivoServicio.guardarArchivo(dto.getFotoArchivo(), "maestro_");
                maestro.setFotoUrl(nuevaFotoUrl);
            } catch (IOException e) {
                throw new NegocioExcepcion("Error al guardar la foto: " + e.getMessage());
            }
        } else if (dto.getFotoUrl() != null) {
            maestro.setFotoUrl(dto.getFotoUrl());
        }

        maestro.setNombre(dto.getNombre());
        maestro.setApellidos(dto.getApellidos());
        maestro.setEmail(dto.getEmail());
        maestro.setTelefono(dto.getTelefono());
        maestro.setTitulo(dto.getTitulo());
        maestro.setApodo(dto.getApodo());
        maestro.setSemestre(semestre);
        maestro.setTurno(turno);   // 🔥 actualizar turno

        Maestro actualizado = maestroRepositorio.save(maestro);
        return toDTO(actualizado);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Maestro no encontrado con ID: " + id));
        maestro.setActivo(activo);
        maestroRepositorio.save(maestro);
    }

    @Transactional
    public void eliminarMaestro(Long id) {
        Long escuelaId = getEscuelaId();

        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Maestro no encontrado con ID: " + id));

        // 1. Verificar asignaciones
        long asignaciones = asignacionRepositorio.countByMaestroId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "MAESTRO_EN_USO",
                    "No se puede eliminar al maestro '" + maestro.getNombreCompleto() +
                    "' porque tiene " + asignaciones +
                    " asignación(es) activa(s). Elimina primero las asignaciones " +
                    "o desactiva al maestro en lugar de eliminarlo."
            );
        }

        // 2. Verificar disponibilidad registrada
        long disponibilidad = disponibilidadRepositorio.countByMaestroId(id);
        if (disponibilidad > 0) {
            throw new NegocioExcepcion(
                    "MAESTRO_EN_USO",
                    "No se puede eliminar al maestro '" + maestro.getNombreCompleto() +
                    "' porque tiene " + disponibilidad +
                    " registro(s) de disponibilidad. Elimínala primero desde la " +
                    "pantalla de disponibilidad o desactiva al maestro."
            );
        }

        // 3. Sin dependencias: eliminar foto y luego el registro
        if (maestro.getFotoUrl() != null) {
            archivoServicio.eliminarArchivo(maestro.getFotoUrl());
        }

        maestroRepositorio.delete(maestro);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /**
     * El turno debe pertenecer al mismo semestre que el maestro.
     * Sin esto, se podrían crear maestros del turno Matutino con
     * semestre_id = 2 pero turno.semestre_id = 1 (inconsistente).
     */
    private void validarCoherenciaSemestreTurno(Semestre semestre, Turno turno) {
        if (turno.getSemestre() == null) {
            throw new NegocioExcepcion(
                    "El turno seleccionado no tiene semestre asignado");
        }
        if (!turno.getSemestre().getSemestreId().equals(semestre.getSemestreId())) {
            throw new NegocioExcepcion(
                    "El turno '" + turno.getNombre() +
                    "' pertenece al semestre '" + turno.getSemestre().getNombre() +
                    "', no a '" + semestre.getNombre() + "'");
        }
    }

    private MaestroDTO toDTO(Maestro maestro) {
        MaestroDTO dto = new MaestroDTO();
        dto.setId(maestro.getMaestroId());
        dto.setNombre(maestro.getNombre());
        dto.setApellidos(maestro.getApellidos());
        dto.setNombreCompleto(maestro.getNombreCompleto());
        dto.setEmail(maestro.getEmail());
        dto.setTelefono(maestro.getTelefono());
        dto.setFotoUrl(maestro.getFotoUrl());
        dto.setTitulo(maestro.getTitulo());
        dto.setApodo(maestro.getApodo());
        dto.setActivo(maestro.getActivo());

        if (maestro.getSemestre() != null) {
            dto.setSemestreId(maestro.getSemestre().getSemestreId());
            dto.setSemestreNombre(maestro.getSemestre().getNombre());
        }
        if (maestro.getTurno() != null) {
            dto.setTurnoId(maestro.getTurno().getTurnoId());
            dto.setTurnoNombre(maestro.getTurno().getNombre());
        }

        return dto;
    }

    private MaestroDetalleDTO toDetalleDTO(Maestro maestro) {
        MaestroDetalleDTO dto = new MaestroDetalleDTO();
        dto.setId(maestro.getMaestroId());
        dto.setNombre(maestro.getNombre());
        dto.setApellidos(maestro.getApellidos());
        dto.setEmail(maestro.getEmail());
        dto.setTelefono(maestro.getTelefono());
        dto.setFotoUrl(maestro.getFotoUrl());
        dto.setTitulo(maestro.getTitulo());
        dto.setApodo(maestro.getApodo());
        dto.setActivo(maestro.getActivo());
        return dto;
    }
}