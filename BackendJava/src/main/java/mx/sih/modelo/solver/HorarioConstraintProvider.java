// mx.sih.modelo.solver.HorarioConstraintProvider.java
package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.Joiners;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.TurnoHorario;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;


public class HorarioConstraintProvider implements ConstraintProvider {

    /**
     * Máximo gap (minutos) entre el FIN de una clase y el INICIO de la
     * siguiente para considerarlas "continuas".
     *
     * Debe ser >= la duración del descanso real. Si tus descansos son de 20 min,
     * 30 está bien. Si son de 10 min, usa 15.
     */
    private static final long TOLERANCIA_CONTINUIDAD_MINUTOS = 30;

    /**
     * Duración esperada de un bloque de clase (minutos).
     * Típico: 50 min. Ajústalo si tu institución usa bloques distintos.
     */
    private static final long DURACION_BLOQUE_MINUTOS = 50;

    public HorarioConstraintProvider() {
        // Sin logs de construcción: el provider se instancia una vez por SolverFactory.
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // HARD
                conflictoMaestro(factory),
                conflictoAula(factory),
                conflictoGrupo(factory),
                horasAsignadas(factory),
                disponibilidadMaestro(factory),
                disponibilidadGrupo(factory),
                bloqueDelTurnoDelGrupo(factory),
                respetarDistribucion(factory),
                horasContinuas(factory),
                // SOFT
                evitarHuecos(factory),
        };
    }

    // ==================== HARD ====================

    private Constraint conflictoMaestro(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getMaestroId),
                        Joiners.equal(AsignacionHorario::getBloqueHorario))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Conflicto de maestro");
    }

    private Constraint conflictoAula(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getAulaId),
                        Joiners.equal(AsignacionHorario::getBloqueHorario))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Conflicto de aula");
    }

    private Constraint conflictoGrupo(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getGrupoId),
                        Joiners.equal(AsignacionHorario::getBloqueHorario))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Conflicto de grupo");
    }

    private Constraint horasAsignadas(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() == null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Horas sin asignar");
    }

    private Constraint disponibilidadMaestro(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .ifNotExists(DisponibilidadMaestro.class,
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getMaestroId(),
                                (DisponibilidadMaestro d) -> d.getMaestro().getMaestroId()),
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getBloqueHorario().getId(),
                                (DisponibilidadMaestro d) -> d.getTurnoHorario().getId()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Maestro no disponible");
    }

    private Constraint disponibilidadGrupo(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .ifNotExists(DisponibilidadGrupo.class,
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getGrupoId(),
                                (DisponibilidadGrupo d) -> d.getGrupo().getGrupoId()),
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getBloqueHorario().getId(),
                                (DisponibilidadGrupo d) -> d.getTurnoHorario().getId()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Grupo no disponible en este bloque");
    }

    private Constraint respetarDistribucion(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .groupBy(AsignacionHorario::getAsignacionId,
                        AsignacionHorario::getDistribucion,
                        ConstraintCollectors.toList())
                .filter((id, dist, lista) -> dist != null && !dist.isEmpty()
                        && !cumpleDistribucion(dist, lista))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Respetar distribución");
    }

    private Constraint horasContinuas(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .groupBy(AsignacionHorario::getAsignacionId,
                        AsignacionHorario::getDistribucion,
                        ConstraintCollectors.toList())
                .filter((id, dist, lista) -> {
                    if (dist == null || dist.isEmpty()) return false;
                    boolean requiereContinuidad = Arrays.stream(dist.split(","))
                            .map(String::trim)
                            .mapToInt(Integer::parseInt)
                            .anyMatch(h -> h > 1);
                    return requiereContinuidad && tieneHorasNoContinuas(lista);
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (id, dist, lista) -> contarHorasNoContinuas(lista))
                .asConstraint("Horas continuas (HARD)");
    }

    private Constraint bloqueDelTurnoDelGrupo(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .filter(a -> {
                    Long turnoBloque = a.getBloqueHorario().getTurno() != null
                            ? a.getBloqueHorario().getTurno().getTurnoId()
                            : null;
                    return turnoBloque != null && !turnoBloque.equals(a.getTurnoId());
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Bloque no pertenece al turno del grupo");
    }

    // ==================== SOFT ====================

    private Constraint evitarHuecos(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .groupBy(AsignacionHorario::getGrupoId,
                        ConstraintCollectors.toList())
                .filter((id, lista) -> lista.size() > 1 && tieneHuecos(lista))
                .penalize(HardSoftScore.ONE_SOFT,
                        (id, lista) -> contarHuecos(lista))
                .asConstraint("Evitar huecos");
    }

    // ==================== DISTRIBUCIÓN ====================

    private boolean cumpleDistribucion(String distribucion, List<AsignacionHorario> bloques) {
        int[] esperado = parsearDistribucion(distribucion);
        Map<Integer, Integer> porDia = contarHorasPorDia(bloques);

        List<Integer> dias = new ArrayList<>(porDia.keySet());
        Collections.sort(dias);

        if (dias.size() != esperado.length) return false;

        int[] esperadoOrdenado = Arrays.copyOf(esperado, esperado.length);
        Arrays.sort(esperadoOrdenado);

        int[] realOrdenado = dias.stream()
                .mapToInt(porDia::get)
                .sorted()
                .toArray();

        return Arrays.equals(esperadoOrdenado, realOrdenado);
    }

    private int[] parsearDistribucion(String d) {
        return Arrays.stream(d.split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private Map<Integer, Integer> contarHorasPorDia(List<AsignacionHorario> bloques) {
        Map<Integer, Integer> mapa = new HashMap<>();
        for (AsignacionHorario a : bloques) {
            if (a.getBloqueHorario() != null) {
                int dia = a.getBloqueHorario().getDiaSemana();
                mapa.merge(dia, 1, Integer::sum);
            }
        }
        return mapa;
    }

    // ==================== CONTINUIDAD ====================

    /**
     * Cuenta pares de clases consecutivas del mismo día cuyo gap (fin de la
     * anterior -> inicio de la siguiente) supera la tolerancia configurada.
     *
     * Si el gap es menor o igual a TOLERANCIA_CONTINUIDAD_MINUTOS, se asume
     * que es un descanso y NO se penaliza.
     */
    private int contarHorasNoContinuas(List<AsignacionHorario> lista) {
        Map<Integer, List<TurnoHorario>> porDia = new HashMap<>();
        for (AsignacionHorario a : lista) {
            if (a.getBloqueHorario() != null) {
                int dia = a.getBloqueHorario().getDiaSemana();
                porDia.computeIfAbsent(dia, k -> new ArrayList<>())
                        .add(a.getBloqueHorario());
            }
        }

        int noContinuas = 0;
        for (List<TurnoHorario> bloques : porDia.values()) {
            bloques.sort(Comparator.comparing(TurnoHorario::getHoraInicio));
            for (int i = 1; i < bloques.size(); i++) {
                LocalTime finAnterior = bloques.get(i - 1).getHoraFin();
                LocalTime inicioSiguiente = bloques.get(i).getHoraInicio();
                long gapMin = Duration.between(finAnterior, inicioSiguiente).toMinutes();
                if (gapMin > TOLERANCIA_CONTINUIDAD_MINUTOS) {
                    noContinuas++;
                }
            }
        }
        return noContinuas;
    }

    private boolean tieneHorasNoContinuas(List<AsignacionHorario> lista) {
        return contarHorasNoContinuas(lista) > 0;
    }

    // ==================== HUECOS ====================

    /**
     * Cuenta huecos usando horas reales.
     *
     * Un "hueco" es un bloque de clase LIBRE entre dos clases asignadas del
     * mismo día. Se calcula contando cuántos bloques de clase caben en el gap:
     *
     *     huecos = round((gap - tolerancia_descanso) / duracion_bloque)
     *
     * Ejemplos con descanso=20, duración=50:
     *     gap = 20  -> 0 huecos (es un descanso, no un hueco)
     *     gap = 50  -> 1 hueco  (un bloque libre)
     *     gap = 100 -> 2 huecos (dos bloques libres)
     *     gap = 120 -> 2 huecos (dos bloques + un poco mas; se redondea)
     */
    private int contarHuecos(List<AsignacionHorario> lista) {
        Map<Integer, List<TurnoHorario>> porDia = new HashMap<>();
        for (AsignacionHorario a : lista) {
            if (a.getBloqueHorario() != null) {
                int dia = a.getBloqueHorario().getDiaSemana();
                porDia.computeIfAbsent(dia, k -> new ArrayList<>())
                        .add(a.getBloqueHorario());
            }
        }

        int huecos = 0;
        for (List<TurnoHorario> bloques : porDia.values()) {
            if (bloques.size() < 2) continue;
            bloques.sort(Comparator.comparing(TurnoHorario::getHoraInicio));

            for (int i = 1; i < bloques.size(); i++) {
                LocalTime finAnterior = bloques.get(i - 1).getHoraFin();
                LocalTime inicioSiguiente = bloques.get(i).getHoraInicio();
                long gapMin = Duration.between(finAnterior, inicioSiguiente).toMinutes();

                // Descontar la tolerancia (descanso permitido) antes de calcular huecos.
                long gapEfectivo = gapMin - TOLERANCIA_CONTINUIDAD_MINUTOS;
                if (gapEfectivo > 0) {
                    long bloquesLibres = Math.round((double) gapEfectivo / DURACION_BLOQUE_MINUTOS);
                    huecos += (int) Math.max(0, bloquesLibres);
                }
            }
        }
        return huecos;
    }

    private boolean tieneHuecos(List<AsignacionHorario> lista) {
        return contarHuecos(lista) > 0;
    }
}