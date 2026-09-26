package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.excepcion.MensajeErrorUtil;
import mx.sih.modelo.entidad.*;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.ValidacionDTO;
import mx.sih.modelo.dto.ResultadoValidacionDTO;
import mx.sih.modelo.dto.CambioManualDTO;
import mx.sih.modelo.dto.SolicitudManualDTO;
import mx.sih.modelo.dto.ResultadoManualDTO;
import mx.sih.repositorio.*;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mx.sih.modelo.dto.AnalisisCuelloBotellaDTO;

@Service
public class HorarioServicio {

    private static final Logger logger = LoggerFactory.getLogger(HorarioServicio.class);

    private final AsignacionRepositorio asignacionRepositorio;
    private final TurnoHorarioRepositorio turnoHorarioRepositorio;
    private final HorarioRepositorio horarioRepositorio;
    private final GrupoRepositorio grupoRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final DisponibilidadMaestroRepositorio disponibilidadRepositorio;
    private final DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio;
    private final AulaRepositorio aulaRepositorio;

    public HorarioServicio(AsignacionRepositorio asignacionRepositorio,
                           TurnoHorarioRepositorio turnoHorarioRepositorio,
                           HorarioRepositorio horarioRepositorio,
                           GrupoRepositorio grupoRepositorio,
                           SemestreRepositorio semestreRepositorio,
                           DisponibilidadMaestroRepositorio disponibilidadRepositorio,
                           DisponibilidadGrupoRepositorio disponibilidadGrupoRepositorio,
                           AulaRepositorio aulaRepositorio) {
        this.asignacionRepositorio = asignacionRepositorio;
        this.turnoHorarioRepositorio = turnoHorarioRepositorio;
        this.horarioRepositorio = horarioRepositorio;
        this.grupoRepositorio = grupoRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.disponibilidadRepositorio = disponibilidadRepositorio;
        this.disponibilidadGrupoRepositorio = disponibilidadGrupoRepositorio;
        this.aulaRepositorio = aulaRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    private Semestre getSemestreActivo(Long escuelaId) {
        return semestreRepositorio
                .findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("No hay semestre activo"));
    }

    private Semestre resolverSemestre(Long escuelaId, Long semestreIdParam) {
        if (semestreIdParam != null) {
            return semestreRepositorio.findByIdAndEscuelaId(semestreIdParam, escuelaId)
                    .orElseThrow(() -> new NegocioExcepcion(
                            "Semestre no encontrado con ID: " + semestreIdParam));
        }
        return getSemestreActivo(escuelaId);
    }

    // ============================================================
    // CONSULTAS
    // ============================================================
    public List<HorarioDTO> obtenerHorarioGrupo(Long grupoId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByGrupoIdAndSemestreId(
                grupoId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<HorarioDTO> obtenerHorarioMaestro(Long maestroId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByMaestroIdAndSemestreId(
                maestroId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<HorarioDTO> obtenerHorarioAula(Long aulaId, Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByAulaIdAndSemestreId(
                aulaId, escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<HorarioDTO> obtenerTodosLosHorarios(Long semestreIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        List<Horario> horarios = horarioRepositorio.findByEscuelaIdAndSemestreId(
                escuelaId, semestre.getSemestreId());
        return horarios.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ============================================================
    // VALIDACIÓN PÚBLICA
    // ============================================================
    public ResultadoValidacionDTO validarFactibilidad(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        List<ValidacionDTO> validaciones = new ArrayList<>();

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);

        if (grupos.isEmpty()) {
            validaciones.add(ValidacionDTO.error("grupos", "Grupos activos",
                    turnoFiltro != null
                            ? "No hay grupos activos en el turno seleccionado"
                            : "No hay grupos activos en el semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        validaciones.add(ValidacionDTO.ok("grupos", "Grupos activos",
                grupos.size() + " grupos activos"));

        List<Asignacion> asignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            asignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        if (asignaciones.isEmpty()) {
            validaciones.add(ValidacionDTO.error("asignaciones", "Asignaciones configuradas",
                    "Ningún grupo tiene asignaciones en este semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        long totalHoras = asignaciones.stream().mapToLong(Asignacion::getHoras).sum();
        validaciones.add(ValidacionDTO.ok("asignaciones", "Asignaciones configuradas",
                asignaciones.size() + " asignaciones · " + totalHoras + " horas a planificar"));

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());

        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long tId : turnoIds) {
            bloques.addAll(turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(tId, semestreId));
        }

        if (bloques.isEmpty()) {
            validaciones.add(ValidacionDTO.error("bloques", "Bloques horarios del turno",
                    "Los turnos de los grupos no tienen bloques configurados en este semestre"));
            return new ResultadoValidacionDTO(validaciones, false, 1, 0);
        }
        validaciones.add(ValidacionDTO.ok("bloques", "Bloques horarios del turno",
                bloques.size() + " bloques de clase configurados"));

        List<DisponibilidadGrupo> dispGrupos = new ArrayList<>();
        List<String> gruposSinDisponibilidad = new ArrayList<>();
        for (Grupo g : grupos) {
            List<DisponibilidadGrupo> disp = disponibilidadGrupoRepositorio
                    .findDisponiblesByGrupoIdAndSemestreId(g.getGrupoId(), escuelaId, semestreId);
            if (disp.isEmpty()) {
                gruposSinDisponibilidad.add(g.getNombre());
            }
            dispGrupos.addAll(disp);
        }

        if (!gruposSinDisponibilidad.isEmpty()) {
            validaciones.add(ValidacionDTO.error("disponibilidad_grupos", "Disponibilidad de grupos",
                    "Los siguientes grupos no tienen bloques disponibles: " +
                            String.join(", ", gruposSinDisponibilidad)));
        } else {
            validaciones.add(ValidacionDTO.ok("disponibilidad_grupos", "Disponibilidad de grupos",
                    "Todos los grupos tienen bloques disponibles (" + dispGrupos.size() + " bloques en total)"));
        }

        // 🎯 CHEQUEO: suma de horas por grupo > bloques del grupo
        List<String> gruposSobrecargados = new ArrayList<>();
        Map<Long, Set<Long>> bloquesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            bloquesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }
        for (Grupo g : grupos) {
            int horasGrupo = asignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(g.getGrupoId()))
                    .mapToInt(Asignacion::getHoras)
                    .sum();
            int bloquesGrupo = bloquesPorGrupo.getOrDefault(g.getGrupoId(), Set.of()).size();
            if (horasGrupo > bloquesGrupo && bloquesGrupo > 0) {
                gruposSobrecargados.add(String.format(
                        "%s: %d horas vs %d bloques (faltan %d)",
                        g.getNombre(), horasGrupo, bloquesGrupo, horasGrupo - bloquesGrupo));
            }
        }
        if (!gruposSobrecargados.isEmpty()) {
            validaciones.add(ValidacionDTO.error("carga_grupos", "Carga total por grupo",
                    "Grupos con más horas asignadas que bloques disponibles:\n  " +
                            String.join("\n  ", gruposSobrecargados)));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_grupos", "Carga total por grupo",
                    "Ningún grupo está sobrecargado"));
        }

        // 🎯 CHEQUEO: suma de horas por materia > horas del catálogo
        List<String> excesoHoras = new ArrayList<>();
        Map<Long, Map<Long, List<Asignacion>>> porGrupoMateria = asignaciones.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getGrupo().getGrupoId(),
                        Collectors.groupingBy(a -> a.getMateria().getMateriaId())));

        for (var entryGrupo : porGrupoMateria.entrySet()) {
            for (var entryMat : entryGrupo.getValue().entrySet()) {
                List<Asignacion> lista = entryMat.getValue();
                if (lista.size() <= 1) continue;

                Asignacion ref = lista.get(0);
                int totalHorasAsignadas = lista.stream()
                        .mapToInt(Asignacion::getHoras)
                        .sum();
                Integer horasCatalogo = ref.getMateria().getHorasSemana();

                if (horasCatalogo == null || horasCatalogo <= 0) continue;

                if (totalHorasAsignadas > horasCatalogo) {
                    excesoHoras.add(String.format(
                            "%s · %s: %d asignaciones suman %d horas, catálogo permite %d",
                            ref.getGrupo().getNombre(),
                            ref.getMateria().getClave(),
                            lista.size(),
                            totalHorasAsignadas,
                            horasCatalogo));
                }
            }
        }
        if (!excesoHoras.isEmpty()) {
            validaciones.add(ValidacionDTO.error("exceso_horas_materia", "Horas por materia",
                    "Grupos con más horas asignadas que las permitidas por el catálogo:\n  " +
                            String.join("\n  ", excesoHoras)));
        } else {
            validaciones.add(ValidacionDTO.ok("exceso_horas_materia", "Horas por materia",
                    "Todas las materias respetan sus horas del catálogo"));
        }

        List<Long> maestroIds = asignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .toList();

        List<DisponibilidadMaestro> dispMaestros = new ArrayList<>();
        List<Long> maestrosSinDisponibilidad = new ArrayList<>();
        for (Long mId : maestroIds) {
            List<DisponibilidadMaestro> disp = disponibilidadRepositorio
                    .findDisponiblesByMaestroIdAndSemestreId(mId, escuelaId, semestreId);
            if (disp.isEmpty()) {
                maestrosSinDisponibilidad.add(mId);
            }
            dispMaestros.addAll(disp);
        }

        if (!maestrosSinDisponibilidad.isEmpty()) {
            List<String> nombres = asignaciones.stream()
                    .filter(a -> maestrosSinDisponibilidad.contains(a.getMaestro().getMaestroId()))
                    .map(a -> a.getMaestro().getNombreCompleto())
                    .distinct()
                    .toList();
            validaciones.add(ValidacionDTO.error("disponibilidad_maestros", "Disponibilidad de maestros",
                    "Sin disponibilidad: " + String.join(", ", nombres)));
        } else {
            validaciones.add(ValidacionDTO.ok("disponibilidad_maestros", "Disponibilidad de maestros",
                    "Todos los maestros (" + maestroIds.size() + ") tienen disponibilidad registrada"));
        }

        Set<String> maestroBloque = new HashSet<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            maestroBloque.add(dm.getMaestro().getMaestroId() + "-" + dm.getTurnoHorario().getId());
        }

        List<String> incompatibles = new ArrayList<>();
        List<String> insuficientes = new ArrayList<>();
        for (Asignacion a : asignaciones) {
            Set<Long> bloquesG = bloquesPorGrupo.getOrDefault(
                    a.getGrupo().getGrupoId(), Collections.emptySet());
            long compatibles = bloquesG.stream()
                    .filter(bId -> maestroBloque.contains(a.getMaestro().getMaestroId() + "-" + bId))
                    .count();
            if (compatibles == 0) {
                incompatibles.add(a.getGrupo().getNombre() + " · " +
                        a.getMateria().getNombre() + " · " +
                        a.getMaestro().getNombreCompleto());
            } else if (compatibles < a.getHoras()) {
                insuficientes.add(a.getGrupo().getNombre() + " · " +
                        a.getMateria().getNombre() + ": " +
                        compatibles + "/" + a.getHoras() + " horas");
            }
        }

        if (!incompatibles.isEmpty()) {
            validaciones.add(ValidacionDTO.error("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Asignaciones sin bloques compatibles:\n  " +
                            String.join("\n  ", incompatibles)));
        } else if (!insuficientes.isEmpty()) {
            validaciones.add(ValidacionDTO.advertencia("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Bloques insuficientes para cubrir todas las horas:\n  " +
                            String.join("\n  ", insuficientes)));
        } else {
            validaciones.add(ValidacionDTO.ok("compatibilidad_maestro_grupo",
                    "Compatibilidad maestro ↔ grupo",
                    "Todos los maestros tienen bloques compatibles con sus grupos"));
        }

        Map<Long, Integer> horasPorMaestro = new HashMap<>();
        for (Asignacion a : asignaciones) {
            horasPorMaestro.merge(a.getMaestro().getMaestroId(), a.getHoras(), Integer::sum);
        }
        Map<Long, Integer> bloquesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            bloquesPorMaestro.merge(dm.getMaestro().getMaestroId(), 1, Integer::sum);
        }

        List<String> sobrecargados = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : horasPorMaestro.entrySet()) {
            int horas = e.getValue();
            int disp = bloquesPorMaestro.getOrDefault(e.getKey(), 0);
            if (disp > 0 && disp < horas) {
                String nombre = asignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(e.getKey()))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst().orElse("ID " + e.getKey());
                sobrecargados.add(nombre + ": " + horas + " horas, " + disp + " bloques");
            }
        }

        if (!sobrecargados.isEmpty()) {
            validaciones.add(ValidacionDTO.error("carga_maestros", "Capacidad horaria de maestros",
                    "Maestros con más horas que bloques disponibles:\n  " +
                            String.join("\n  ", sobrecargados)));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_maestros", "Capacidad horaria de maestros",
                    "Ningún maestro está sobrecargado"));
        }

        List<String> distInvalidas = new ArrayList<>();
        for (Asignacion a : asignaciones) {
            String d = a.getDistribucion();
            if (d == null || d.isBlank()) continue;
            try {
                int suma = Arrays.stream(d.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .mapToInt(Integer::parseInt)
                        .sum();
                if (suma != a.getHoras()) {
                    distInvalidas.add(a.getMateria().getNombre() +
                            " (" + a.getGrupo().getNombre() + "): distribución '" + d +
                            "' suma " + suma + " pero tiene " + a.getHoras() + " horas");
                }
            } catch (NumberFormatException ex) {
                distInvalidas.add(a.getMateria().getNombre() +
                        " (" + a.getGrupo().getNombre() + "): distribución '" + d +
                        "' tiene formato inválido");
            }
        }

        if (!distInvalidas.isEmpty()) {
            validaciones.add(ValidacionDTO.advertencia("distribuciones", "Distribución de horas",
                    "Distribuciones inconsistentes:\n  " + String.join("\n  ", distInvalidas)));
        } else {
            validaciones.add(ValidacionDTO.ok("distribuciones", "Distribución de horas",
                    "Todas las distribuciones son coherentes"));
        }

        Set<Long> aulasUsadas = asignaciones.stream()
                .map(a -> a.getAula().getAulaId())
                .collect(Collectors.toSet());

        int maxGruposParalelos = grupos.size();
        int aulasDisponibles = aulasUsadas.size();

        if (aulasDisponibles < maxGruposParalelos) {
            validaciones.add(ValidacionDTO.advertencia("carga_aulas", "Carga de aulas",
                    "Hay " + maxGruposParalelos + " grupos pero solo " + aulasDisponibles +
                            " aulas en uso. Los grupos competirán por las mismas aulas durante el mismo bloque."));
        } else {
            validaciones.add(ValidacionDTO.ok("carga_aulas", "Carga de aulas",
                    aulasDisponibles + " aulas disponibles para " + maxGruposParalelos + " grupos"));
        }

        long errores = validaciones.stream().filter(v -> "ERROR".equals(v.estado())).count();
        long advertencias = validaciones.stream().filter(v -> "ADVERTENCIA".equals(v.estado())).count();

        return new ResultadoValidacionDTO(
                validaciones, errores == 0, (int) errores, (int) advertencias);
    }

    /**
    * Analiza cuellos de botella por (grupo, bloque).
    */
    public AnalisisCuelloBotellaDTO analizarCuellosBotella(Long semestreIdParam, Long turnoIdParam) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, semestreIdParam);
        Long semestreId = semestre.getSemestreId();
        Long turnoFiltro = (turnoIdParam != null && turnoIdParam > 0) ? turnoIdParam : null;

        List<Grupo> grupos = grupoRepositorio.findActivosByEscuelaYSemestreYTurno(
                escuelaId, semestreId, turnoFiltro);

        if (grupos.isEmpty()) {
            throw new NegocioExcepcion("No hay grupos activos en el semestre/turno");
        }

        Set<Long> turnoIds = grupos.stream()
                .map(g -> g.getTurno().getTurnoId())
                .collect(Collectors.toSet());
        List<TurnoHorario> bloques = new ArrayList<>();
        for (Long turnoId : turnoIds) {
            bloques.addAll(
                    turnoHorarioRepositorio.findClasesByTurnoIdAndSemestreId(turnoId, semestreId));
        }
        Map<Long, TurnoHorario> bloquesPorId = bloques.stream()
                .collect(Collectors.toMap(TurnoHorario::getId, b -> b));

        List<Asignacion> todasAsignaciones = new ArrayList<>();
        for (Grupo g : grupos) {
            todasAsignaciones.addAll(
                    asignacionRepositorio.findByGrupoIdAndSemestreId(g.getGrupoId(), semestreId));
        }

        List<Long> maestroIds = todasAsignaciones.stream()
                .map(a -> a.getMaestro().getMaestroId())
                .distinct()
                .toList();
        Map<Long, Set<Long>> bloquesPorMaestro = new HashMap<>();
        for (Long mId : maestroIds) {
            List<DisponibilidadMaestro> disp = disponibilidadRepositorio
                    .findDisponiblesByMaestroIdAndSemestreId(mId, escuelaId, semestreId);
            Set<Long> setBloques = disp.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDisponible()))
                    .map(d -> d.getTurnoHorario().getId())
                    .collect(Collectors.toSet());
            bloquesPorMaestro.put(mId, setBloques);
        }

        Map<Long, Set<Long>> bloquesPorGrupo = new HashMap<>();
        for (Grupo g : grupos) {
            List<DisponibilidadGrupo> disp = disponibilidadGrupoRepositorio
                    .findDisponiblesByGrupoIdAndSemestreId(g.getGrupoId(), escuelaId, semestreId);
            Set<Long> setBloques = disp.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDisponible()))
                    .map(d -> d.getTurnoHorario().getId())
                    .collect(Collectors.toSet());
            bloquesPorGrupo.put(g.getGrupoId(), setBloques);
        }

        Map<Long, List<Asignacion>> asignacionesPorGrupo = todasAsignaciones.stream()
                .collect(Collectors.groupingBy(a -> a.getGrupo().getGrupoId()));

        List<AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO> pares = new ArrayList<>();
        int paresCriticos = 0;
        int paresAdvertencia = 0;
        int paresOk = 0;

        for (Grupo grupo : grupos) {
            Set<Long> bloquesDelGrupo = bloquesPorGrupo.getOrDefault(grupo.getGrupoId(), Set.of());
            List<Asignacion> asignacionesGrupo = asignacionesPorGrupo
                    .getOrDefault(grupo.getGrupoId(), List.of());

            if (asignacionesGrupo.isEmpty()) continue;

            for (TurnoHorario bloque : bloques) {
                if (!bloquesDelGrupo.contains(bloque.getId())) continue;

                List<String> nombresMaestrosDisponibles = new ArrayList<>();
                for (Asignacion a : asignacionesGrupo) {
                    Long mId = a.getMaestro().getMaestroId();
                    Set<Long> bloquesDelMaestro = bloquesPorMaestro.getOrDefault(mId, Set.of());
                    if (bloquesDelMaestro.contains(bloque.getId())) {
                        nombresMaestrosDisponibles.add(a.getMaestro().getNombreCompleto());
                    }
                }

                int disponibles = nombresMaestrosDisponibles.size();
                String severidad;
                if (disponibles <= 1) {
                    severidad = "CRITICO";
                    paresCriticos++;
                } else if (disponibles == 2) {
                    severidad = "ADVERTENCIA";
                    paresAdvertencia++;
                } else {
                    severidad = "OK";
                    paresOk++;
                }

                AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO par =
                        new AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO();
                par.setGrupoId(grupo.getGrupoId());
                par.setGrupoNombre(grupo.getNombre());
                par.setGrupoGrado(grupo.getGrado());
                par.setGrupoTurno(grupo.getTurno() != null ? grupo.getTurno().getNombre() : null);

                par.setTurnoHorarioId(bloque.getId());
                par.setDiaSemana(bloque.getDiaSemana());
                par.setDiaNombre(nombreDia(bloque.getDiaSemana()));
                par.setHoraInicio(bloque.getHoraInicio().toString());
                par.setHoraFin(bloque.getHoraFin().toString());

                par.setMaestrosDisponibles(disponibles);
                par.setTotalAsignacionesGrupo(asignacionesGrupo.size());
                par.setMaestrosNombres(nombresMaestrosDisponibles);
                par.setSeveridad(severidad);

                pares.add(par);
            }
        }

        pares.sort(Comparator
                .comparingInt((AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO p) ->
                        switch (p.getSeveridad()) {
                            case "CRITICO" -> 0;
                            case "ADVERTENCIA" -> 1;
                            default -> 2;
                        })
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getGrupoNombre)
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getDiaSemana)
                .thenComparing(AnalisisCuelloBotellaDTO.ParGrupoBloqueDTO::getHoraInicio));

        AnalisisCuelloBotellaDTO dto = new AnalisisCuelloBotellaDTO();
        dto.setSemestreId(semestreId);
        dto.setSemestreNombre(semestre.getNombre());
        if (turnoFiltro != null) {
            Turno turno = grupos.get(0).getTurno();
            dto.setTurnoId(turno.getTurnoId());
            dto.setTurnoNombre(turno.getNombre());
        }
        dto.setTotalBloques(bloques.size());
        dto.setTotalGrupos(grupos.size());
        dto.setTotalParesGrupoBloque(pares.size());
        dto.setParesCriticos(paresCriticos);
        dto.setParesAdvertencia(paresAdvertencia);
        dto.setParesOk(paresOk);
        dto.setPares(pares);

        return dto;
    }

   /** Nombre del día en español. */
   private static String nombreDia(Integer dia) {
       if (dia == null) return "?";
       String[] dias = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
       return (dia >= 1 && dia <= 5) ? dias[dia] : "Día " + dia;
   }

    // ============================================================
    // PRE-VALIDACIÓN INTERNA
    // ============================================================
    /**
     * Análisis de factibilidad previo a generar: los problemas que lo bloquean ({@code ❌}) y los
     * avisos. Lo usa el generador IA para compartir exactamente las mismas comprobaciones en vez de
     * tener las suyas propias.
     */
    public List<String> analizarFactibilidad(
            List<Grupo> grupos,
            List<Asignacion> todasAsignaciones,
            List<DisponibilidadGrupo> dispGrupos,
            List<DisponibilidadMaestro> dispMaestros) {

        List<String> problemas = new ArrayList<>();

        Map<Long, Set<Long>> bloquesDisponiblesPorGrupo = new HashMap<>();
        for (DisponibilidadGrupo dg : dispGrupos) {
            bloquesDisponiblesPorGrupo
                    .computeIfAbsent(dg.getGrupo().getGrupoId(), k -> new HashSet<>())
                    .add(dg.getTurnoHorario().getId());
        }

        Set<String> maestroBloqueDisponible = new HashSet<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            maestroBloqueDisponible.add(
                    dm.getMaestro().getMaestroId() + "-" + dm.getTurnoHorario().getId()
            );
        }

        for (Grupo grupo : grupos) {
            List<Asignacion> asignacionesGrupo = todasAsignaciones.stream()
                    .filter(a -> a.getGrupo().getGrupoId().equals(grupo.getGrupoId()))
                    .collect(Collectors.toList());

            if (asignacionesGrupo.isEmpty()) continue;

            Set<Long> bloquesGrupo = bloquesDisponiblesPorGrupo.getOrDefault(
                    grupo.getGrupoId(), Collections.emptySet());

            if (bloquesGrupo.isEmpty()) {
                problemas.add("❌ Grupo " + grupo.getNombre() +
                        ": tiene asignaciones pero no tiene bloques configurados en disponibilidad_grupo");
                continue;
            }

            int totalHorasGrupo = asignacionesGrupo.stream()
                    .mapToInt(Asignacion::getHoras)
                    .sum();
            int totalBloquesGrupo = bloquesGrupo.size();

            if (totalHorasGrupo > totalBloquesGrupo) {
                problemas.add(String.format(
                        "❌ Grupo %s: tiene %d horas asignadas en total pero solo %d bloques disponibles " +
                                "(faltan %d bloques). Reduce asignaciones o amplía la disponibilidad del grupo.",
                        grupo.getNombre(), totalHorasGrupo, totalBloquesGrupo,
                        totalHorasGrupo - totalBloquesGrupo));
            }

            Map<Long, List<Asignacion>> porMateria = asignacionesGrupo.stream()
                    .collect(Collectors.groupingBy(a -> a.getMateria().getMateriaId()));

            for (var entry : porMateria.entrySet()) {
                List<Asignacion> lista = entry.getValue();
                if (lista.size() <= 1) continue;

                Asignacion ref = lista.get(0);
                int totalHorasAsignadas = lista.stream()
                        .mapToInt(Asignacion::getHoras)
                        .sum();
                Integer horasCatalogo = ref.getMateria().getHorasSemana();

                if (horasCatalogo == null || horasCatalogo <= 0) continue;

                if (totalHorasAsignadas > horasCatalogo) {
                    String ids = lista.stream()
                            .map(a -> String.valueOf(a.getAsignacionId()))
                            .collect(Collectors.joining(", "));

                    problemas.add(String.format(
                            "❌ Grupo %s · Materia %s: tiene %d asignaciones que suman %d horas, " +
                                    "pero el catálogo de la materia solo permite %d horas. " +
                                    "Asignaciones: [%s]. " +
                                    "Reduce las horas o elimina/desactiva una de las asignaciones.",
                            grupo.getNombre(),
                            ref.getMateria().getClave() + " - " + ref.getMateria().getNombre(),
                            lista.size(),
                            totalHorasAsignadas,
                            horasCatalogo,
                            ids));
                }
            }

            for (Asignacion a : asignacionesGrupo) {
                Long maestroId = a.getMaestro().getMaestroId();
                int horasNecesarias = a.getHoras();

                long bloquesCompatibles = bloquesGrupo.stream()
                        .filter(bloqueId ->
                                maestroBloqueDisponible.contains(maestroId + "-" + bloqueId))
                        .count();

                if (bloquesCompatibles == 0) {
                    problemas.add(String.format(
                            "❌ Grupo %s · Materia %s · Maestro %s: 0 bloques compatibles " +
                                    "(el maestro NO está disponible en ningún bloque del grupo)",
                            grupo.getNombre(),
                            a.getMateria().getNombre(),
                            a.getMaestro().getNombreCompleto()));
                } else if (bloquesCompatibles < horasNecesarias) {
                    problemas.add(String.format(
                            "⚠️ Grupo %s · Materia %s · Maestro %s: " +
                                    "%d bloques compatibles pero se necesitan %d horas",
                            grupo.getNombre(),
                            a.getMateria().getNombre(),
                            a.getMaestro().getNombreCompleto(),
                            bloquesCompatibles,
                            horasNecesarias));
                }
            }
        }

        Map<Long, Integer> horasAsignadasPorMaestro = new HashMap<>();
        for (Asignacion a : todasAsignaciones) {
            horasAsignadasPorMaestro.merge(a.getMaestro().getMaestroId(), a.getHoras(), Integer::sum);
        }

        Map<Long, Integer> bloquesDisponiblesPorMaestro = new HashMap<>();
        for (DisponibilidadMaestro dm : dispMaestros) {
            bloquesDisponiblesPorMaestro.merge(dm.getMaestro().getMaestroId(), 1, Integer::sum);
        }

        for (Map.Entry<Long, Integer> entry : horasAsignadasPorMaestro.entrySet()) {
            Long maestroId = entry.getKey();
            int horasAsignadas = entry.getValue();
            int bloquesDisponibles = bloquesDisponiblesPorMaestro.getOrDefault(maestroId, 0);

            if (bloquesDisponibles == 0) continue;

            if (bloquesDisponibles < horasAsignadas) {
                String nombreMaestro = todasAsignaciones.stream()
                        .filter(a -> a.getMaestro().getMaestroId().equals(maestroId))
                        .map(a -> a.getMaestro().getNombreCompleto())
                        .findFirst().orElse("Maestro ID " + maestroId);

                problemas.add(String.format(
                        "❌ Maestro %s: tiene %d horas asignadas pero solo %d bloques disponibles " +
                                "(faltan %d bloques)",
                        nombreMaestro, horasAsignadas, bloquesDisponibles,
                        horasAsignadas - bloquesDisponibles));
            }
        }

        return problemas;
    }

    // ============================================================
    // CONVERSIÓN
    // ============================================================
    private HorarioDTO toDTO(Horario h) {
        HorarioDTO dto = new HorarioDTO();
        dto.setId(h.getHorarioId());
        dto.setGrupoId(h.getGrupo().getGrupoId());
        dto.setGrupoNombre(h.getGrupo().getNombre());
        dto.setAsignacionId(h.getAsignacion().getAsignacionId());
        dto.setMateriaNombre(h.getAsignacion().getMateria().getNombre());
        dto.setMateriaClave(h.getAsignacion().getMateria().getClave());
        dto.setMaestroId(h.getMaestroId());
        dto.setMaestroNombre(h.getAsignacion().getMaestro().getTituloNombreCompleto());
        dto.setTurnoHorarioId(h.getTurnoHorario().getId());
        dto.setDiaSemana(h.getTurnoHorario().getDiaSemana());
        dto.setHoraInicio(h.getTurnoHorario().getHoraInicio().toString());
        dto.setHoraFin(h.getTurnoHorario().getHoraFin().toString());
        dto.setAulaId(h.getAula().getAulaId());
        dto.setAulaNombre(h.getAula().getNombre());
        dto.setColorHex(h.getAsignacion().getColorHex());
        dto.setVersion(h.getVersion());
        if (h.getSemestre() != null) {
            dto.setSemestreId(h.getSemestre().getSemestreId());
            dto.setSemestreNombre(h.getSemestre().getNombre());
        }
        // Turno, especialidad y grado: se leen del GRUPO, no de las columnas copiadas, para que la
        // respuesta sea correcta aunque la fila la haya escrito una version anterior a db/11 (esas
        // columnas podrian estar en NULL). El grupo ya se toca mas arriba en este metodo, asi que
        // no anade ninguna consulta.
        Grupo grupo = h.getGrupo();
        if (grupo != null) {
            dto.setGrado(grupo.getGrado());
            if (grupo.getTurno() != null) {
                dto.setTurnoId(grupo.getTurno().getTurnoId());
            }
            if (grupo.getEspecialidad() != null) {
                dto.setEspecialidadId(grupo.getEspecialidad().getEspecialidadId());
                dto.setEspecialidadNombre(grupo.getEspecialidad().getNombre());
            }
        }
        return dto;
    }

    // ============================================================
    // EDICIÓN MANUAL DEL HORARIO (tablero de pines)
    // ============================================================

    /**
     * Valida y aplica una tanda de cambios hechos a mano en el tablero de pines.
     *
     * <h2>Todo o nada</h2>
     * Se valida la tanda completa sobre un modelo en memoria y solo después se escribe.
     * Si algún cambio tiene un problema <b>no se toca la base</b> y se devuelven TODOS los
     * problemas numerados para que el tablero marque cada pin.
     *
     * <h2>Reglas comprobadas</h2>
     * <ul>
     *   <li>El bloque destino pertenece al turno del grupo.</li>
     *   <li>Grupo y maestro libres en ese bloque (choques).</li>
     *   <li>Maestro y grupo disponibles en ese bloque.</li>
     *   <li>Aula: se asigna una libre del turno (se prefiere la de la asignación).</li>
     *   <li>Al colocar, la materia no supera sus horas contratadas.</li>
     * </ul>
     *
     * <p>Los cambios se validan de forma progresiva dentro de la misma tanda, así que un
     * QUITAR seguido de un COLOCAR permite intercambiar dos clases de lugar.
     */
    @Transactional
    public ResultadoManualDTO aplicarCambiosManuales(SolicitudManualDTO solicitud) {
        Long escuelaId = getEscuelaId();
        Semestre semestre = resolverSemestre(escuelaId, solicitud.getSemestreId());
        Long semestreId = semestre.getSemestreId();
        boolean validarSolo = Boolean.TRUE.equals(solicitud.getValidarSolo());

        List<CambioManualDTO> cambios = solicitud.getCambios() == null
                ? List.of() : solicitud.getCambios();
        if (cambios.isEmpty()) {
            throw new NegocioExcepcion("sin_cambios", "No hay cambios que aplicar");
        }

        // ── Estado actual (el semestre entero son cientos de filas: cabe en memoria) ──
        List<Horario> actuales = horarioRepositorio.findByEscuelaIdAndSemestreId(escuelaId, semestreId);
        Map<Long, Horario> horarioPorId = new HashMap<>();
        Map<Long, Long> bloqueDe = new HashMap<>();
        Map<Long, Aula> aulaDe = new HashMap<>();
        Map<String, String> ocupado = new HashMap<>();
        Map<Long, Integer> colocadasPorAsignacion = new HashMap<>();

        for (Horario h : actuales) {
            horarioPorId.put(h.getHorarioId(), h);
            bloqueDe.put(h.getHorarioId(), h.getTurnoHorario().getId());
            aulaDe.put(h.getHorarioId(), h.getAula());
            ocupar(ocupado, h.getGrupo().getGrupoId(), h.getMaestroId(), h.getAula().getAulaId(),
                    h.getTurnoHorario().getId(), etiquetaAsignacion(h.getAsignacion()));
            colocadasPorAsignacion.merge(h.getAsignacion().getAsignacionId(), 1, Integer::sum);
        }

        List<Aula> aulasSemestre = aulaRepositorio
                .findByEscuelaIdAndActivoTrueAndSemestreId(escuelaId, semestreId);

        // ── Validación progresiva (sin tocar entidades administradas por Hibernate) ──
        List<String> errores = new ArrayList<>();
        List<Horario> porCrear = new ArrayList<>();
        List<Long> porBorrar = new ArrayList<>();
        Map<Long, Long> destinoDe = new HashMap<>();
        Map<Long, Aula> aulaDestinoDe = new HashMap<>();
        int colocados = 0;
        int movidos = 0;
        int quitados = 0;

        for (int i = 0; i < cambios.size(); i++) {
            CambioManualDTO c = cambios.get(i);
            String etiqueta = "Cambio " + (i + 1) + ": ";
            String tipo = c.getTipo() == null ? "" : c.getTipo().trim().toUpperCase();
            try {
                if ("COLOCAR".equals(tipo)) {
                    colocados += validarColocar(c, escuelaId, semestreId, validarSolo ? null : porCrear,
                            ocupado, colocadasPorAsignacion, aulasSemestre);

                } else if ("MOVER".equals(tipo)) {
                    movidos += validarMover(c, escuelaId, semestreId, horarioPorId, bloqueDe, aulaDe,
                            destinoDe, aulaDestinoDe, porBorrar, ocupado, aulasSemestre);

                } else if ("QUITAR".equals(tipo)) {
                    quitados += validarQuitar(c, horarioPorId, bloqueDe, aulaDe, destinoDe,
                            porBorrar, ocupado, colocadasPorAsignacion);

                } else {
                    errores.add(etiqueta + "tipo de cambio desconocido '" + c.getTipo() + "'");
                }
            } catch (NegocioExcepcion e) {
                errores.add(etiqueta + e.getMensajeCrudo());
            }
        }

        ResultadoManualDTO resultado = new ResultadoManualDTO();
        resultado.setSoloValidacion(validarSolo);
        resultado.setColocados(colocados);
        resultado.setMovidos(movidos);
        resultado.setQuitados(quitados);
        resultado.setErrores(errores);

        if (!errores.isEmpty()) {
            resultado.setAplicado(false);
            resultado.setMensaje("No se aplicó ningún cambio: " + errores.size() + " problema(s) por resolver");
            logger.info("Edición manual rechazada: {} problema(s)", errores.size());
            return resultado;
        }

        if (validarSolo) {
            resultado.setAplicado(false);
            resultado.setMensaje("Tanda válida: " + colocados + " por colocar, "
                    + movidos + " por mover, " + quitados + " por quitar");
            return resultado;
        }

        // ── Aplicar ──
        for (Long id : porBorrar) {
            Horario h = horarioPorId.get(id);
            if (h != null) {
                horarioRepositorio.delete(h);
            }
        }
        destinoDe.forEach((horarioId, bloqueId) -> {
            Horario h = horarioPorId.get(horarioId);
            if (h == null) {
                return;
            }
            turnoHorarioRepositorio.findById(bloqueId).ifPresent(h::setTurnoHorario);
            Aula aula = aulaDestinoDe.get(horarioId);
            if (aula != null) {
                h.setAula(aula);
            }
        });
        if (!porCrear.isEmpty()) {
            horarioRepositorio.saveAll(porCrear);
        }

        logger.info("Edición manual aplicada: {} colocada(s), {} movida(s), {} quitada(s)",
                colocados, movidos, quitados);
        resultado.setAplicado(true);
        resultado.setMensaje("Se aplicaron " + colocados + " colocación(es), "
                + movidos + " movimiento(s) y " + quitados + " retiro(s)");
        return resultado;
    }

    /** COLOCAR: una hora de una materia que estaba en la caja pasa a un bloque. */
    private int validarColocar(CambioManualDTO c, Long escuelaId, Long semestreId, List<Horario> porCrear,
                               Map<String, String> ocupado, Map<Long, Integer> colocadasPorAsignacion,
                               List<Aula> aulasSemestre) {
        if (c.getAsignacionId() == null || c.getTurnoHorarioId() == null) {
            throw new NegocioExcepcion("para colocar hacen falta la asignación y el bloque destino");
        }
        Asignacion asignacion = asignacionRepositorio.findById(c.getAsignacionId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró la asignación indicada"));
        if (asignacion.getEscuela() == null
                || !escuelaId.equals(asignacion.getEscuela().getEscuelaId())) {
            throw new NegocioExcepcion("la asignación pertenece a otra escuela");
        }
        if (asignacion.getSemestre() == null
                || !semestreId.equals(asignacion.getSemestre().getSemestreId())) {
            throw new NegocioExcepcion("la asignación pertenece a otro semestre");
        }
        if (Boolean.FALSE.equals(asignacion.getActivo())) {
            throw new NegocioExcepcion("la asignación está inactiva");
        }

        TurnoHorario bloque = turnoHorarioRepositorio.findById(c.getTurnoHorarioId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró el bloque destino"));
        String bloqueTexto = textoBloque(bloque);
        Grupo grupo = asignacion.getGrupo();
        Long maestroId = asignacion.getMaestro().getMaestroId();

        validarBloqueDelTurno(bloque, grupo, bloqueTexto);
        validarLibres(ocupado, grupo, maestroId, bloque.getId(), bloqueTexto);
        validarDisponibilidad(escuelaId, semestreId, maestroId, grupo, bloque.getId(), bloqueTexto);

        int colocadas = colocadasPorAsignacion.getOrDefault(asignacion.getAsignacionId(), 0);
        int horas = asignacion.getHoras() == null ? 0 : asignacion.getHoras();
        if (colocadas >= horas) {
            throw new NegocioExcepcion("la materia " + claveMateria(asignacion)
                    + " ya tiene sus " + horas + " hora(s) colocada(s)");
        }

        Aula aula = elegirAula(aulasSemestre, ocupado, bloque.getId(), asignacion.getAula(),
                grupo, bloqueTexto);
        if (porCrear != null) {
            Horario nuevo = new Horario();
            nuevo.setEscuela(asignacion.getEscuela());
            nuevo.setGrupo(grupo);
            // Turno, especialidad y grado del grupo (db/11), justo donde se fija el grupo.
            nuevo.copiarDelGrupo();
            nuevo.setAsignacion(asignacion);
            nuevo.setTurnoHorario(bloque);
            nuevo.setAula(aula);
            nuevo.setMaestroId(maestroId);
            nuevo.setSemestre(asignacion.getSemestre());
            nuevo.setVersion(1);
            porCrear.add(nuevo);
        }

        ocupar(ocupado, grupo.getGrupoId(), maestroId, aula.getAulaId(), bloque.getId(),
                etiquetaAsignacion(asignacion));
        colocadasPorAsignacion.merge(asignacion.getAsignacionId(), 1, Integer::sum);
        return 1;
    }

    /** MOVER: un pin ya colocado cambia de bloque (y de aula si la suya está ocupada). */
    private int validarMover(CambioManualDTO c, Long escuelaId, Long semestreId,
                             Map<Long, Horario> horarioPorId, Map<Long, Long> bloqueDe,
                             Map<Long, Aula> aulaDe, Map<Long, Long> destinoDe,
                             Map<Long, Aula> aulaDestinoDe, List<Long> porBorrar,
                             Map<String, String> ocupado, List<Aula> aulasSemestre) {
        if (c.getHorarioId() == null || c.getTurnoHorarioId() == null) {
            throw new NegocioExcepcion("para mover hacen falta la clase y el bloque destino");
        }
        Horario h = horarioPorId.get(c.getHorarioId());
        if (h == null) {
            throw new NegocioExcepcion("no se encontró la clase colocada indicada");
        }
        if (porBorrar.contains(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había quitado en esta misma tanda");
        }
        TurnoHorario bloque = turnoHorarioRepositorio.findById(c.getTurnoHorarioId())
                .orElseThrow(() -> new NegocioExcepcion("no se encontró el bloque destino"));
        if (bloque.getId().equals(bloqueDe.get(h.getHorarioId()))) {
            return 0;  // ya está ahí: no es un error, simplemente no hay nada que hacer
        }

        String bloqueTexto = textoBloque(bloque);
        Grupo grupo = h.getGrupo();
        Long maestroId = h.getMaestroId();
        validarBloqueDelTurno(bloque, grupo, bloqueTexto);

        Long bloqueOrigen = bloqueDe.get(h.getHorarioId());
        Aula aulaOrigen = aulaDe.get(h.getHorarioId());
        // Se libera primero el origen para que mover "en cadena" dentro de la misma tanda funcione.
        liberar(ocupado, grupo.getGrupoId(), maestroId, aulaOrigen.getAulaId(), bloqueOrigen);
        try {
            validarLibres(ocupado, grupo, maestroId, bloque.getId(), bloqueTexto);
            validarDisponibilidad(escuelaId, semestreId, maestroId, grupo, bloque.getId(), bloqueTexto);
            Aula aula = elegirAula(aulasSemestre, ocupado, bloque.getId(), aulaOrigen, grupo, bloqueTexto);
            destinoDe.put(h.getHorarioId(), bloque.getId());
            aulaDestinoDe.put(h.getHorarioId(), aula);
            aulaDe.put(h.getHorarioId(), aula);
            bloqueDe.put(h.getHorarioId(), bloque.getId());
            ocupar(ocupado, grupo.getGrupoId(), maestroId, aula.getAulaId(), bloque.getId(),
                    etiquetaAsignacion(h.getAsignacion()));
            return 1;
        } catch (NegocioExcepcion e) {
            // Devolver la ocupación del origen: la tanda sigue evaluándose desde el estado real.
            ocupar(ocupado, grupo.getGrupoId(), maestroId, aulaOrigen.getAulaId(), bloqueOrigen,
                    etiquetaAsignacion(h.getAsignacion()));
            throw e;
        }
    }

    /** QUITAR: un pin vuelve a la caja (se borra la fila del horario). */
    private int validarQuitar(CambioManualDTO c, Map<Long, Horario> horarioPorId,
                              Map<Long, Long> bloqueDe, Map<Long, Aula> aulaDe,
                              Map<Long, Long> destinoDe, List<Long> porBorrar,
                              Map<String, String> ocupado, Map<Long, Integer> colocadasPorAsignacion) {
        if (c.getHorarioId() == null) {
            throw new NegocioExcepcion("para quitar hace falta la clase");
        }
        Horario h = horarioPorId.get(c.getHorarioId());
        if (h == null) {
            throw new NegocioExcepcion("no se encontró la clase colocada indicada");
        }
        if (porBorrar.contains(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había quitado en esta misma tanda");
        }
        if (destinoDe.containsKey(h.getHorarioId())) {
            throw new NegocioExcepcion("esa clase ya se había movido en esta misma tanda");
        }
        liberar(ocupado, h.getGrupo().getGrupoId(), h.getMaestroId(),
                aulaDe.get(h.getHorarioId()).getAulaId(), bloqueDe.get(h.getHorarioId()));
        porBorrar.add(h.getHorarioId());
        colocadasPorAsignacion.merge(h.getAsignacion().getAsignacionId(), -1, Integer::sum);
        return 1;
    }

    // ─── Ayudantes de la edición manual ───────────────────────

    private void ocupar(Map<String, String> ocupado, Long grupoId, Long maestroId, Long aulaId,
                        Long bloqueId, String quien) {
        ocupado.put("g|" + grupoId + "|" + bloqueId, quien);
        ocupado.put("m|" + maestroId + "|" + bloqueId, quien);
        ocupado.put("a|" + aulaId + "|" + bloqueId, quien);
    }

    private void liberar(Map<String, String> ocupado, Long grupoId, Long maestroId, Long aulaId,
                         Long bloqueId) {
        ocupado.remove("g|" + grupoId + "|" + bloqueId);
        ocupado.remove("m|" + maestroId + "|" + bloqueId);
        ocupado.remove("a|" + aulaId + "|" + bloqueId);
    }

    private void validarBloqueDelTurno(TurnoHorario bloque, Grupo grupo, String bloqueTexto) {
        Long turnoBloque = bloque.getTurno() == null ? null : bloque.getTurno().getTurnoId();
        Long turnoGrupo = grupo.getTurno() == null ? null : grupo.getTurno().getTurnoId();
        if (turnoBloque == null || !turnoBloque.equals(turnoGrupo)) {
            throw new NegocioExcepcion("el bloque " + bloqueTexto
                    + " no pertenece al turno del grupo " + grupo.getNombre());
        }
    }

    private void validarLibres(Map<String, String> ocupado, Grupo grupo, Long maestroId,
                               Long bloqueId, String bloqueTexto) {
        String enGrupo = ocupado.get("g|" + grupo.getGrupoId() + "|" + bloqueId);
        if (enGrupo != null) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre() + " ya tiene clase el "
                    + bloqueTexto + " (" + enGrupo + ")");
        }
        String enMaestro = ocupado.get("m|" + maestroId + "|" + bloqueId);
        if (enMaestro != null) {
            throw new NegocioExcepcion("el maestro ya tiene clase el " + bloqueTexto
                    + " (" + enMaestro + ")");
        }
    }

    private void validarDisponibilidad(Long escuelaId, Long semestreId, Long maestroId, Grupo grupo,
                                       Long bloqueId, String bloqueTexto) {
        List<DisponibilidadMaestro> dispMaestro = disponibilidadRepositorio
                .findDisponiblesByMaestroIdAndSemestreId(maestroId, escuelaId, semestreId);
        if (dispMaestro.isEmpty()) {
            throw new NegocioExcepcion("el maestro no tiene disponibilidad registrada en este semestre");
        }
        boolean maestroOk = dispMaestro.stream().anyMatch(d -> d.getTurnoHorario() != null
                && bloqueId.equals(d.getTurnoHorario().getId()));
        if (!maestroOk) {
            throw new NegocioExcepcion("el maestro no está disponible el " + bloqueTexto);
        }

        List<DisponibilidadGrupo> dispGrupo = disponibilidadGrupoRepositorio
                .findDisponiblesByGrupoIdAndSemestreId(grupo.getGrupoId(), escuelaId, semestreId);
        if (dispGrupo.isEmpty()) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre()
                    + " no tiene disponibilidad registrada en este semestre");
        }
        boolean grupoOk = dispGrupo.stream().anyMatch(d -> d.getTurnoHorario() != null
                && bloqueId.equals(d.getTurnoHorario().getId()));
        if (!grupoOk) {
            throw new NegocioExcepcion("el grupo " + grupo.getNombre()
                    + " no está disponible el " + bloqueTexto);
        }
    }

    /** Primera aula libre del turno del grupo; si la preferida (la de la asignación) lo está, se usa esa. */
    /**
     * true si el patron pide alguna sesion de 2 o mas horas seguidas.
     *
     * Lo pide el generador para colocarlas antes: una sesion doble solo cabe donde hay dos bloques
     * contiguos libres del grupo, del maestro y del aula al mismo tiempo.
     */
    private static boolean tieneSesionLarga(String distribucion) {
        if (distribucion == null || distribucion.isBlank()) {
            return false;
        }
        for (String parte : distribucion.split(",")) {
            try {
                if (Integer.parseInt(parte.trim()) > 1) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Dato sucio en la distribucion: se ignora.
            }
        }
        return false;
    }

    /** true si al maestro le quedan menos de 4 bloques libres sobre las horas que debe cubrir. */
    private static boolean maestroApretado(Long maestroId, Map<Long, Integer> horasPorMaestro,
                                           Map<Long, Integer> bloquesPorMaestro) {
        int horas = horasPorMaestro.getOrDefault(maestroId, 0);
        int bloques = bloquesPorMaestro.getOrDefault(maestroId, 0);
        return bloques > 0 && (bloques - horas) < 4;
    }

    private Aula elegirAula(List<Aula> aulas, Map<String, String> ocupado, Long bloqueId,
                            Aula preferida, Grupo grupo, String bloqueTexto) {
        Long turnoGrupo = grupo.getTurno() == null ? null : grupo.getTurno().getTurnoId();
        if (preferida != null
                && preferida.getTurno() != null
                && preferida.getTurno().getTurnoId().equals(turnoGrupo)
                && ocupado.get("a|" + preferida.getAulaId() + "|" + bloqueId) == null) {
            return preferida;
        }
        List<Aula> libres = aulas.stream()
                .filter(a -> a.getTurno() != null && a.getTurno().getTurnoId().equals(turnoGrupo))
                .filter(a -> ocupado.get("a|" + a.getAulaId() + "|" + bloqueId) == null)
                .toList();
        // Preferencia de aula: una clase que va a taller se queda en un taller si hay otro libre,
        // en vez de caer en un aula normal.
        boolean quiereTaller = preferida != null && Boolean.TRUE.equals(preferida.getTaller());
        return libres.stream()
                .filter(a -> Boolean.TRUE.equals(a.getTaller()) == quiereTaller)
                .findFirst()
                .or(() -> libres.stream().findFirst())
                .orElseThrow(() -> new NegocioExcepcion("no hay aula libre para el grupo "
                        + grupo.getNombre() + " el " + bloqueTexto));
    }

    /** "lunes 14:00" para los mensajes al usuario. */
    private String textoBloque(TurnoHorario bloque) {
        String dia;
        switch (bloque.getDiaSemana() == null ? 0 : bloque.getDiaSemana()) {
            case 1: dia = "lunes"; break;
            case 2: dia = "martes"; break;
            case 3: dia = "miércoles"; break;
            case 4: dia = "jueves"; break;
            case 5: dia = "viernes"; break;
            default: dia = "día " + bloque.getDiaSemana();
        }
        String hora = bloque.getHoraInicio() == null ? "" : " " + bloque.getHoraInicio();
        return dia + hora;
    }

    private String claveMateria(Asignacion asignacion) {
        if (asignacion.getMateria() == null) {
            return "la materia";
        }
        String clave = asignacion.getMateria().getClave();
        return (clave == null || clave.isBlank()) ? asignacion.getMateria().getNombre() : clave;
    }

    /** "MATE (El Profe)" para decir quién ocupa un bloque. */
    private String etiquetaAsignacion(Asignacion asignacion) {
        if (asignacion == null) {
            return "otra clase";
        }
        String maestro = "sin maestro";
        if (asignacion.getMaestro() != null) {
            String apodo = asignacion.getMaestro().getApodo();
            maestro = (apodo != null && !apodo.isBlank())
                    ? apodo : asignacion.getMaestro().getTituloNombreCompleto();
        }
        return claveMateria(asignacion) + " (" + maestro + ")";
    }
}