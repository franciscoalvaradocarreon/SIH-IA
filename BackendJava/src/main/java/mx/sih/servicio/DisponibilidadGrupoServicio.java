package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.DisponibilidadGrupoCrearDTO;
import mx.sih.modelo.dto.DisponibilidadGrupoDTO;
import mx.sih.modelo.dto.ImportarDisponibilidadDTO;
import mx.sih.modelo.dto.ResultadoImportacionDisponibilidadDTO;
import mx.sih.modelo.entidad.*;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DisponibilidadGrupoServicio {

    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadGrupoServicio.class);

    private final DisponibilidadGrupoRepositorio disponibilidadRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;

    public DisponibilidadGrupoServicio(DisponibilidadGrupoRepositorio disponibilidadRepositorio,
                                        GrupoRepositorio grupoRepositorio,
                                        TurnoHorarioRepositorio turnoHorarioRepositorio,
                                        SemestreRepositorio semestreRepositorio,
                                        TurnoRepositorio turnoRepositorio) {
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.grupoRepositorio = grupoRepositorio;
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

    /**
     * 🔥 Obtener disponibilidad por grupo y semestre
     */
    public List<DisponibilidadGrupoDTO> obtenerDisponibilidadPorGrupo(Long grupoId, Long semestreId) {
        Long escuelaId = getEscuelaId();

        if (semestreId == null) {
            Semestre semestre = semestreRepositorio
                    .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
            semestreId = semestre.getSemestreId();
        }

        List<DisponibilidadGrupo> lista = disponibilidadRepositorio
                .findByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 🔥 Obtener por ID
     */
    public DisponibilidadGrupoDTO obtenerDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadGrupo d = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!d.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }
        return toDTO(d);
    }

    /**
     * 🔥 Crear o actualizar disponibilidad
     */
    @Transactional
    public DisponibilidadGrupoDTO guardarDisponibilidad(DisponibilidadGrupoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        // Validar semestre
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        // Validar grupo
        Grupo grupo = grupoRepositorio.findByIdAndEscuelaId(dto.getGrupoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Grupo no encontrado con ID: " + dto.getGrupoId()));

        // Validar turno_horario
        TurnoHorario turnoHorario = turnoHorarioRepositorio.findByIdAndEscuelaId(dto.getTurnoHorarioId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Horario no encontrado con ID: " + dto.getTurnoHorarioId()));

        // Validar que el turno_horario pertenezca al turno del grupo
        if (!turnoHorario.getTurno().getTurnoId().equals(grupo.getTurno().getTurnoId())) {
            throw new NegocioExcepcion("El bloque horario no pertenece al turno del grupo");
        }

        // Validar semestre del turno_horario
        if (!turnoHorario.getSemestre().getSemestreId().equals(dto.getSemestreId())) {
            throw new NegocioExcepcion("El bloque horario no pertenece al semestre seleccionado");
        }
        
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        // Buscar si ya existe
        DisponibilidadGrupo disponibilidad = disponibilidadRepositorio
                .findByGrupoIdAndTurnoHorarioIdAndSemestreId(
                        dto.getGrupoId(), dto.getTurnoHorarioId(), escuelaId, dto.getSemestreId())
                .orElse(new DisponibilidadGrupo());

        disponibilidad.setEscuela(escuela);
        disponibilidad.setGrupo(grupo);
        disponibilidad.setTurnoHorario(turnoHorario);
        disponibilidad.setSemestre(semestre);
        disponibilidad.setDisponible(dto.getDisponible());

        DisponibilidadGrupo guardado = disponibilidadRepositorio.save(disponibilidad);
        return toDTO(guardado);
    }

    /**
     * 🔥 Eliminar por ID
     */
    @Transactional
    public void eliminarDisponibilidad(Long id) {
        Long escuelaId = getEscuelaId();
        DisponibilidadGrupo d = disponibilidadRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Disponibilidad no encontrada con ID: " + id));

        if (!d.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a esta disponibilidad");
        }
        disponibilidadRepositorio.delete(d);
    }

    /**
     * 🔥 Eliminar todas las disponibilidades de un grupo en un semestre
     */
    @Transactional
    public void eliminarDisponibilidadPorGrupo(Long grupoId, Long semestreId) {
        Long escuelaId = getEscuelaId();
        disponibilidadRepositorio.deleteByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
    }

    
    /**
    * Cuenta los bloques disponibles configurados de un grupo en un semestre.
    */
   public long contarBloquesDisponibles(Long grupoId, Long semestreId) {
       Long escuelaId = getEscuelaId();

       if (semestreId == null) {
           Semestre semestre = semestreRepositorio
                   .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                   .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
           semestreId = semestre.getSemestreId();
       }

       return disponibilidadRepositorio
               .countDisponiblesByGrupoIdAndSemestreId(grupoId, escuelaId, semestreId);
   }
    
    
    // ================== CONVERSIÓN ==================

    // ============================================================
    // IMPORTACION DESDE OTRO SEMESTRE
    // ============================================================

    /**
     * Trae la disponibilidad de los grupos de ESTE turno desde otro semestre.
     *
     * <p>Es el gemelo de la importacion de maestros: la disponibilidad es "grupo x bloque", asi que
     * hay que emparejar dos cosas:
     *
     * <ol>
     *   <li>El <b>grupo</b>: se busca en el semestre de destino uno con el mismo nombre <b>en el mismo
     *       turno</b> (los bloques del grupo son los de su turno).</li>
     *   <li>El <b>bloque</b>: el bloque del turno de destino del mismo dia que empieza a la misma
     *       hora.</li>
     * </ol>
     *
     * <p>Lo que no se pueda emparejar se salta y se informa; lo que ya exista en el destino se
     * respeta. Todo en UNA transaccion.
     */
    @Transactional
    public ResultadoImportacionDisponibilidadDTO importarDisponibilidad(ImportarDisponibilidadDTO dto) {
        Long escuelaId = getEscuelaId();

        // --- 1. El turno de destino manda ---
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

        List<DisponibilidadGrupo> fuente = disponibilidadRepositorio
                .findBySemestreIdAndTurnoId(origen.getSemestreId(), turnoOrigen.getTurnoId());
        if (fuente.isEmpty()) {
            throw new NegocioExcepcion("El turno '" + destino.getNombre() + "' del semestre '"
                    + origen.getNombre() + "' no tiene disponibilidad que traer");
        }

        // --- 2. Indices del DESTINO: bloques del turno y grupos del turno ---
        Map<String, TurnoHorario> bloquesDestino = new HashMap<>();
        for (TurnoHorario th : turnoHorarioRepositorio
                .findByTurnoIdAndSemestreId(destino.getTurnoId(), semestreDestinoId)) {
            bloquesDestino.put(claveDeBloque(th), th);
        }

        Map<String, Grupo> gruposDestino = new HashMap<>();
        for (Grupo g : grupoRepositorio.findByEscuelaIdAndSemestreId(escuelaId, semestreDestinoId)) {
            if (g.getTurno() != null && g.getTurno().getTurnoId().equals(destino.getTurnoId())) {
                // Si hubiera dos grupos con el mismo nombre en el turno (distinta especialidad), se
                // toma el primero: la pantalla tampoco puede distinguirlos por nombre.
                gruposDestino.putIfAbsent(claveDeNombre(g.getNombre()), g);
            }
        }

        Set<String> yaConfiguradas = new HashSet<>();
        for (DisponibilidadGrupo d : disponibilidadRepositorio
                .findBySemestreIdAndTurnoId(semestreDestinoId, destino.getTurnoId())) {
            yaConfiguradas.add(d.getGrupo().getGrupoId() + "|" + d.getTurnoHorario().getId());
        }

        // --- 3. Copiar lo que se pueda emparejar ---
        int copiadas = 0;
        int filasSinGrupo = 0;
        int filasSinBloque = 0;
        int filasYaEstaban = 0;
        Set<String> gruposFaltantes = new LinkedHashSet<>();
        Set<String> bloquesFaltantes = new LinkedHashSet<>();
        Set<Long> duenosTocados = new LinkedHashSet<>();

        for (DisponibilidadGrupo original : fuente) {
            Grupo duenio = gruposDestino.get(claveDeNombre(original.getGrupo().getNombre()));
            if (duenio == null) {
                filasSinGrupo++;
                gruposFaltantes.add(original.getGrupo().getNombre());
                continue;
            }

            TurnoHorario bloque = bloquesDestino.get(claveDeBloque(original.getTurnoHorario()));
            if (bloque == null) {
                filasSinBloque++;
                bloquesFaltantes.add(etiquetaDeBloque(original.getTurnoHorario()));
                continue;
            }

            String clave = duenio.getGrupoId() + "|" + bloque.getId();
            if (yaConfiguradas.contains(clave)) {
                filasYaEstaban++;
                continue;
            }

            Escuela escuela = new Escuela();
            escuela.setEscuelaId(escuelaId);

            DisponibilidadGrupo nueva = new DisponibilidadGrupo();
            nueva.setEscuela(escuela);
            nueva.setGrupo(duenio);
            nueva.setTurnoHorario(bloque);
            nueva.setDisponible(original.getDisponible());
            nueva.setSemestre(destino.getSemestre());

            disponibilidadRepositorio.save(nueva);
            yaConfiguradas.add(clave);
            duenosTocados.add(duenio.getGrupoId());
            copiadas++;
        }

        // --- 4. Resumen por motivo ---
        List<String> omitidos = new ArrayList<>();
        if (filasSinGrupo > 0) {
            omitidos.add(filasSinGrupo + " filas de grupos que no estan en este turno: "
                    + muestra(gruposFaltantes));
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
                .append(duenosTocados.size() == 1 ? " grupo" : " grupos")
                .append(" del turno '").append(destino.getNombre())
                .append("' desde '").append(origen.getNombre()).append("'.");
        if (copiadas == 0) {
            mensaje.append(" No habia nada nuevo que traer.");
        }

        logger.info("Importacion de disponibilidad (grupos) del turno {}: {} filas, {} grupos, {} motivos",
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

    private DisponibilidadGrupoDTO toDTO(DisponibilidadGrupo d) {
        TurnoHorario th = d.getTurnoHorario();
        DisponibilidadGrupoDTO dto = new DisponibilidadGrupoDTO();
        dto.setId(d.getDisponibilidadGrupoId());
        dto.setGrupoId(d.getGrupo().getGrupoId());
        dto.setGrupoNombre(d.getGrupo().getNombre());
        dto.setTurnoHorarioId(th.getId());
        dto.setDiaSemana(th.getDiaSemana());
        dto.setHoraInicio(th.getHoraInicio().toString());
        dto.setHoraFin(th.getHoraFin().toString());
        dto.setDisponible(d.getDisponible());
        if (d.getSemestre() != null) {
            dto.setSemestreId(d.getSemestre().getSemestreId());
            dto.setSemestreNombre(d.getSemestre().getNombre());
        }
        return dto;
    }
}