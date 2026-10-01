import { describe, expect, it } from 'vitest'
import { agruparAsignaciones } from '../ReporteAsignacion'
import type { Horario } from '../../types'

/**
 * PRUEBAS DEL REPORTE ASIGNACIÓN (el agrupamiento).
 *
 * Lo que se prueba es la funcion pura que comparten pantalla, PDF y Excel: una fila por
 * (maestro, materia, grupo) con las horas sumadas, sin importar en cuantas aulas haya caido la
 * materia. El caso central es REAL, medido en produccion: TICS de 1°D quedo repartida entre AULA A4,
 * T. COMP 1, T. COMP 2 y T. COMP 3, con el mismo maestro y el mismo grupo: seis horas que deben
 * salir en UN SOLO registro.
 */

/** Una clase del horario con lo minimo; cada prueba cambia solo lo que le interesa. */
const clase = (parcial: Partial<Horario>): Horario => ({
  id: 1,
  grupoId: 18,
  grupoNombre: '1°D',
  asignacionId: 153,
  materiaNombre: 'Tecnologias de la Informacion y Comunicacion',
  materiaClave: 'TICS',
  maestroId: 7,
  maestroNombre: 'Lic. Francisco Alvarado Carreon',
  maestroApodo: 'Paco',
  turnoHorarioId: 1,
  diaSemana: 2,
  horaInicio: '18:30',
  horaFin: '19:20',
  aulaId: 8,
  aulaNombre: 'AULA A4',
  version: 1,
  turnoId: 2,
  especialidadNombre: 'PROGRAMACIÓN',
  grado: 1,
  ...parcial,
})

/** Las 6 clases reales de TICS de 1°D, en cuatro aulas distintas. */
const ticsDe1D: Horario[] = [
  clase({ id: 1, aulaId: 8, aulaNombre: 'AULA A4' }),
  clase({ id: 2, aulaId: 5, aulaNombre: 'T. COMP 3' }),
  clase({ id: 3, asignacionId: 154, aulaId: 96, aulaNombre: 'T. COMP 1' }),
  clase({ id: 4, asignacionId: 154, aulaId: 96, aulaNombre: 'T. COMP 1' }),
  clase({ id: 5, asignacionId: 154, aulaId: 97, aulaNombre: 'T. COMP 2' }),
  clase({ id: 6, asignacionId: 154, aulaId: 97, aulaNombre: 'T. COMP 2' }),
]

describe('Reporte Asignación: agrupamiento por maestro, materia y grupo', () => {
  it('REGRESION: una materia repartida en cuatro aulas sale en UN registro con las horas sumadas', () => {
    const renglones = agruparAsignaciones(ticsDe1D)

    expect(renglones).toHaveLength(1)
    expect(renglones[0]).toMatchObject({
      maestro: 'Lic. Francisco Alvarado Carreon',
      materia: 'Tecnologias de la Informacion y Comunicacion',
      especialidad: 'PROGRAMACIÓN',
      grupo: '1°D',
      horas: 6,
    })
  })

  it('la suma no pierde ni inventa horas', () => {
    const renglones = agruparAsignaciones(ticsDe1D)

    expect(renglones.reduce((s, r) => s + r.horas, 0)).toBe(ticsDe1D.length)
  })

  it('no mezcla dos grupos del mismo maestro y la misma materia', () => {
    const renglones = agruparAsignaciones([
      clase({ id: 1, grupoId: 18, grupoNombre: '1°D' }),
      clase({ id: 2, grupoId: 19, grupoNombre: '1°E' }),
      clase({ id: 3, grupoId: 19, grupoNombre: '1°E' }),
    ])

    expect(renglones.map(r => `${r.grupo}:${r.horas}`)).toEqual(['1°D:1', '1°E:2'])
  })

  it('no mezcla dos materias distintas del mismo maestro', () => {
    const renglones = agruparAsignaciones([
      clase({ id: 1, materiaClave: 'TICS', materiaNombre: 'TICS' }),
      clase({ id: 2, materiaClave: 'ALG', materiaNombre: 'Algebra' }),
      clase({ id: 3, materiaClave: 'ALG', materiaNombre: 'Algebra' }),
    ])

    expect(renglones.map(r => `${r.materia}:${r.horas}`)).toEqual(['Algebra:2', 'TICS:1'])
  })

  it('no mezcla a dos maestros de la misma materia y grupo', () => {
    const renglones = agruparAsignaciones([
      clase({ id: 1, maestroId: 7, maestroNombre: 'Lic. Uno' }),
      clase({ id: 2, maestroId: 9, maestroNombre: 'Ing. Dos' }),
    ])

    expect(renglones).toHaveLength(2)
    expect(renglones.map(r => r.maestro)).toEqual(['Ing. Dos', 'Lic. Uno'])
  })

  it('REGRESION: ignora las versiones viejas del horario', () => {
    const renglones = agruparAsignaciones([
      clase({ id: 1 }),
      clase({ id: 2, version: 2 }),
      clase({ id: 3, version: 3 }),
    ])

    expect(renglones).toHaveLength(1)
    expect(renglones[0].horas).toBe(1)
  })

  it('usa (sin especialidad) cuando el grupo no tiene', () => {
    const renglones = agruparAsignaciones([clase({ especialidadNombre: null })])

    expect(renglones[0].especialidad).toBe('(sin especialidad)')
  })

  it('ordena por maestro, y dentro de cada maestro por materia y grupo', () => {
    const renglones = agruparAsignaciones([
      clase({ id: 1, maestroNombre: 'Lic. Zeta', materiaNombre: 'Zootecnia', materiaClave: 'ZOO' }),
      clase({ id: 2, maestroNombre: 'Ing. Alfa', materiaNombre: 'Algebra', materiaClave: 'ALG' }),
      clase({ id: 3, maestroNombre: 'Lic. Zeta', materiaNombre: 'Analisis', materiaClave: 'ANA' }),
    ])

    expect(renglones.map(r => `${r.maestro}/${r.materia}`)).toEqual([
      'Ing. Alfa/Algebra',
      'Lic. Zeta/Analisis',
      'Lic. Zeta/Zootecnia',
    ])
  })
})
