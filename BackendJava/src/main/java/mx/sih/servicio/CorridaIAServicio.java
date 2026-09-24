package mx.sih.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.IntentoIA;
import mx.sih.modelo.dto.CorridaIADTO;
import mx.sih.modelo.entidad.CorridaIa;
import mx.sih.modelo.entidad.CorridaIaDetalle;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.repositorio.CorridaIaDetalleRepositorio;
import mx.sih.repositorio.CorridaIaRepositorio;

/**
 * Guarda corridas del generador IA y las aplica al horario vigente.
 *
 * <p>El problema que resuelve: el generador vive en memoria. Cuando el usuario lanza otra generacion,
 * la anterior se pierde, asi que no habia forma de comparar dos opciones. Ahora una corrida se puede
 * guardar con un nombre, seguir generando, y al final aplicar la que haya salido mejor.
 *
 * <p>Guardar y aplicar son cosas distintas a proposito:
 * <ul>
 *   <li>Guardar es inerte: escribe en {@code sih.corrida_ia} y no toca el horario que ve la escuela.</li>
 *   <li>Aplicar reescribe el horario vigente. Para eso NO se duplica la logica: se reconstruye el
 *       {@link IntentoIA} guardado y se llama a {@code HorarioIAServicio.registrar}, que ya valida
 *       contra el catalogo vivo y tiene sus comprobaciones probadas.</li>
 * </ul>
 */
@Service
public class CorridaIAServicio {

    private static final Logger logger = LoggerFactory.getLogger(CorridaIAServicio.class);

    private static final int MAX_NOMBRE = 120;
    private static final int MAX_NOTAS = 500;

    /**
     * Nombre del indice unico de {@code sih.corrida_ia}.
     *
     * <p>Se busca el texto directamente en el mensaje en vez de usar MensajeErrorUtil porque sus
     * expresiones estan afinadas para las constraints {@code no_solape_*}: P_CONSTRAINT busca la
     * palabra inglesa "constraint" y Postgres en espanol dice "restriccion de unicidad «...»". Con
     * este nombre el helper devolveria null y el usuario veria el error generico.
     */
    private static final String IDX_NOMBRE_UNICO = "corrida_ia_nombre_unico";

    private final CorridaIaRepositorio corridas;
    private final CorridaIaDetalleRepositorio detalles;
    private final HorarioIAServicio horarioIAServicio;

    public CorridaIAServicio(CorridaIaRepositorio corridas,
                             CorridaIaDetalleRepositorio detalles,
                             HorarioIAServicio horarioIAServicio) {
        this.corridas = corridas;
        this.detalles = detalles;
        this.horarioIAServicio = horarioIAServicio;
    }

    // ============================================================
    // GUARDAR
    // ============================================================

    /**
     * Guarda un intento completo (su solucion y sus metricas) con el nombre que le ponga el usuario.
     *
     * @param usuario correo de quien la guarda. Es el mismo criterio que usa el modulo IA para
     *                "solicitadoPor", asi que no hace falta resolver un id.
     */
    @Transactional
    public CorridaIADTO guardar(Long escuelaId, Long semestreId, Long turnoId,
                                IntentoIA intento, String nombre, String notas, String usuario) {
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        if (semestreId == null) {
            throw new NegocioExcepcion("sin_semestre", "No se ha indicado el semestre de la corrida");
        }
        if (intento == null) {
            throw new NegocioExcepcion("sin_intento", "No hay ningun intento que guardar.");
        }
        if (intento.getHoras() <= 0) {
            throw new NegocioExcepcion("intento_vacio",
                    "El intento " + intento.getNumero() + " no coloca ninguna hora: no hay nada que guardar.");
        }

        String nombreLimpio = limpiar(nombre, MAX_NOMBRE, "nombre");
        if (nombreLimpio == null) {
            throw new NegocioExcepcion("nombre_vacio",
                    "Ponle un nombre a la corrida para poder reconocerla despues.");
        }
        String notasLimpias = limpiar(notas, MAX_NOTAS, "notas");

        CorridaIa corrida = new CorridaIa();
        // Solo el id: no hace falta leer la escuela ni el semestre para escribir la clave foranea.
        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);
        corrida.setEscuela(escuela);
        Semestre semestre = new Semestre();
        semestre.setSemestreId(semestreId);
        corrida.setSemestre(semestre);

        corrida.setTurnoId(turnoId);
        corrida.setNombre(nombreLimpio);
        corrida.setNotas(notasLimpias);
        corrida.setAsesor(intento.getAsesor());
        corrida.setNumeroIntento(intento.getNumero());
        corrida.setGeneradoEn(intento.getGeneradoEn());
        corrida.setCreadoPor(usuario);

        copiarMetricas(intento, corrida);
        corrida.setTotalFilas(intento.getFilas().size());
        corrida.setTotalPendientes(intento.getPendientes().size());
        corrida.setTotalProblemas(intento.getProblemas().size());
        // Los problemas se guardan como texto para poder explicar en la pantalla por que una corrida
        // no se puede aplicar, en vez de rechazarla sin decir nada.
        corrida.setProblemas(intento.getProblemas().isEmpty()
                ? null
                : String.join("\n", intento.getProblemas()));

        try {
            corridas.saveAndFlush(corrida);
        } catch (DataIntegrityViolationException e) {
            throw traducir(e, nombreLimpio);
        }

        List<CorridaIaDetalle> filas = new ArrayList<>(intento.getFilas().size());
        for (IntentoIA.Fila f : intento.getFilas()) {
            CorridaIaDetalle d = new CorridaIaDetalle();
            d.setCorrida(corrida);
            d.setAsignacionId(f.getAsignacionId());
            d.setTurnoHorarioId(f.getTurnoHorarioId());
            d.setMaestroId(f.getMaestroId());
            d.setAulaId(f.getAulaId());
            filas.add(d);
        }
        detalles.saveAll(filas);
        detalles.flush();

        logger.info("Corrida IA guardada: '{}' (id {}, {} bloques, {} h, semestre {})",
                nombreLimpio, corrida.getCorridaIaId(), filas.size(), intento.getHoras(), semestreId);

        return aDTO(corrida, filas.size());
    }

    // ============================================================
    // LISTAR
    // ============================================================

    /** Las corridas guardadas de una escuela y semestre, con el recuento real de bloques. */
    @Transactional(readOnly = true)
    public List<CorridaIADTO> listar(Long escuelaId, Long semestreId) {
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        if (semestreId == null) {
            return List.of();
        }

        List<CorridaIa> lista = corridas.listar(escuelaId, semestreId);
        if (lista.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> reales = contarBloquesReales(lista);

        List<CorridaIADTO> salida = new ArrayList<>(lista.size());
        for (CorridaIa c : lista) {
            salida.add(aDTO(c, reales.getOrDefault(c.getCorridaIaId(), 0L)));
        }
        return salida;
    }

    /**
     * Cuantos bloques tiene guardados cada corrida, en UNA consulta.
     *
     * <p>Sirve para detectar corridas incompletas: si se borra una asignacion del catalogo, la clave
     * foranea en cascada se lleva por delante filas del detalle y la opcion deja de representar lo que
     * era. Aplicarla tal cual daria un horario a medias.
     */
    private Map<Long, Long> contarBloquesReales(List<CorridaIa> lista) {
        List<Long> ids = new ArrayList<>(lista.size());
        for (CorridaIa c : lista) {
            ids.add(c.getCorridaIaId());
        }

        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : detalles.contarPorCorridas(ids)) {
            mapa.put((Long) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    // ============================================================
    // APLICAR
    // ============================================================

    /**
     * Aplica una corrida guardada al horario VIGENTE (version = 1), reemplazando el de los grupos del
     * alcance. Es la operacion destructiva: lo que hubiera antes se pierde.
     *
     * <p>No se reimplementa nada: se reconstruye el intento guardado y se llama a
     * {@code registrar}, que recarga el catalogo dentro de su propia transaccion y valida que las
     * asignaciones y los bloques sigan existiendo.
     */
    @Transactional
    public HorarioIAServicio.RegistroIA aplicar(Long escuelaId, Long corridaId) {
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        if (corridaId == null) {
            throw new NegocioExcepcion("sin_corrida", "No se ha indicado la corrida que aplicar.");
        }

        CorridaIa corrida = corridas.buscarPorIdYEscuela(corridaId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("corrida_no_encontrada",
                        "Esa corrida guardada no existe o no pertenece a la escuela activa."));

        List<CorridaIaDetalle> filas = detalles.listarPorCorrida(corridaId);
        if (filas.isEmpty()) {
            throw new NegocioExcepcion("corrida_vacia",
                    "La corrida '" + corrida.getNombre() + "' no tiene ningun bloque guardado.");
        }

        // Comprobacion de integridad: si el detalle se quedo corto respecto a lo que se guardo, la
        // corrida ya no es la que era y aplicarla daria un horario a medias.
        if (corrida.getTotalFilas() != null && filas.size() != corrida.getTotalFilas()) {
            throw new NegocioExcepcion("corrida_incompleta",
                    "La corrida '" + corrida.getNombre() + "' esta incompleta: se guardaron "
                            + corrida.getTotalFilas() + " bloques y solo quedan " + filas.size()
                            + ". Algo del catalogo se borro despues de guardarla.");
        }

        IntentoIA intento = new IntentoIA();
        intento.setNumero(corrida.getNumeroIntento() == null ? 0 : corrida.getNumeroIntento());
        intento.setAsesor(corrida.getAsesor());
        intento.setHoras(corrida.getHoras() == null ? 0 : corrida.getHoras());
        // Si la corrida tenia problemas duros, registrar() la rechaza con su propio mensaje: no se
        // duplica aqui esa comprobacion.
        intento.setProblemas(leerProblemas(corrida));

        List<IntentoIA.Fila> filasIntento = new ArrayList<>(filas.size());
        for (CorridaIaDetalle d : filas) {
            filasIntento.add(new IntentoIA.Fila(
                    d.getAsignacionId(), d.getTurnoHorarioId(), d.getMaestroId(), d.getAulaId()));
        }
        intento.setFilas(filasIntento);

        HorarioIAServicio.RegistroIA registro = horarioIAServicio.registrar(
                corrida.getSemestre().getSemestreId(), corrida.getTurnoId(), intento);

        logger.info("Corrida IA '{}' (id {}) aplicada al horario vigente: {} bloques, {} h",
                corrida.getNombre(), corridaId, registro.filas(), registro.horas());

        return registro;
    }

    // ============================================================
    // AUXILIARES
    // ============================================================

    private static void copiarMetricas(IntentoIA intento, CorridaIa corrida) {
        corrida.setMilisegundos(intento.getMilisegundos());
        corrida.setHoras(intento.getHoras());
        corrida.setHorasDemandadas(intento.getHorasDemandadas());
        corrida.setSesionesLargas(intento.getSesionesLargas());
        corrida.setSesionesLargasPendientes(intento.getSesionesLargasPendientes());
        corrida.setArranquesTarde(intento.getArranquesTarde());
        corrida.setCastigoHuecos(intento.getCastigoHuecos());
        corrida.setAdyacencias(intento.getAdyacencias());
        corrida.setMateriasCompletas(intento.getMateriasCompletas());
        corrida.setMateriasTotales(intento.getMateriasTotales());
        corrida.setMedium(intento.getMedium());
    }

    /** Reconstruye la lista de problemas duros desde el texto guardado. */
    private static List<String> leerProblemas(CorridaIa corrida) {
        List<String> lista = new ArrayList<>();
        String texto = corrida.getProblemas();
        if (texto == null || texto.isBlank()) {
            return lista;
        }
        for (String linea : texto.split("\n")) {
            if (!linea.isBlank()) {
                lista.add(linea.trim());
            }
        }
        return lista;
    }

    /** Quita espacios y valida el largo. Devuelve null si queda vacio. */
    private static String limpiar(String valor, int max, String campo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > max) {
            throw new NegocioExcepcion("texto_largo",
                    "El campo " + campo + " no puede pasar de " + max + " caracteres.");
        }
        return limpio;
    }

    /** Traduce la violacion del indice de nombre unico a un mensaje entendible. */
    private static NegocioExcepcion traducir(DataIntegrityViolationException e, String nombre) {
        String mensaje = e.getMostSpecificCause() == null ? null : e.getMostSpecificCause().getMessage();
        if (mensaje != null && mensaje.contains(IDX_NOMBRE_UNICO)) {
            return new NegocioExcepcion("nombre_duplicado",
                    "Ya existe una corrida guardada con el nombre '" + nombre
                            + "' en este semestre. Ponle otro nombre para poder distinguirlas.");
        }
        logger.error("Corrida IA: error de integridad al guardar '{}'", nombre, e);
        return new NegocioExcepcion("integridad_datos",
                "No se pudo guardar la corrida por un problema de integridad de datos.", e);
    }

    private static CorridaIADTO aDTO(CorridaIa c, long filasGuardadas) {
        int problemas = c.getTotalProblemas() == null ? 0 : c.getTotalProblemas();
        boolean incompleta = c.getTotalFilas() != null && filasGuardadas != c.getTotalFilas();

        String motivo;
        if (problemas > 0) {
            motivo = "Tiene " + problemas + " problema(s) duro(s): un horario asi no se puede registrar.";
        } else if (incompleta) {
            motivo = "Incompleta: se guardaron " + c.getTotalFilas() + " bloques y quedan "
                    + filasGuardadas + ". Algo del catalogo se borro despues de guardarla.";
        } else if (filasGuardadas == 0) {
            motivo = "No tiene ningun bloque guardado.";
        } else {
            motivo = null;
        }

        return new CorridaIADTO(
                c.getCorridaIaId(),
                c.getNombre(),
                c.getNotas(),
                c.getTurnoId(),
                c.getAsesor(),
                c.getGeneradoEn(),
                c.getCreado(),
                c.getCreadoPor(),
                c.getMilisegundos(),
                c.getHoras(),
                c.getHorasDemandadas(),
                c.getSesionesLargas(),
                c.getSesionesLargasPendientes(),
                c.getArranquesTarde(),
                c.getCastigoHuecos(),
                c.getAdyacencias(),
                c.getMateriasCompletas(),
                c.getMateriasTotales(),
                c.getMedium(),
                c.getTotalFilas(),
                c.getTotalPendientes(),
                c.getTotalProblemas(),
                filasGuardadas,
                motivo == null,
                motivo);
    }
}
