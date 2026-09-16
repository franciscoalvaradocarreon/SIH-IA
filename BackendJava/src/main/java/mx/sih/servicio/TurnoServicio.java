package mx.sih.servicio;

import com.zaxxer.hikari.util.ClockSource;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.TurnoCrearDTO;
import mx.sih.modelo.dto.TurnoDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Semestre;
import mx.sih.modelo.entidad.Turno;
import mx.sih.repositorio.EscuelaRepositorio;
import mx.sih.repositorio.SemestreRepositorio;
import mx.sih.repositorio.TurnoRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurnoServicio {

    private final TurnoRepositorio turnoRepositorio;
    private final EscuelaRepositorio escuelaRepositorio;
    private final SemestreRepositorio semestreRepositorio;

    public TurnoServicio(TurnoRepositorio turnoRepositorio,
                         EscuelaRepositorio escuelaRepositorio,
                         SemestreRepositorio semestreRepositorio) {
        this.turnoRepositorio = turnoRepositorio;
        this.escuelaRepositorio = escuelaRepositorio;
        this.semestreRepositorio = semestreRepositorio;
    }

    private Long getEscuelaId() {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("No se ha seleccionado una escuela activa");
        }
        return escuelaId;
    }

    public Page<TurnoDTO> listarTurnos(Pageable pageable, String busqueda, Long semestreId) {
        Long escuelaId = getEscuelaId();
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<Turno> pagina;
        
        pagina = turnoRepositorio.findByEscuelaIdAndSemestreIdAndBusqueda(
            escuelaId, semestreId, busquedaNormalizada, pageable);
        
        return pagina.map(this::toDTO);
    }

    public TurnoDTO obtenerTurno(Long id) {
        Long escuelaId = getEscuelaId();
        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        // Verificar que el turno pertenezca a la escuela actual
        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        return toDTO(turno);
    }

// mx.sih.servicio.TurnoServicio.java

    @Transactional
    public TurnoDTO crearTurno(TurnoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        // Verificar que la escuela existe
        Escuela escuela = escuelaRepositorio.findById(escuelaId)
            .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada"));

        // Verificar que el semestre existe (si se proporciona)
        Semestre semestre = null;
        if (dto.getSemestreId() != null) {
            semestre = semestreRepositorio.findByIdAndEscuelaId(dto.getSemestreId(), escuelaId)
                .orElseThrow(() -> new NegocioExcepcion("Semestre no encontrado"));
        }

        Turno turno = new Turno();
        turno.setEscuela(escuela);
        turno.setNombre(dto.getNombre());
        turno.setDescripcion(dto.getDescripcion());
        turno.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        turno.setSemestre(semestre);

        Turno guardado = turnoRepositorio.save(turno);
        return toDTO(guardado);
    }

    @Transactional
    public TurnoDTO actualizarTurno(Long id, TurnoCrearDTO dto) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        // Verificar que el turno pertenezca a la escuela actual
        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        // Verificar nombre único (excluyendo el mismo registro)
        if (!turno.getNombre().equalsIgnoreCase(dto.getNombre()) &&
            turnoRepositorio.existsByEscuelaIdAndNombreIgnoreCaseAndIdNot(escuelaId, dto.getNombre(), id)) {
            throw new NegocioExcepcion("Ya existe otro turno con el nombre: " + dto.getNombre() + " en esta escuela");
        }

        turno.setNombre(dto.getNombre().toUpperCase());
        turno.setDescripcion(dto.getDescripcion());


        Turno actualizado = turnoRepositorio.save(turno);
        return toDTO(actualizado);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        turno.setActivo(activo);
        turnoRepositorio.save(turno);
    }

    @Transactional
    public void eliminarTurno(Long id) {
        Long escuelaId = getEscuelaId();

        Turno turno = turnoRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Turno no encontrado con ID: " + id));

        if (!turno.getEscuela().getEscuelaId().equals(escuelaId)) {
            throw new NegocioExcepcion("No tiene acceso a este turno");
        }

        turnoRepositorio.deleteById(id);
    }

    private TurnoDTO toDTO(Turno turno) {
        TurnoDTO dto = new TurnoDTO();
        dto.setId(turno.getTurnoId());
        dto.setEscuelaId(turno.getEscuela().getEscuelaId());
        dto.setNombre(turno.getEscuela().getNombre());
        dto.setNombre(turno.getNombre());
        dto.setDescripcion(turno.getDescripcion());
        dto.setActivo(turno.getActivo());

        if (turno.getSemestre() != null) {
            dto.setSemestreId(turno.getSemestre().getSemestreId());
            dto.setSemestreNombre(turno.getSemestre().getNombre());
        }

        return dto;
    }
}