package mx.sih.servicio;

import java.util.List;
import java.util.Locale;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.TipoEspacioCrearDTO;
import mx.sih.modelo.dto.TipoEspacioDTO;
import mx.sih.modelo.entidad.TipoEspacio;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.TipoEspacioRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogo de laboratorios y talleres de un turno.
 *
 * <p>Es por escuela, semestre y TURNO, como aulas, grupos o materias: cada turno tiene su propia
 * lista y puede tener tipos distintos al otro turno del mismo semestre. El semestre se deduce del
 * turno, asi que la pantalla solo manda el turno.
 *
 * <p>Todas las operaciones comprueban que el turno pertenece a la escuela activa del token: un id
 * de otro plantel no puede servir para nada.
 */
@Service
public class TipoEspacioServicio {

    private final TipoEspacioRepositorio tipoEspacioRepositorio;
    private final TurnoRepositorio turnoRepositorio;

    public TipoEspacioServicio(TipoEspacioRepositorio tipoEspacioRepositorio,
                               TurnoRepositorio turnoRepositorio) {
        this.tipoEspacioRepositorio = tipoEspacioRepositorio;
        this.turnoRepositorio = turnoRepositorio;
    }

    @Transactional(readOnly = true)
    public List<TipoEspacioDTO> listar(Long turnoId, boolean soloActivos) {
        turnoDeLaEscuela(turnoId);
        List<TipoEspacio> lista = soloActivos
                ? tipoEspacioRepositorio.findByTurnoIdAndActivoTrueOrderByClaseAscNombreAsc(turnoId)
                : tipoEspacioRepositorio.findByTurnoIdOrderByClaseAscNombreAsc(turnoId);
        return lista.stream().map(this::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public TipoEspacioDTO obtener(Long id, Long turnoId) {
        return aDTO(buscar(id, turnoId));
    }

    @Transactional
    public TipoEspacioDTO crear(TipoEspacioCrearDTO dto) {
        Turno turno = turnoDeLaEscuela(dto.getTurnoId());
        String clase = normalizarClase(dto.getClase());
        String nombre = normalizarNombre(dto.getNombre());

        if (tipoEspacioRepositorio.existsByTurnoIdAndClaseAndNombreIgnoreCase(
                turno.getTurnoId(), clase, nombre)) {
            throw new NegocioExcepcion("tipo_espacio_duplicado",
                    "Ya existe un tipo con ese nombre en este turno.");
        }

        TipoEspacio tipo = new TipoEspacio();
        tipo.setEscuela(turno.getSemestre().getEscuela());
        tipo.setSemestre(turno.getSemestre());
        tipo.setTurnoId(turno.getTurnoId());
        tipo.setClase(clase);
        tipo.setNombre(nombre);
        tipo.setActivo(dto.getActivo() == null || dto.getActivo());
        return aDTO(tipoEspacioRepositorio.save(tipo));
    }

    @Transactional
    public TipoEspacioDTO actualizar(Long id, TipoEspacioCrearDTO dto) {
        TipoEspacio tipo = buscar(id, dto.getTurnoId());
        String clase = normalizarClase(dto.getClase());
        String nombre = normalizarNombre(dto.getNombre());

        boolean cambiaNombreOClase = !nombre.equalsIgnoreCase(tipo.getNombre())
                || !clase.equalsIgnoreCase(tipo.getClase());
        if (cambiaNombreOClase && tipoEspacioRepositorio.existsByTurnoIdAndClaseAndNombreIgnoreCase(
                tipo.getTurnoId(), clase, nombre)) {
            throw new NegocioExcepcion("tipo_espacio_duplicado",
                    "Ya existe un tipo con ese nombre en este turno.");
        }

        tipo.setClase(clase);
        tipo.setNombre(nombre);
        if (dto.getActivo() != null) {
            tipo.setActivo(dto.getActivo());
        }
        return aDTO(tipoEspacioRepositorio.save(tipo));
    }

    /**
     * Baja logica. No se borra la fila porque las aulas del turno apuntan a ella: borrarla dejaria
     * aulas sin tipo y un taller de mecanica pasaria a ser un aula normal, que es peor.
     */
    @Transactional
    public TipoEspacioDTO desactivar(Long id, Long turnoId) {
        TipoEspacio tipo = buscar(id, turnoId);
        tipo.setActivo(false);
        return aDTO(tipoEspacioRepositorio.save(tipo));
    }

    private TipoEspacio buscar(Long id, Long turnoId) {
        if (id == null) {
            throw new NegocioExcepcion("tipo_espacio_inexistente", "El tipo de espacio no existe.");
        }
        turnoDeLaEscuela(turnoId);
        return tipoEspacioRepositorio
                .findByTipoEspacioIdAndTurnoId(id, turnoId)
                .orElseThrow(() -> new NegocioExcepcion("tipo_espacio_inexistente",
                        "El tipo de espacio no existe en este turno."));
    }

    private Turno turnoDeLaEscuela(Long turnoId) {
        if (turnoId == null) {
            throw new NegocioExcepcion("turno_requerido", "Falta indicar el turno.");
        }
        Turno turno = turnoRepositorio.findById(turnoId)
                .orElseThrow(() -> new NegocioExcepcion("turno_inexistente",
                        "El turno indicado no existe."));
        Long escuelaActiva = EscuelaContexto.getEscuelaId();
        if (escuelaActiva != null && turno.getSemestre() != null
                && turno.getSemestre().getEscuela() != null
                && !escuelaActiva.equals(turno.getSemestre().getEscuela().getEscuelaId())) {
            throw new NegocioExcepcion("turno_ajeno",
                    "Ese turno no pertenece a la escuela activa.");
        }
        return turno;
    }

    private String normalizarClase(String clase) {
        String valor = clase == null ? "" : clase.trim().toUpperCase(Locale.ROOT);
        if (!TipoEspacio.CLASE_LABORATORIO.equals(valor) && !TipoEspacio.CLASE_TALLER.equals(valor)) {
            throw new NegocioExcepcion("clase_invalida",
                    "La clase debe ser LABORATORIO o TALLER.");
        }
        return valor;
    }

    private String normalizarNombre(String nombre) {
        String valor = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (valor.isEmpty()) {
            throw new NegocioExcepcion("nombre_requerido", "El nombre es obligatorio.");
        }
        return valor;
    }

    private TipoEspacioDTO aDTO(TipoEspacio tipo) {
        return new TipoEspacioDTO(
                tipo.getTipoEspacioId(),
                tipo.getSemestre() != null ? tipo.getSemestre().getSemestreId() : null,
                tipo.getTurnoId(),
                tipo.getClase(),
                tipo.getNombre(),
                tipo.getActivo());
    }
}
