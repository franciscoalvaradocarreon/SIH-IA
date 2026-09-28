package mx.sih.servicio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.ImportarMaestrosDTO;
import mx.sih.modelo.dto.MaestroCrearDTO;
import mx.sih.modelo.dto.MaestroDTO;
import mx.sih.modelo.dto.MaestroDetalleDTO;
import mx.sih.modelo.dto.ResultadoImportacionMaestrosDTO;
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
    // IMPORTACION DESDE OTRO SEMESTRE
    // ============================================================

    /**
     * Trae los maestros de OTRO semestre de la misma escuela al semestre de destino.
     *
     * SOLO la tabla de maestros: datos de la persona y su turno. No se copia ninguna otra tabla; en
     * particular NO se copian asignaciones ni disponibilidad, que son de cada semestre y se manejan
     * en sus propias pantallas.
     *
     * El detalle que manda aqui, igual que en especialidades: sih.maestros.semestre_id y
     * sih.maestros.turno_id son NOT NULL y el turno tiene que ser del MISMO semestre que el maestro
     * (ver validarCoherenciaSemestreTurno). Asi que cada maestro que se trae necesita encontrar SU
     * turno en el semestre de destino, y se busca por NOMBRE: el turno_id es de otro semestre.
     *
     * Decisiones:
     *
     *  1. Se traen TODOS los maestros del semestre de origen: en la pantalla se elige el semestre,
     *     no maestro por maestro.
     *  2. Si el destino NO tiene un turno con ese nombre, el maestro se SALTA y se informa. Nunca se
     *     inventa un turno ni se apunta a uno de otro semestre.
     *  3. Si el destino YA tiene a ese maestro (mismo nombre y apellidos) en ese turno, se SALTA y se
     *     informa: no se sobrescribe ni se duplica. Es la misma unicidad que valida crearMaestro y
     *     que impone el indice unico de la tabla.
     *  4. La FOTO se copia a un archivo nuevo. Si las dos filas compartieran la misma URL, borrar un
     *     maestro borraria la foto del otro (eliminarMaestro borra el archivo del disco).
     *  5. Todo va en UNA transaccion: si algo falla a mitad, no queda un semestre copiado a medias.
     */
    @Transactional
    public ResultadoImportacionMaestrosDTO importarMaestros(ImportarMaestrosDTO dto) {
        Long escuelaId = getEscuelaId();

        if (dto.getSemestreOrigenId().equals(dto.getSemestreDestinoId())) {
            throw new NegocioExcepcion("El semestre de origen y el de destino son el mismo");
        }

        Semestre origen = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreOrigenId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No se encontro el semestre de origen"));
        Semestre destino = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreDestinoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No se encontro el semestre de destino"));

        // El TURNO que el usuario tiene seleccionado en la pantalla manda el alcance: solo se traen
        // los maestros del turno del ORIGEN que se llame igual.
        Turno turnoDestino = resolverTurnoDeLaImportacion(dto.getTurnoId(), destino, escuelaId);
        String nombreTurno = turnoDestino.getNombre().trim().toUpperCase(Locale.ROOT);

        List<Maestro> fuente = maestroRepositorio
                .findByEscuelaIdAndSemestreId(escuelaId, origen.getSemestreId())
                .stream()
                .filter(m -> m.getTurno() != null
                        && m.getTurno().getNombre() != null
                        && m.getTurno().getNombre().trim().toUpperCase(Locale.ROOT).equals(nombreTurno))
                .toList();
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + turnoDestino.getNombre()
                    + "' del semestre '" + origen.getNombre()
                    + "' no tiene maestros que traer");
        }

        int copiados = 0;
        int fotosCopiadas = 0;
        List<String> omitidos = new ArrayList<>();

        for (Maestro original : fuente) {
            String nombreCompleto = original.getNombreCompleto();

            if (maestroRepositorio.existsByNombreApellidosAndSemestreIdAndTurnoId(
                    original.getNombre(), original.getApellidos(),
                    destino.getSemestreId(), turnoDestino.getTurnoId())) {
                omitidos.add(nombreCompleto + " (ya existia en '" + turnoDestino.getNombre() + "')");
                continue;
            }

            Maestro copia = new Maestro();
            copia.setNombre(original.getNombre());
            copia.setApellidos(original.getApellidos());
            copia.setEmail(original.getEmail());
            copia.setTelefono(original.getTelefono());
            copia.setTitulo(original.getTitulo());
            copia.setApodo(original.getApodo());
            copia.setActivo(original.getActivo());

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);
            copia.setEscuela(escuela);
            copia.setSemestre(destino);
            copia.setTurno(turnoDestino);

            // La foto se duplica en disco. Si no se puede copiar (archivo borrado, formato raro),
            // copiarArchivo devuelve null y el maestro se trae igual, solo que sin foto.
            String fotoCopiada = archivoServicio.copiarArchivo(original.getFotoUrl(), "maestro_");
            if (fotoCopiada != null) {
                copia.setFotoUrl(fotoCopiada);
                fotosCopiadas++;
            }

            maestroRepositorio.save(copia);
            copiados++;
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se trajeron ").append(copiados)
                .append(copiados == 1 ? " maestro" : " maestros")
                .append(" del turno '").append(turnoDestino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (fotosCopiadas > 0) {
            mensaje.append(" Se copiaron ").append(fotosCopiadas)
                    .append(fotosCopiadas == 1 ? " foto." : " fotos.");
        }
        if (!omitidos.isEmpty()) {
            mensaje.append(" Se saltaron ").append(omitidos.size()).append(": ")
                    .append(String.join("; ", omitidos)).append(".");
        }

        return new ResultadoImportacionMaestrosDTO(copiados, omitidos, mensaje.toString());
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

    /**
     * Resuelve el turno de destino de una importacion desde otro semestre.
     *
     * El turno lo elige el usuario en la pantalla y TIENE que ser del semestre de destino: sin esta
     * comprobacion se podria importar hacia un turno de otro semestre y romper la coherencia
     * semestre-turno que valida crearMaestro.
     */
    private Turno resolverTurnoDeLaImportacion(Long turnoId, Semestre destino, Long escuelaId) {
        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Turno no encontrado o no pertenece a esta escuela"));
        if (turno.getSemestre() == null
                || !turno.getSemestre().getSemestreId().equals(destino.getSemestreId())) {
            throw new NegocioExcepcion("El turno '" + turno.getNombre()
                    + "' no pertenece al semestre '" + destino.getNombre() + "'");
        }
        return turno;
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