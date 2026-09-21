package mx.sih.ia;

import mx.sih.modelo.entidad.Asignacion;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.Grupo;
import mx.sih.modelo.entidad.TurnoHorario;

import java.util.List;

/**
 * Todo lo que necesita el generador IA para armar un horario: los bloques del turno, los grupos, las
 * asignaciones (con su patrón) y las dos disponibilidades.
 *
 * <p>Son las mismas entidades que usa el solver, para que las reglas sean exactamente las mismas.
 */
public record DatosIA(List<TurnoHorario> bloques,
                      List<Grupo> grupos,
                      List<Asignacion> asignaciones,
                      List<DisponibilidadGrupo> disponibilidadGrupo,
                      List<DisponibilidadMaestro> disponibilidadMaestro) {
}
