import { describe, expect, it } from 'vitest'
import { resumirMaterias, textoAulas } from '../ReporteHorarioGrupos'
import type { Horario } from '../../types'

/**
 * PRUEBAS DEL RESUMEN "MATERIAS Y MAESTROS ASIGNADOS" (reporte Horario por Grupo).
 *
 * El resumen lo comparten la pantalla, el PDF y el Excel, asi que se prueba la funcion pura en vez
 * de montar el reporte entero.
 *
 * Los casos centrales son REALES, medidos en produccion (grupo 1°D, turno vespertino):
 *   - LEO I   es UNA asignacion repartida en AULA A1 (2 h) y AULA A4 (4 h);
 *   - Logica  es UNA asignacion repartida en AULA A3 (1 h) y AULA A4 (3 h);
 *   - TICS    son DOS asignaciones: la 153 en AULA A4 y T. COMP 3, y la 154 en T. COMP 1 y T. COMP 2.
 * El motor reparte la asignacion entre las aulas de la materia cuando va en modo "elegir el taller".
 */

/** Una clase del horario con lo minimo; cada prueba cambia solo lo que le interesa. */
const clase = (parcial: Partial<Horario>): Horario => ({
  id: 1,
  grupoId: 18,
  grupoNombre: '1°D',
  asignacionId: 81,
  materiaNombre: 'Lectura, Expresion Oral y Escrita I',
  materiaClave: 'LEO I',
  maestroId: 1,
  maestroNombre: 'Emilia Cordova Vazquez',
  maestroApodo: 'Emilia',
  turnoHorarioId: 1,
  diaSemana: 2,
  horaInicio: '18:30',
  horaFin: '19:20',
  aulaId: 1,
  aulaNombre: 'AULA A4',
  version: 1,
  turnoId: 2,
  ...parcial,
})

/** Las 6 clases reales de TICS de 1°D: la asignacion 153 (2 h) y la 154 (4 h). */
const ticsDe1D: Horario[] = [
  clase({ id: 1, asignacionId: 153, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 5, aulaNombre: 'T. COMP 3', diaSemana: 2 }),
  clase({ id: 2, asignacionId: 153, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 3, aulaNombre: 'AULA A4', diaSemana: 3 }),
  clase({ id: 3, asignacionId: 154, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 4, aulaNombre: 'T. COMP 1', diaSemana: 4 }),
  clase({ id: 4, asignacionId: 154, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 4, aulaNombre: 'T. COMP 1', diaSemana: 4 }),
  clase({ id: 5, asignacionId: 154, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 6, aulaNombre: 'T. COMP 2', diaSemana: 5 }),
  clase({ id: 6, asignacionId: 154, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaId: 6, aulaNombre: 'T. COMP 2', diaSemana: 5 }),
]

describe('Resumen de materias y maestros: un renglon por asignacion', () => {
  it('REGRESION: no repite la materia; una asignacion repartida da un renglon con sus aulas', () => {
    const resumen = resumirMaterias(ticsDe1D)

    // Dos asignaciones de TICS -> dos renglones (no cuatro), con el desglose de aulas y sus horas.
    expect(resumen).toHaveLength(2)
    expect(resumen.map(m => `${m.asignacionId} -> ${textoAulas(m)} (${m.horas} h)`)).toEqual([
      '153 -> AULA A4 (1), T. COMP 3 (1) (2 h)',
      '154 -> T. COMP 1 (2), T. COMP 2 (2) (4 h)',
    ])

    // Lo que NO debe volver a pasar: un renglon que afirme que las 2 horas fueron en un solo aula.
    expect(resumen.some(m => textoAulas(m) === 'T. COMP 3')).toBe(false)
    expect(resumen.some(m => textoAulas(m) === 'T. COMP 1')).toBe(false)
  })

  it('con UNA sola aula no agrega parentesis: el aula se lee como siempre', () => {
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 76, materiaClave: 'ING', materiaNombre: 'Ingles I', aulaNombre: 'AULA A4' }),
      clase({ id: 2, asignacionId: 76, materiaClave: 'ING', materiaNombre: 'Ingles I', aulaNombre: 'AULA A4' }),
    ])

    expect(resumen).toHaveLength(1)
    expect(textoAulas(resumen[0])).toBe('AULA A4')
    expect(resumen[0].horas).toBe(2)
  })

  it('no pierde ni inventa horas: la suma sigue siendo la del grupo', () => {
    const resumen = resumirMaterias(ticsDe1D)

    expect(resumen.reduce((suma, m) => suma + m.horas, 0)).toBe(6)
    expect(resumen.map(m => m.horas)).toEqual([2, 4])
  })

  it('no mezcla dos asignaciones de la misma materia aunque compartan aula', () => {
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 153, aulaNombre: 'AULA A4' }),
      clase({ id: 2, asignacionId: 153, aulaNombre: 'AULA A4' }),
      clase({ id: 3, asignacionId: 154, aulaNombre: 'AULA A4' }),
    ])

    expect(resumen).toHaveLength(2)
    expect(resumen.map(m => `${m.asignacionId}:${m.horas} h:${textoAulas(m)}`)).toEqual([
      '153:2 h:AULA A4',
      '154:1 h:AULA A4',
    ])
  })

  it('ordena por materia y, dentro de ella, por aula (dos exportaciones salen iguales)', () => {
    const resumen = resumirMaterias([
      clase({ id: 1, asignacionId: 301, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaNombre: 'T. COMP 1' }),
      clase({ id: 2, asignacionId: 302, materiaClave: 'TICS', materiaNombre: 'Tecnologias', aulaNombre: 'AULA A4' }),
      clase({ id: 3, asignacionId: 303, materiaClave: 'ALG', materiaNombre: 'Algebra', aulaNombre: 'AULA A4' }),
    ])

    expect(resumen.map(m => m.materiaNombre)).toEqual(['Algebra', 'Tecnologias', 'Tecnologias'])
    // Y el aula de una asignacion repartida sale siempre en el mismo orden (por nombre).
    const repartida = resumirMaterias([
      clase({ id: 1, asignacionId: 81, aulaNombre: 'AULA A4' }),
      clase({ id: 2, asignacionId: 81, aulaNombre: 'AULA A1' }),
      clase({ id: 3, asignacionId: 81, aulaNombre: 'AULA A4' }),
    ])
    expect(textoAulas(repartida[0])).toBe('AULA A1 (1), AULA A4 (2)')
  })

  it('sigue funcionando cuando una clase no tiene aula asignada', () => {
    const resumen = resumirMaterias([clase({ id: 1, asignacionId: 200, aulaId: 0, aulaNombre: '' })])

    expect(resumen).toHaveLength(1)
    expect(textoAulas(resumen[0])).toBe('')
    expect(resumen[0].horas).toBe(1)
  })
})
