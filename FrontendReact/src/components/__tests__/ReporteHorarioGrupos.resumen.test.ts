import { describe, expect, it } from 'vitest'
import { resumirMaterias } from '../ReporteHorarioGrupos'
import type { Horario } from '../../types'

/**
 * PRUEBAS DEL RESUMEN "MATERIAS Y MAESTROS ASIGNADOS" (reporte Horario por Grupo).
 *
 * El resumen lo comparten la pantalla, el PDF y el Excel, asi que se prueba la funcion pura en vez
 * de montar el reporte entero. El caso central es REAL, medido en produccion: la asignacion 153 de
 * TICS de 1°D dice "AULA A4" con 2 horas, pero el motor (en modo "elegir el taller") repartio esas
 * dos horas entre AULA A4 y T. COMP 3. Antes el resumen imprimia un unico renglon
 * "TICS · T. COMP 3 · 2 horas", que nunca existio.
 */

/** Una clase del horario con lo minimo; cada prueba cambia solo lo que le interesa. */
const clase = (parcial: Partial<Horario>): Horario => ({
  id: 1,
  grupoId: 18,
  grupoNombre: '1°D',
  asignacionId: 153,
  materiaNombre: 'Tecnologias de la Informacion y Comunicacion',
  materiaClave: 'TICS',
  maestroId: 1,
  maestroNombre: 'Francisco Alvarado Carreon',
  maestroApodo: 'Paco',
  turnoHorarioId: 1,
  diaSemana: 2,
  horaInicio: '18:30',
  horaFin: '19:20',
  aulaId: 5,
  aulaNombre: 'T. COMP 3',
  version: 1,
  turnoId: 2,
  ...parcial,
})

/** Las 6 clases reales de TICS de 1°D: asignacion 153 (2 h) y 154 (4 h). */
const ticsDe1D: Horario[] = [
  clase({ id: 1, asignacionId: 153, aulaId: 5, aulaNombre: 'T. COMP 3', diaSemana: 2, horaInicio: '18:30' }),
  clase({ id: 2, asignacionId: 153, aulaId: 3, aulaNombre: 'AULA A4', diaSemana: 3, horaInicio: '16:30' }),
  clase({ id: 3, asignacionId: 154, aulaId: 4, aulaNombre: 'T. COMP 1', diaSemana: 4, horaInicio: '16:30' }),
  clase({ id: 4, asignacionId: 154, aulaId: 4, aulaNombre: 'T. COMP 1', diaSemana: 4, horaInicio: '17:40' }),
  clase({ id: 5, asignacionId: 154, aulaId: 6, aulaNombre: 'T. COMP 2', diaSemana: 5, horaInicio: '18:30' }),
  clase({ id: 6, asignacionId: 154, aulaId: 6, aulaNombre: 'T. COMP 2', diaSemana: 5, horaInicio: '19:20' }),
]

describe('Resumen de materias y maestros: una fila por asignacion y aula', () => {
  it('REGRESION: un renglon por aula cuando la asignacion quedo repartida en varias', () => {
    const resumen = resumirMaterias(ticsDe1D)

    // Cuatro aulas, no dos: la 153 en AULA A4 y T. COMP 3; la 154 en T. COMP 1 y T. COMP 2.
    expect(resumen.map(r => `${r.aulaNombre}:${r.horas}`)).toEqual([
      'AULA A4:1',
      'T. COMP 1:2',
      'T. COMP 2:2',
      'T. COMP 3:1',
    ])

    // Lo que NO debe volver a pasar: un renglon que junte 2 horas en un aula donde solo hubo 1.
    expect(resumen.some(r => r.aulaNombre === 'T. COMP 3' && r.horas === 2)).toBe(false)
    expect(resumen.some(r => r.aulaNombre === 'T. COMP 1' && r.horas === 4)).toBe(false)
  })

  it('no pierde ni inventa horas: la suma sigue siendo la del grupo', () => {
    const resumen = resumirMaterias(ticsDe1D)

    const total = resumen.reduce((suma, r) => suma + r.horas, 0)
    expect(total).toBe(6)
    expect(total).toBe(ticsDe1D.length)
  })

  it('no mezcla dos asignaciones de la misma materia aunque compartan aula', () => {
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 153, aulaId: 3, aulaNombre: 'AULA A4' }),
      clase({ id: 2, asignacionId: 153, aulaId: 3, aulaNombre: 'AULA A4' }),
      clase({ id: 3, asignacionId: 154, aulaId: 3, aulaNombre: 'AULA A4' }),
    ])

    expect(resumen).toHaveLength(2)
    expect(resumen.map(r => `${r.asignacionId}:${r.horas}`)).toEqual(['153:2', '154:1'])
  })

  it('ordena por materia y, dentro de ella, por aula (dos exportaciones salen iguales)', () => {
    // Cada renglon con SU asignacion: el resumen agrupa por (asignacion, aula), asi que dos
    // asignaciones distintas no se fusionan aunque coincidan en aula.
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 201, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 4, aulaNombre: 'T. COMP 1' }),
      clase({ id: 2, asignacionId: 202, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 3, aulaNombre: 'AULA A4' }),
      clase({ id: 3, asignacionId: 203, materiaClave: 'ALG', materiaNombre: 'Algebra', aulaId: 3, aulaNombre: 'AULA A4' }),
    ])

    expect(resumen.map(r => `${r.materiaNombre}/${r.aulaNombre}`)).toEqual([
      'Algebra/AULA A4',
      'Tecnologias/AULA A4',
      'Tecnologias/T. COMP 1',
    ])
  })

  it('sigue funcionando cuando una clase no tiene aula asignada', () => {
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 200, aulaId: 0, aulaNombre: '' }),
    ])

    expect(resumen).toHaveLength(1)
    expect(resumen[0].aulaNombre).toBe('')
    expect(resumen[0].horas).toBe(1)
  })
})
