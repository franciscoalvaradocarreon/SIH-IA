package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.MateriaCrearDTO;
import mx.sih.modelo.dto.MateriaDTO;
import mx.sih.modelo.dto.MateriaDetalleDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Materia;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.AsignacionRepositorio;
import mx.sih.repositorio.HorarioRepositorio;
import mx.sih.repositorio.MateriaRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MateriaServicio {

    private final MateriaRepositorio materiaRepositorio;
    private final SemestreRepositorio semestreRepositorio;
    private final TurnoRepositorio turnoRepositorio;
    private final AsignacionRepositorio asignacionRepositorio;
    private final HorarioRepositorio horarioRepositorio;

    public MateriaServicio(MateriaRepositorio materiaRepositorio,
                           SemestreRepositorio semestreRepositorio,
                           TurnoRepositorio turnoRepositorio,
                           AsignacionRepositorio asignacionRepositorio,
                           HorarioRepositorio horarioRepositorio) {
        this.materiaRepositorio = materiaRepositorio;
        this.semestreRepositorio = semestreRepositorio;
        this.turnoRepositorio = turnoRepositorio;
        this.asignacionRepositorio = asignacionRepositorio;
        this.horarioRepositorio = horarioRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<MateriaDTO> listarMaterias(Pageable pageable, String busqueda,
                                            Long semestreId, Long turnoId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();

        Page<Materia> pagina = materiaRepositorio.buscarPorEscuelaYFiltros(
                escuelaId, busquedaNormalizada, semestreId, turnoId, pageable);
        return pagina.map(this::toDTO);
    }

    public MateriaDetalleDTO obtenerMateria(Long id) {
        Long escuelaId = getEscuelaId();
        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));
        return toDetalleDTO(materia);
    }

    @Transactional
    public MateriaDTO crearMateria(MateriaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        // 🔥 Coherencia: turno y semestre deben coincidir
        validarCoherenciaSemestreTurno(semestre, turno);

        // 🔥 Unicidad: clave por (semestre, turno)
        if (materiaRepositorio.existsByClaveAndSemestreIdAndTurnoId(
                dto.getClave(), semestre.getSemestreId(), turno.getTurnoId())) {
            throw new NegocioExcepcion(
                    "MATERIA_CLAVE_DUPLICADA",
                    "Ya existe una materia con la clave '" + dto.getClave() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        Escuela escuela = new Escuela();
        escuela.setEscuelaId(escuelaId);

        Materia materia = new Materia();
        materia.setNombre(dto.getNombre());
        materia.setClave(dto.getClave());
        materia.setDescripcion(dto.getDescripcion());
        materia.setCreditos(dto.getCreditos());
        materia.setColorHex(dto.getColorHex());
        materia.setHorasSemana(dto.getHorasSemana());
        materia.setEscuela(escuela);
        materia.setSemestre(semestre);
        materia.setTurno(turno);   // 🔥
        materia.setActivo(true);

        Materia guardada = materiaRepositorio.save(materia);
        return toDTO(guardada);
    }

    @Transactional
    public MateriaDTO actualizarMateria(Long id, MateriaCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Semestre semestre = semestreRepositorio
                .findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));

        Turno turno = turnoRepositorio
                .findByIdAndEscuelaId(dto.getTurnoId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado"));

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));

        validarCoherenciaSemestreTurno(semestre, turno);

        if (materiaRepositorio.existsByClaveAndSemestreIdAndTurnoIdAndIdNot(
                dto.getClave(), semestre.getSemestreId(),
                turno.getTurnoId(), id)) {
            throw new NegocioExcepcion(
                    "MATERIA_CLAVE_DUPLICADA",
                    "Ya existe otra materia con la clave '" + dto.getClave() +
                    "' en el turno '" + turno.getNombre() +
                    "' del semestre '" + semestre.getNombre() + "'"
            );
        }

        materia.setNombre(dto.getNombre());
        materia.setClave(dto.getClave());
        materia.setDescripcion(dto.getDescripcion());
        materia.setCreditos(dto.getCreditos());
        materia.setColorHex(dto.getColorHex());
        materia.setHorasSemana(dto.getHorasSemana());
        materia.setSemestre(semestre);
        materia.setTurno(turno);   // 🔥
        // Nota: NO tocamos activo aquí. Si quieres respetar el form, añade:
        // if (dto.getActivo() != null) materia.setActivo(dto.getActivo());

        Materia actualizada = materiaRepositorio.save(materia);
        return toDTO(actualizada);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();
        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));
        materia.setActivo(activo);
        materiaRepositorio.save(materia);
    }

    @Transactional
    public void eliminarMateria(Long id) {
        Long escuelaId = getEscuelaId();

        Materia materia = materiaRepositorio.findByIdAndEscuelaId(id, escuelaId)
                .orElseThrow(() -> new NegocioExcepcion(
                        "Materia no encontrada con ID: " + id));

        // 1. Verificar horarios generados
        long horarios = horarioRepositorio.countByMateriaId(id);
        if (horarios > 0) {
            throw new NegocioExcepcion(
                    "MATERIA_EN_USO",
                    "No se puede eliminar la materia '" + materia.getNombre() +
                    "' porque está siendo usada en " + horarios +
                    " bloque(s) de horario. Elimina primero los horarios " +
                    "o desactiva la materia en lugar de eliminarla."
            );
        }

        // 2. Verificar asignaciones
        long asignaciones = asignacionRepositorio.countByMateriaId(id);
        if (asignaciones > 0) {
            throw new NegocioExcepcion(
                    "MATERIA_EN_USO",
                    "No se puede eliminar la materia '" + materia.getNombre() +
                    "' porque está siendo usada en " + asignaciones +
                    " asignación(es) activa(s). Elimina primero las asignaciones " +
                    "o desactiva la materia en lugar de eliminarla."
            );
        }

        // 3. Sin dependencias: eliminar
        materiaRepositorio.delete(materia);
    }

    // ============================================================
    // HELPERS
    // ============================================================

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

    private MateriaDTO toDTO(Materia materia) {
        MateriaDTO dto = new MateriaDTO();
        dto.setId(materia.getMateriaId());
        dto.setNombre(materia.getNombre());
        dto.setClave(materia.getClave());
        dto.setDescripcion(materia.getDescripcion());
        dto.setCreditos(materia.getCreditos());
        dto.setHorasSemana(materia.getHorasSemana());
        dto.setColorHex(materia.getColorHex());
        dto.setActivo(materia.getActivo());

        if (materia.getSemestre() != null) {
            dto.setSemestreId(materia.getSemestre().getSemestreId());
            dto.setSemestreNombre(materia.getSemestre().getNombre());
        }
        if (materia.getTurno() != null) {
            dto.setTurnoId(materia.getTurno().getTurnoId());
            dto.setTurnoNombre(materia.getTurno().getNombre());
        }
        return dto;
    }

    private MateriaDetalleDTO toDetalleDTO(Materia materia) {
        MateriaDetalleDTO dto = new MateriaDetalleDTO();
        dto.setId(materia.getMateriaId());
        dto.setNombre(materia.getNombre());
        dto.setClave(materia.getClave());
        dto.setDescripcion(materia.getDescripcion());
        dto.setCreditos(materia.getCreditos());
        dto.setColorHex(materia.getColorHex());
        dto.setHorasSemana(materia.getHorasSemana());
        dto.setActivo(materia.getActivo());

        if (materia.getSemestre() != null) {
            dto.setSemestreId(materia.getSemestre().getSemestreId());
            dto.setSemestreNombre(materia.getSemestre().getNombre());
        }
        if (materia.getTurno() != null) {
            dto.setTurnoId(materia.getTurno().getTurnoId());
            dto.setTurnoNombre(materia.getTurno().getNombre());
        }
        return dto;
    }
}