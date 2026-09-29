package mx.sih.servicio;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.ReglasIA;
import mx.sih.modelo.dto.EstadisticasHorarioDTO;
import mx.sih.modelo.entidad.Asignacion;
import mx.sih.modelo.entidad.Horario;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.modelo.entidad.TurnoHorario;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoHorarioRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estadisticas de un horario YA guardado, con las MISMAS formulas del motor IA.
 *
 * <p>Es el recuadro del tablero manual: al mover clases a mano interesa saber si la edicion mejoro
 * o empeoro lo que habia dejado la corrida. Por eso no se inventan metricas nuevas: se replican las
 * de {@link mx.sih.ia.GeneradorIA} (arranques, huecos con su tope por dia, adyacencias, desvio del
 * patron y score MEDIUM con los pesos de {@link ReglasIA}), y se anaden las comprobaciones DURAS
 * (choques de grupo/maestro/aula, materia repetida el mismo dia, clases en descanso y materias con
 * mas horas colocadas que las contratadas) para que un descuadre se vea de inmediato.
 *
 * <p>Detalle de fidelidad: el motor mide los huecos y los arranques sobre los bloques de CLASE del
 * turno (los descansos no cuentan como hueco) y considera que dos bloques siguen en la misma sesion
 * si el siguiente empieza dentro de {@link ReglasIA#TOLERANCIA_CONTIGUIDAD_MIN} minutos del anterior
 * (es decir, el descanso de 20 minutos NO parte la sesion).
 */
@Service
public class EstadisticasHorarioServicio {

    /** Cuantas materias con horas pendientes se devuelven en la lista. */
    private static final int MAX_PENDIENTES = 12;

    private final HorarioRepositorio horarioRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final SemestreRepositorio semestreRepositorio;

    public EstadisticasHorarioServicio(HorarioRepositorio horarioRepositorio,
                                       AsignacionRepositorio asignacionRepositorio,
                                       TurnoHorarioRepositorio turnoHorarioRepositorio,
                                       TurnoRepositorio turnoRepositorio,
                                       SemestreRepositorio semestreRepositorio) {
        this.horarioRepositorio = horarioRepositorio;
        this.asignacionRepositorio = asignacionRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    /**
     * Calcula las estadisticas del horario del turno indicado.
     *
     * @param semestreId semestre del horario (el activo en la pantalla)
     * @param turnoId    turno cuyo horario se mide
     */
    @Transactional(readOnly = true)
    public EstadisticasHorarioDTO calcular(Long semestreId, Long turnoId) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findByIdAndEscuelaId(turnoId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));
        Semestre semestre = semestreRepositorio.findByIdAndEscuelaId(semestreId, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        // ── La rejilla del turno: bloques de CLASE por dia, en orden ──
        List<TurnoHorario> bloques = turnoHorarioRepositorio
                .findByTurnoIdAndSemestreId(turnoId, semestreId);
        Map<Integer, List<TurnoHorario>> rejillaPorDia = new TreeMap<>();
        for (TurnoHorario b : bloques) {
            if (Boolean.TRUE.equals(b.getDescanso())) {
                continue;   // los descansos no son casillas de clase
            }
            rejillaPorDia.computeIfAbsent(b.getDiaSemana(), k -> new ArrayList<>()).add(b);
        }
        for (List<TurnoHorario> dia : rejillaPorDia.values()) {
            dia.sort(Comparator.comparing(TurnoHorario::getOrden,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }

        // ── Las filas del horario de este turno ──
        List<Horario> filas = new ArrayList<>();
        for (Horario h : horarioRepositorio.findByEscuelaIdAndSemestreId(escuelaId, semestreId)) {
            if (turnoId.equals(h.getTurnoId())) {
                filas.add(h);
            }
        }

        // ── Las materias (asignaciones) del turno ──
        List<Asignacion> asignaciones = asignacionRepositorio
                .buscarPorEscuelaYSemestreYFiltros(escuelaId, semestreId, null, null, turnoId, null, "",
                        PageRequest.of(0, 2000))
                .getContent().stream()
                .filter(a -> !Boolean.FALSE.equals(a.getActivo()))
                .toList();

        // ── Indices de las filas: por asignacion+dia y por grupo+dia ──
        Map<Long, Integer> colocadasPorAsignacion = new HashMap<>();
        Map<Long, Map<Integer, List<Horario>>> porAsignacionDia = new HashMap<>();
        Map<Long, Map<Integer, List<Horario>>> porGrupoDia = new HashMap<>();
        Map<String, List<Horario>> porGrupoMateriaDia = new HashMap<>();
        Set<Long> grupos = new HashSet<>();

        for (Horario h : filas) {
            Long asignacionId = h.getAsignacion().getAsignacionId();
            Long grupoId = h.getGrupo().getGrupoId();
            Integer dia = h.getTurnoHorario().getDiaSemana();
            Long materiaId = h.getAsignacion().getMateria().getMateriaId();

            colocadasPorAsignacion.merge(asignacionId, 1, Integer::sum);
            grupos.add(grupoId);
            porAsignacionDia.computeIfAbsent(asignacionId, k -> new HashMap<>())
                    .computeIfAbsent(dia, k -> new ArrayList<>()).add(h);
            porGrupoDia.computeIfAbsent(grupoId, k -> new HashMap<>())
                    .computeIfAbsent(dia, k -> new ArrayList<>()).add(h);
            porGrupoMateriaDia.computeIfAbsent(grupoId + "|" + materiaId + "|" + dia,
                    k -> new ArrayList<>()).add(h);
        }

        // ── Cobertura y forma por materia ──
        int horasDemandadas = 0;
        int materiasTotales = 0;
        int materiasCompletas = 0;
        int horasPendientes = 0;
        int sesionesLargas = 0;
        int sesionesLargasPendientes = 0;
        int desvioDistribucion = 0;
        int excedeHoras = 0;
        List<EstadisticasHorarioDTO.Pendiente> pendientes = new ArrayList<>();

        for (Asignacion a : asignaciones) {
            int contratadas = a.getHoras() == null ? 0 : a.getHoras();
            if (contratadas <= 0) {
                continue;   // sin horas contratadas no es una clase del horario
            }
            Long asignacionId = a.getAsignacionId();
            int colocadas = colocadasPorAsignacion.getOrDefault(asignacionId, 0);

            materiasTotales++;
            horasDemandadas += contratadas;
            if (colocadas >= contratadas) {
                materiasCompletas++;
            }
            if (colocadas > contratadas) {
                excedeHoras++;
            }

            List<Integer> tamanos = tamanosDeSesiones(porAsignacionDia.get(asignacionId));
            for (int tam : tamanos) {
                if (tam >= 2) {
                    sesionesLargas++;
                }
            }

            if (colocadas < contratadas) {
                int faltan = contratadas - colocadas;
                horasPendientes += faltan;
                sesionesLargasPendientes += largasPendientes(a, colocadas, tamanos);
                pendientes.add(new EstadisticasHorarioDTO.Pendiente(
                        asignacionId,
                        nombre(a.getGrupo() == null ? null : a.getGrupo().getNombre()),
                        nombre(a.getMateria() == null ? null : a.getMateria().getNombre()),
                        a.getMaestro() == null ? "" : nombreCompleto(a.getMaestro().getNombre(),
                                a.getMaestro().getApellidos()),
                        colocadas, contratadas, faltan));
            }

            // Desvio del patron: horas colocadas en cada dia con clase (igual que el motor).
            int[] patron = ReglasIA.parsearDistribucion(a.getDistribucion());
            if (patron.length > 0 && colocadas > 0) {
                desvioDistribucion += ReglasIA.desvioDistribucion(patron,
                        horasPorDia(porAsignacionDia.get(asignacionId)));
            }
        }

        // Se enseñan primero las que mas horas deben.
        pendientes.sort(Comparator.comparingInt(EstadisticasHorarioDTO.Pendiente::getFaltan).reversed());
        int totalPendientes = pendientes.size();
        if (pendientes.size() > MAX_PENDIENTES) {
            pendientes = new ArrayList<>(pendientes.subList(0, MAX_PENDIENTES));
        }

        // ── Forma por grupo y dia (arranques, huecos y adyacencias) ──
        int arranquesTarde = 0;
        int huecos = 0;
        int castigoHuecos = 0;
        int adyacencias = 0;

        for (Long grupoId : grupos) {
            Map<Integer, List<Horario>> delGrupo = porGrupoDia.getOrDefault(grupoId, Map.of());
            for (Map.Entry<Integer, List<TurnoHorario>> entrada : rejillaPorDia.entrySet()) {
                List<TurnoHorario> rejilla = entrada.getValue();
                List<Horario> delDia = delGrupo.getOrDefault(entrada.getKey(), List.of());
                if (delDia.isEmpty()) {
                    continue;
                }

                Map<Long, Horario> porBloque = new HashMap<>();
                for (Horario h : delDia) {
                    porBloque.put(h.getTurnoHorario().getId(), h);
                }

                int primera = -1;
                int ultima = -1;
                for (int p = 0; p < rejilla.size(); p++) {
                    if (porBloque.containsKey(rejilla.get(p).getId())) {
                        if (primera < 0) {
                            primera = p;
                        }
                        ultima = p;
                    }
                }
                if (primera < 0) {
                    continue;
                }
                if (primera != 0) {
                    arranquesTarde++;
                }

                int delDiaHuecos = 0;
                for (int p = primera; p < ultima; p++) {
                    if (!porBloque.containsKey(rejilla.get(p).getId())) {
                        delDiaHuecos++;
                    }
                }
                huecos += delDiaHuecos;
                castigoHuecos += Math.min(ReglasIA.PESO_HUECO * delDiaHuecos, ReglasIA.TOPE_HUECOS_DIA);

                for (int p = 0; p + 1 < rejilla.size(); p++) {
                    long separacion = Duration.between(rejilla.get(p).getHoraFin(),
                            rejilla.get(p + 1).getHoraInicio()).toMinutes();
                    if (separacion > ReglasIA.TOLERANCIA_ADYACENCIA_MIN) {
                        continue;
                    }
                    Horario antes = porBloque.get(rejilla.get(p).getId());
                    Horario despues = porBloque.get(rejilla.get(p + 1).getId());
                    if (antes != null && despues != null
                            && antes.getMaestroId() != null
                            && antes.getMaestroId().equals(despues.getMaestroId())
                            && !antes.getAsignacion().getMateria().getMateriaId()
                                    .equals(despues.getAsignacion().getMateria().getMateriaId())) {
                        adyacencias++;
                    }
                }
            }
        }

        // ── Reglas duras ──
        Map<String, Integer> ocupacion = new HashMap<>();
        int choquesGrupo = 0;
        int choquesMaestro = 0;
        int choquesAula = 0;
        int clasesEnDescanso = 0;
        for (Horario h : filas) {
            Long bloqueId = h.getTurnoHorario().getId();
            choquesGrupo += repetido(ocupacion, bloqueId + "|grupo|" + h.getGrupo().getGrupoId());
            choquesMaestro += repetido(ocupacion, bloqueId + "|maestro|" + h.getMaestroId());
            choquesAula += repetido(ocupacion, bloqueId + "|aula|"
                    + (h.getAula() == null ? "-" : h.getAula().getAulaId()));
            if (Boolean.TRUE.equals(h.getTurnoHorario().getDescanso())) {
                clasesEnDescanso++;
            }
        }

        int materiasRepetidasDia = 0;
        for (List<Horario> delPar : porGrupoMateriaDia.values()) {
            if (tamanosDeSesiones(Map.of(0, delPar)).size() > 1) {
                materiasRepetidasDia++;
            }
        }

        int problemas = choquesGrupo + choquesMaestro + choquesAula + materiasRepetidasDia
                + clasesEnDescanso + excedeHoras;

        // ── Score MEDIUM (mismos pesos que el motor) ──
        int horasColocadas = filas.size();
        int medium = ReglasIA.medium(horasColocadas, horasDemandadas, sesionesLargasPendientes,
                arranquesTarde, castigoHuecos, adyacencias, desvioDistribucion);

        EstadisticasHorarioDTO dto = new EstadisticasHorarioDTO();
        dto.setTurnoId(turnoId);
        dto.setTurnoNombre(turno.getNombre());
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        dto.setGrupos(grupos.size());
        dto.setHorasColocadas(horasColocadas);
        dto.setHorasDemandadas(horasDemandadas);
        dto.setCoberturaPorcentaje(horasDemandadas == 0 ? 0
                : (int) Math.round(100.0 * horasColocadas / horasDemandadas));
        dto.setMateriasCompletas(materiasCompletas);
        dto.setMateriasTotales(materiasTotales);
        dto.setHorasPendientes(horasPendientes);
        dto.setHuecos(huecos);
        dto.setCastigoHuecos(castigoHuecos);
        dto.setArranquesTarde(arranquesTarde);
        dto.setAdyacencias(adyacencias);
        dto.setDesvioDistribucion(desvioDistribucion);
        dto.setSesionesLargas(sesionesLargas);
        dto.setSesionesLargasPendientes(sesionesLargasPendientes);
        dto.setMedium(medium);
        dto.setChoquesGrupo(choquesGrupo);
        dto.setChoquesMaestro(choquesMaestro);
        dto.setChoquesAula(choquesAula);
        dto.setMateriasRepetidasDia(materiasRepetidasDia);
        dto.setClasesEnDescanso(clasesEnDescanso);
        dto.setExcedeHoras(excedeHoras);
        dto.setProblemas(problemas);
        dto.setPendientes(pendientes);
        dto.setMensaje(mensaje(dto, totalPendientes));
        return dto;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /** Frase del recuadro: cobertura, materias y, si las hay, horas pendientes y problemas. */
    private static String mensaje(EstadisticasHorarioDTO dto, int totalPendientes) {
        StringBuilder sb = new StringBuilder();
        sb.append(dto.getHorasColocadas()).append('/').append(dto.getHorasDemandadas())
                .append(" h (").append(dto.getCoberturaPorcentaje()).append(" %) · ")
                .append(dto.getMateriasCompletas()).append('/').append(dto.getMateriasTotales())
                .append(" materias completas");
        if (totalPendientes > 0) {
            sb.append(" · faltan ").append(dto.getHorasPendientes()).append(" h en ")
                    .append(totalPendientes).append(totalPendientes == 1 ? " materia" : " materias");
        }
        if (dto.getProblemas() > 0) {
            sb.append(" · ").append(dto.getProblemas()).append(" problema(s) por resolver");
        } else {
            sb.append(" · sin choques");
        }
        return sb.toString();
    }

    /**
     * Tamaños (en horas) de las sesiones de una asignacion: bloques seguidos del mismo dia mientras
     * el siguiente empiece dentro de la tolerancia de contiguidad del motor.
     */
    private static List<Integer> tamanosDeSesiones(Map<Integer, List<Horario>> porDia) {
        List<Integer> tamanos = new ArrayList<>();
        if (porDia == null) {
            return tamanos;
        }
        for (List<Horario> delDia : porDia.values()) {
            List<Horario> ordenadas = new ArrayList<>(delDia);
            ordenadas.sort(Comparator.comparing(h -> h.getTurnoHorario().getOrden(),
                    Comparator.nullsLast(Comparator.naturalOrder())));

            int tam = 0;
            LocalTime finAnterior = null;
            for (Horario h : ordenadas) {
                LocalTime inicio = h.getTurnoHorario().getHoraInicio();
                boolean sigue = finAnterior != null
                        && Duration.between(finAnterior, inicio).toMinutes()
                                <= ReglasIA.TOLERANCIA_CONTIGUIDAD_MIN;
                if (sigue) {
                    tam++;
                } else {
                    if (tam > 0) {
                        tamanos.add(tam);
                    }
                    tam = 1;
                }
                finAnterior = h.getTurnoHorario().getHoraFin();
            }
            if (tam > 0) {
                tamanos.add(tam);
            }
        }
        return tamanos;
    }

    /** Horas colocadas en cada dia con clase (lo que compara el desvio del patron). */
    private static int[] horasPorDia(Map<Integer, List<Horario>> porDia) {
        if (porDia == null || porDia.isEmpty()) {
            return new int[0];
        }
        List<Integer> dias = new ArrayList<>(porDia.keySet());
        dias.sort(Comparator.naturalOrder());
        int[] horas = new int[dias.size()];
        for (int i = 0; i < dias.size(); i++) {
            int suma = 0;
            for (int tam : tamanosDeSesiones(Map.of(dias.get(i), porDia.get(dias.get(i))))) {
                suma += tam;
            }
            horas[i] = suma;
        }
        return horas;
    }

    /**
     * Sesiones de 2+ h que quedaron sin colocar.
     *
     * <p>Se emparejan las piezas del patron (de mayor a menor) con las sesiones ya colocadas; cada
     * pieza de 2+ h que no encuentre hueco cuenta. Con la materia completa -el caso normal- el
     * resultado es 0, igual que en el motor.
     */
    private static int largasPendientes(Asignacion a, int colocadas, List<Integer> tamanosColocados) {
        int contratadas = a.getHoras() == null ? 0 : a.getHoras();
        int faltan = contratadas - colocadas;
        if (faltan <= 0) {
            return 0;
        }
        int[] patron = ReglasIA.parsearDistribucion(a.getDistribucion());
        if (patron.length == 0) {
            return faltan >= 2 ? 1 : 0;
        }

        int[] objetivo = ReglasIA.recortarPatron(patron, contratadas);
        List<Integer> libres = new ArrayList<>(tamanosColocados);
        libres.sort(Comparator.reverseOrder());

        int pendientesLargas = 0;
        for (int i = objetivo.length - 1; i >= 0; i--) {
            int pieza = objetivo[i];
            boolean encajada = false;
            for (int j = 0; j < libres.size(); j++) {
                if (libres.get(j) >= pieza) {
                    libres.set(j, 0);
                    encajada = true;
                    break;
                }
            }
            if (!encajada && pieza >= 2) {
                pendientesLargas++;
            }
        }
        return pendientesLargas;
    }

    /** Suma 1 al contador de esa clave y devuelve 1 si es la SEGUNDA vez (un choque). */
    private static int repetido(Map<String, Integer> contadores, String clave) {
        int antes = contadores.getOrDefault(clave, 0);
        contadores.put(clave, antes + 1);
        return antes == 1 ? 1 : 0;
    }

    private static String nombre(String valor) {
        return valor == null ? "" : valor;
    }

    private static String nombreCompleto(String nombre, String apellidos) {
        return (nombre(nombre) + " " + nombre(apellidos)).trim();
    }
}
