// mx.sih.servicio.DisponibilidadMaestroServicio.java
package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.DisponibilidadMaestroCrearDTO;
import mx.sih.modelo.dto.DisponibilidadMaestroDTO;
import mx.sih.modelo.dto.ImportarDisponibilidadDTO;
import mx.sih.modelo.dto.ResultadoImportacionDisponibilidadDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DisponibilidadMaestroServicio {

    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadMaestroServicio.class);

    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;
    private final MaestroRepositorio maestroRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;

    public DisponibilidadMaestroServicio(DisponibilidadMaestroRepositorio disponibilidadRepositorio,
                                         MaestroRepositorio maestroRepositorio,
                                         TurnoHorarioRepositorio turnoHorarioRepositorio,
                                         SemestreRepositorio semestreRepositorio,
                                         TurnoRepositorio turnoRepositorio) {
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.maestroRepositorio = maestroRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    // Listar disponibilidades paginadas
    public Page<DisponibilidadMaestroDTO> listarDisponibilidades(Pageable pageable, String busqueda, Long semestreId) {
        Long escuelaId = getEscuelaId();
        
        // Si no viene semestreId, usar el activo
        if (semestreId == null) {
            Semestre semestre = semestreRepositorio.findSemestreActual(escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestre.getSemestreId();
        }
        
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<DisponibilidadMaestro> pagina = disponibilidadRepositorio
                .buscarPorEscuelaYMaestroYSemestre(escuelaId, semestreId, busquedaNormalizada, pageable);
        return pagina.map(this::toDTO);
    }

    // Obtener disponibilidad por ID
    public DisponibilidadMaestroDTO obtenerDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!disponibilidad.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }

        return toDTO(disponibilidad);
    }

    // Obtener disponibilidad por maestro
    public List<DisponibilidadMaestroDTO> obtenerDisponibilidadPorMaestro(Long maestroId) {
        Long escuelaId = getEscuelaId();
        List<DisponibilidadMaestro> lista = disponibilidadRepositorio
                .findByMaestroId(maestroId, escuelaId);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Crear o actualizar disponibilidad
    @Transactional
    public DisponibilidadMaestroDTO guardarDisponibilidad(DisponibilidadMaestroCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado o no pertenece a la escuela"));
        
        // VERIFICAR QUE EL MAESTRO EXISTA
        Maestro maestro = maestroRepositorio.findByIdAndEscuelaId(dto.getMaestroId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Maestro no encontrado con ID: " + dto.getMaestroId()));

        // VERIFICAR QUE EL TURNO_HORARIO EXISTA Y PERTENEZCA A LA ESCUELA ACTIVA
         TurnoHorario turnoHorario = turnoHorarioRepositorio
            .findByIdAndEscuelaId(dto.getTurnoHorarioId(), escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Horario no encontrado con ID: " + dto.getTurnoHorarioId()));

        // Validación 1: el bloque horario debe pertenecer al semestre indicado
        if (!turnoHorario.getSemestre().getSemestreId().equals(dto.getSemestreId())) {
            throw new NegocioExcepcion(
                    "El bloque horario no pertenece al semestre seleccionado");
        }
        
        if (maestro.getTurno() == null || turnoHorario.getTurno() == null) {
            throw new NegocioExcepcion(
                    "No se puede validar el turno del maestro o del bloque horario");
        }
        if (!turnoHorario.getTurno().getTurnoId().equals(maestro.getTurno().getTurnoId())) {
            throw new NegocioExcepcion(
                    "El bloque horario pertenece al turno '" + turnoHorario.getTurno().getNombre() +
                    "', pero el maestro '" + maestro.getNombreCompleto() +
                    "' pertenece al turno '" + maestro.getTurno().getNombre() +
                    "'. Asigna únicamente bloques del turno del maestro.");
        }
        
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        // Buscar si ya existe
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio
                .findByMaestroIdAndTurnoHorarioId(dto.getMaestroId(), dto.getTurnoHorarioId(), escuelaId)
                .orElse(new DisponibilidadMaestro());

        disponibilidad.setEscuela(escuela);
        disponibilidad.setMaestro(maestro);
        disponibilidad.setTurnoHorario(turnoHorario);
        disponibilidad.setDisponible(dto.getDisponible());
        disponibilidad.setSemestre(semestre);

        DisponibilidadMaestro guardado = disponibilidadRepositorio.save(disponibilidad);
        return toDTO(guardado);
    }

    // Eliminar disponibilidad
    @Transactional
    public void eliminarDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadMaestro disponibilidad = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!disponibilidad.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }

        disponibilidadRepositorio.delete(disponibilidad);
    }

    // Eliminar todas las disponibilidades de un maestro
    @Transactional
    public void eliminarDisponibilidadPorMaestro(Long maestroId) {
        Long escuelaId = getEscuelaId();
        List<DisponibilidadMaestro> lista = disponibilidadRepositorio
                .findByMaestroId(maestroId, escuelaId);
        disponibilidadRepositorio.deleteAll(lista);
    }

    // ============================================================
    // IMPORTACION DESDE OTRO SEMESTRE
    // ============================================================

    /**
     * Trae la disponibilidad de los maestros de ESTE turno desde otro semestre.
     *
     * <p>La disponibilidad es "maestro x bloque", asi que al cambiar de semestre hay que emparejar
     * DOS cosas:
     *
     * <ol>
     *   <li>El <b>maestro</b>: se busca en el semestre de destino uno con el mismo nombre y apellidos
     *       <b>en el mismo turno</b> (un maestro pertenece a un turno, y sus bloques son los de ese
     *       turno).</li>
     *   <li>El <b>bloque</b>: se busca en el turno de destino el bloque del mismo dia que empieza a
     *       la misma hora.</li>
     * </ol>
     *
     * <p>Lo que no se pueda emparejar NO se inventa: se salta y se informa. Y lo que ya exista en el
     * destino NO se pisa: se respeta lo que el usuario ya tenga configurado alli.
     *
     * <p>Todo va en UNA transaccion: o entra la disponibilidad del turno entera, o no entra nada.
     */
    @Transactional
    public ResultadoImportacionDisponibilidadDTO importarDisponibilidad(ImportarDisponibilidadDTO dto) {
        Long escuelaId = getEscuelaId();

        // --- 1. El turno de destino manda: de el salen el semestre y los bloques validos ---
        Turno destino = turnoRepositorio.findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        if (destino.getSemestre() == null) {
            throw new NegocioExcepcion("El turno seleccionado no tiene semestre asignado");
        }
        Long semestreDestinoId = destino.getSemestre().getSemestreId();

        if (!dto.getSemestreDestinoId().equals(semestreDestinoId)) {
            throw new NegocioExcepcion("El turno '" + destino.getNombre()
                    + "' no pertenece al semestre destino indicado");
        }
        if (dto.getSemestreOrigenId().equals(semestreDestinoId)) {
            throw new NegocioExcepcion("El semestre de origen y el de destino son el mismo");
        }

        Semestre origen = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreOrigenId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No se encontro el semestre de origen"));

        Turno turnoOrigen = buscarTurnoPorNombre(escuelaId, origen, destino);

        List<DisponibilidadMaestro> fuente = disponibilidadRepositorio
                .findBySemestreIdAndTurnoId(origen.getSemestreId(), turnoOrigen.getTurnoId());
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + destino.getNombre() + "' del semestre '"
                    + origen.getNombre() + "' no tiene disponibilidad que traer");
        }

        // --- 2. Indices del DESTINO: bloques del turno y maestros del turno ---
        Map<String, TurnoHorario> bloquesDestino = new HashMap<>();
        for (TurnoHorario th : turnoHorarioRepositorio
                .findByTurnoIdAndSemestreId(destino.getTurnoId(), semestreDestinoId)) {
            bloquesDestino.put(claveDeBloque(th), th);
        }

        Map<String, Maestro> maestrosDestino = new HashMap<>();
        for (Maestro m : maestroRepositorio.findByEscuelaIdAndSemestreId(escuelaId, semestreDestinoId)) {
            if (m.getTurno() != null && m.getTurno().getTurnoId().equals(destino.getTurnoId())) {
                maestrosDestino.putIfAbsent(claveDeNombre(m.getNombre(), m.getApellidos()), m);
            }
        }

        Set<String> yaConfiguradas = new HashSet<>();
        for (DisponibilidadMaestro d : disponibilidadRepositorio
                .findBySemestreIdAndTurnoId(semestreDestinoId, destino.getTurnoId())) {
            yaConfiguradas.add(d.getMaestro().getMaestroId() + "|" + d.getTurnoHorario().getId());
        }

        // --- 3. Copiar lo que se pueda emparejar ---
        int copiadas = 0;
        int filasSinMaestro = 0;
        int filasSinBloque = 0;
        int filasYaEstaban = 0;
        Set<String> maestrosFaltantes = new LinkedHashSet<>();
        Set<String> bloquesFaltantes = new LinkedHashSet<>();
        Set<Long> duenosTocados = new LinkedHashSet<>();

        for (DisponibilidadMaestro original : fuente) {
            Maestro duenio = maestrosDestino.get(claveDeNombre(
                    original.getMaestro().getNombre(), original.getMaestro().getApellidos()));
            if (duenio == null) {
                filasSinMaestro++;
                maestrosFaltantes.add(original.getMaestro().getNombreCompleto());
                continue;
            }

            TurnoHorario bloque = bloquesDestino.get(claveDeBloque(original.getTurnoHorario()));
            if (bloque == null) {
                filasSinBloque++;
                bloquesFaltantes.add(etiquetaDeBloque(original.getTurnoHorario()));
                continue;
            }

            String clave = duenio.getMaestroId() + "|" + bloque.getId();
            if (yaConfiguradas.contains(clave)) {
                filasYaEstaban++;
                continue;
            }

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);

            DisponibilidadMaestro nueva = new DisponibilidadMaestro();
            nueva.setEscuela(escuela);
            nueva.setMaestro(duenio);
            nueva.setTurnoHorario(bloque);
            nueva.setDisponible(original.getDisponible());
            nueva.setSemestre(destino.getSemestre());

            disponibilidadRepositorio.save(nueva);
            yaConfiguradas.add(clave);
            duenosTocados.add(duenio.getMaestroId());
            copiadas++;
        }

        // --- 4. Resumen por motivo (no fila por fila: pueden ser miles) ---
        List<String> omitidos = new ArrayList<>();
        if (filasSinMaestro > 0) {
            omitidos.add(filasSinMaestro + " filas de maestros que no estan en este turno: "
                    + muestra(maestrosFaltantes));
        }
        if (filasSinBloque > 0) {
            omitidos.add(filasSinBloque + " filas de bloques que no existen en este turno: "
                    + muestra(bloquesFaltantes));
        }
        if (filasYaEstaban > 0) {
            omitidos.add(filasYaEstaban + " filas que ya estaban configuradas en este semestre");
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se trajeron ").append(copiadas)
                .append(copiadas == 1 ? " casilla" : " casillas")
                .append(" de disponibilidad de ").append(duenosTocados.size())
                .append(duenosTocados.size() == 1 ? " maestro" : " maestros")
                .append(" del turno '").append(destino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (copiadas == 0) {
            mensaje.append(" No habia nada nuevo que traer.");
        }

        logger.info("Importacion de disponibilidad del turno {}: {} filas, {} maestros, {} motivos",
                destino.getNombre(), copiadas, duenosTocados.size(), omitidos.size());

        return new ResultadoImportacionDisponibilidadDTO(copiadas, duenosTocados.size(), omitidos,
                mensaje.toString());
    }

    /** Busca en el semestre de origen el turno que se llama igual que el de destino. */
    private Turno buscarTurnoPorNombre(Long escuelaId, Semestre origen, Turno destino) {
        String buscado = destino.getNombre() == null
                ? ""
                : destino.getNombre().trim().toUpperCase(Locale.ROOT);

        return turnoRepositorio.findByEscuelaIdAndSemestreId(escuelaId, origen.getSemestreId())
                .stream()
                .filter(t -> t.getNombre() != null
                        && t.getNombre().trim().toUpperCase(Locale.ROOT).equals(buscado))
                .findFirst()
                .orElseThrow(() -> new NegocioExcepcion("El semestre '" + origen.getNombre()
                        + "' no tiene un turno llamado '" + destino.getNombre() + "'"));
    }

    /** Clave del bloque para emparejar entre semestres: mismo dia y misma hora de inicio. */
    private static String claveDeBloque(TurnoHorario th) {
        return th.getDiaSemana() + "|" + th.getHoraInicio();
    }

    /** Clave para emparejar dueños por nombre (sin mayusculas ni espacios de sobra). */
    private static String claveDeNombre(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            if (parte != null) {
                sb.append(parte.trim().toLowerCase(Locale.ROOT)).append('|');
            }
        }
        return sb.toString();
    }

    /** Etiqueta legible de un bloque: "Lunes 07:00-07:50". */
    private static String etiquetaDeBloque(TurnoHorario th) {
        return nombreDia(th.getDiaSemana()) + " " + th.getHoraInicio() + "-" + th.getHoraFin();
    }

    private static String nombreDia(Integer diaSemana) {
        String[] dias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
        if (diaSemana == null) {
            return "Día ?";
        }
        return diaSemana >= 1 && diaSemana <= 5 ? dias[diaSemana - 1] : "Día " + diaSemana;
    }

    /** Primeros 5 elementos de una coleccion, con "y N mas" si hay mas. */
    private static String muestra(Set<String> valores) {
        List<String> lista = new ArrayList<>(valores);
        int mostrados = Math.min(5, lista.size());
        String texto = String.join(", ", lista.subList(0, mostrados));
        return lista.size() > mostrados ? texto + " y " + (lista.size() - mostrados) + " mas" : texto;
    }

    private DisponibilidadMaestroDTO toDTO(DisponibilidadMaestro disponibilidad) {
        TurnoHorario th = disponibilidad.getTurnoHorario();
        DisponibilidadMaestroDTO dto = new DisponibilidadMaestroDTO();
        dto.setId(disponibilidad.getDisponibilidadMaestroId());
        dto.setMaestroId(disponibilidad.getMaestro().getMaestroId());
        dto.setMaestroNombre(disponibilidad.getMaestro().getNombreCompleto());
        dto.setTurnoHorarioId(th.getId());
        dto.setDiaSemana(th.getDiaSemana());
        dto.setHoraInicio(th.getHoraInicio().toString());
        dto.setHoraFin(th.getHoraFin().toString());
        dto.setDisponible(disponibilidad.getDisponible());
        if (disponibilidad.getSemestre() != null) {
            dto.setSemestreId(disponibilidad.getSemestre().getSemestreId());
            dto.setSemestreNombre(disponibilidad.getSemestre().getNombre());
        }
        return dto;
    }
}