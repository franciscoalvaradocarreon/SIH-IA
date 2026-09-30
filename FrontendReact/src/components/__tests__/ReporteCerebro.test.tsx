import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import ReporteCerebro from '../ReporteCerebro'
import type { Horario, Materia, Turno } from '../../types'

/**
 * PRUEBAS DEL REPORTE CEREBRO.
 *
 * El reporte no tiene backend propio: se arma en el navegador con tres consultas (turnos, materias
 * y el horario del semestre). Por eso aqui se sustituyen esos tres servicios por dobles con datos
 * fijos: la prueba queda determinista, corre en milisegundos y no necesita ni backend ni base.
 *
 * Lo que se comprueba es la LOGICA del reporte, que es donde ha habido errores de verdad:
 *   - que sin turno no dibuje nada y bloquee las exportaciones;
 *   - que las columnas sean los grupos de ESE grado;
 *   - que la celda use el apodo y caiga al nombre completo cuando no lo hay;
 *   - que dos maestros de la misma materia y grupo salgan juntos;
 *   - que el renglon de cierre sume las horas de la materia;
 *   - REGRESION: que ignore las versiones viejas del horario y las filas de otro turno;
 *   - que los grupos sin especialidad tengan su propia tarjeta;
 *   - que avise cuando el horario vigente no tiene clases.
 */

// --- Dobles de los servicios y del contexto ------------------------------------------------
// vi.hoisted: los dobles tienen que existir ANTES de que vi.mock registre las fabricas.
const listarTurnos = vi.hoisted(() => vi.fn())
const listarMaterias = vi.hoisted(() => vi.fn())
const obtenerTodosHorarios = vi.hoisted(() => vi.fn())

vi.mock('../../api/turnoService', () => ({ turnoService: { listar: listarTurnos } }))
vi.mock('../../api/materiaService', () => ({ materiaService: { listar: listarMaterias } }))
vi.mock('../../api/horarioService', () => ({ horarioService: { obtenerTodos: obtenerTodosHorarios } }))
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    semestreActivo: { id: 11, nombre: 'prueba Ago 2026 - Ene 2027' },
    escuelaActiva: { nombre: 'CETIS 66', nombreLargo: 'CETIS 66' },
  }),
}))

// --- Datos de prueba -----------------------------------------------------------------------

/** Una fila del horario con lo minimo; cada prueba cambia solo lo que le interesa. */
const fila = (parcial: Partial<Horario>): Horario => ({
  id: 1,
  grupoId: 10,
  grupoNombre: '1°A',
  asignacionId: 1,
  materiaNombre: 'Matematicas',
  materiaClave: 'M1',
  maestroId: 1,
  maestroNombre: 'Carlos Perez',
  turnoHorarioId: 1,
  diaSemana: 1,
  horaInicio: '07:00',
  horaFin: '08:00',
  aulaId: 1,
  aulaNombre: 'A1',
  version: 1,
  turnoId: 2,
  especialidadNombre: 'PROGRAMACION',
  grado: 1,
  ...parcial,
})

const materia = (clave: string, nombre: string, horasSemana: number): Materia => ({
  id: Number(clave.replace(/\D/g, '')) || 1,
  nombre,
  clave,
  creditos: 0,
  horasSemana,
  colorHex: '#2563eb',
  activo: true,
})

const turnos: Turno[] = [
  { id: 2, nombre: 'VESPERTINO', activo: true },
  { id: 3, nombre: 'MATUTINO', activo: false },
]

const materias: Materia[] = [
  materia('M1', 'Matematicas', 6),
  materia('M2', 'Fisica', 4),
  materia('M3', 'Quimica', 2),
]

const horario: Horario[] = [
  // Grado 1, dos grupos (1°A y 1°B) -> dos columnas.
  fila({ id: 1, grupoId: 10, grupoNombre: '1°A', materiaClave: 'M1', materiaNombre: 'Matematicas', maestroId: 1, maestroNombre: 'Carlos Perez', maestroApodo: 'Carlos' }),
  // Misma materia y mismo grupo, otro maestro: la celda debe decir "Carlos, Diana".
  fila({ id: 2, grupoId: 10, grupoNombre: '1°A', materiaClave: 'M1', materiaNombre: 'Matematicas', maestroId: 2, maestroNombre: 'Diana Gomez', maestroApodo: 'Diana' }),
  // Sin apodo: cae al nombre completo.
  fila({ id: 3, grupoId: 10, grupoNombre: '1°A', materiaClave: 'M2', materiaNombre: 'Fisica', maestroId: 3, maestroNombre: 'Manuel Ruiz', maestroApodo: null }),
  fila({ id: 4, grupoId: 10, grupoNombre: '1°A', materiaClave: 'M3', materiaNombre: 'Quimica', maestroId: 4, maestroNombre: 'Julio Sosa', maestroApodo: 'Julio' }),
  fila({ id: 5, grupoId: 11, grupoNombre: '1°B', materiaClave: 'M1', materiaNombre: 'Matematicas', maestroId: 5, maestroNombre: 'Ana Lima', maestroApodo: 'Ana' }),
  fila({ id: 6, grupoId: 11, grupoNombre: '1°B', materiaClave: 'M2', materiaNombre: 'Fisica', maestroId: 5, maestroNombre: 'Ana Lima', maestroApodo: 'Ana' }),
  fila({ id: 7, grupoId: 11, grupoNombre: '1°B', materiaClave: 'M3', materiaNombre: 'Quimica', maestroId: 6, maestroNombre: 'Lupita Vega', maestroApodo: 'Lupita' }),
  // Otro grado: bloque aparte. Se le da una materia distinta para que los textos no se repitan
  // entre bloques y las aserciones puedan apuntar a una fila concreta.
  fila({ id: 8, grupoId: 12, grupoNombre: '3°A', grado: 3, materiaClave: 'M5', materiaNombre: 'Calculo', maestroId: 7, maestroNombre: 'Pedro Nunez', maestroApodo: 'Pedro' }),
  // Grupo sin especialidad.
  fila({ id: 9, grupoId: 13, grupoNombre: '5°X', grado: 5, especialidadNombre: null, materiaClave: 'M4', materiaNombre: 'Seminario', maestroId: 8, maestroNombre: 'Sofia Lara', maestroApodo: 'Sofia' }),
  // NO deben salir: version vieja del horario y fila de otro turno.
  fila({ id: 10, version: 2, materiaClave: 'M9', materiaNombre: 'FUERA DE VERSION', grupoId: 10, grupoNombre: '1°A' }),
  fila({ id: 11, turnoId: 3, materiaClave: 'M8', materiaNombre: 'FUERA DE TURNO', grupoId: 10, grupoNombre: '1°A' }),
]

// --- Utilidades de la prueba ---------------------------------------------------------------

/** Monta el reporte y espera a que termine la carga (mientras tanto dibuja el girador). */
const montar = async () => {
  render(<ReporteCerebro />)
  // Este aviso solo existe cuando ya respondieron los tres servicios y no hay turno elegido.
  await screen.findByText('Selecciona un turno para ver el reporte.')
}

/** Elige un turno en el desplegable, que es lo que dispara el armado del reporte. */
const elegirTurno = (id: number) =>
  fireEvent.change(screen.getByRole('combobox'), { target: { value: String(id) } })

/** Fila de la tabla que contiene un texto dado (para mirar dentro de ella). */
const filaCon = (texto: string): HTMLElement => {
  const celda = screen.getByText(texto)
  const tr = celda.closest('tr')
  if (!tr) throw new Error(`No se encontro la fila de "${texto}"`)
  return tr
}

describe('Reporte Cerebro', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listarTurnos.mockResolvedValue({ data: { content: turnos } })
    listarMaterias.mockResolvedValue({ data: { content: materias } })
    obtenerTodosHorarios.mockResolvedValue({ data: horario })
  })

  it('sin turno elegido no dibuja el reporte y bloquea las exportaciones', async () => {
    await montar()

    expect(screen.getByText('Selecciona un turno para ver el reporte.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Guardar PDF/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Exportar Excel/i })).toBeDisabled()
  })

  it('ofrece solo los turnos activos', async () => {
    await montar()

    expect(screen.getByRole('option', { name: 'VESPERTINO' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'MATUTINO' })).not.toBeInTheDocument()
  })

  it('arma una columna por grupo del grado y una fila por materia, y habilita exportar', async () => {
    await montar()
    elegirTurno(2)

    // Una tarjeta por especialidad.
    expect(await screen.findByText('Especialidad: PROGRAMACION')).toBeInTheDocument()
    // Una columna por grupo de ese grado (no del plantel entero).
    expect(screen.getByRole('columnheader', { name: '1°A' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '1°B' })).toBeInTheDocument()
    // El otro grado va en su propio bloque, con su propia columna.
    expect(screen.getByRole('columnheader', { name: '3°A' })).toBeInTheDocument()
    // Una fila por materia, con sus horas.
    expect(within(filaCon('Fisica')).getByText('4')).toBeInTheDocument()
    // Ya se puede exportar.
    expect(screen.getByRole('button', { name: /Guardar PDF/i })).toBeEnabled()
  })

  it('usa el apodo del maestro y cae al nombre completo cuando no tiene', async () => {
    await montar()
    elegirTurno(2)
    await screen.findByText('Especialidad: PROGRAMACION')

    // M1 en 1°A: el apodo.
    expect(within(filaCon('Matematicas')).getByText(/Carlos/)).toBeInTheDocument()
    // M2 en 1°A: sin apodo, se muestra el nombre completo.
    expect(screen.getByText('Manuel Ruiz')).toBeInTheDocument()
    // En 1°B la misma materia la da otra maestra: cada grupo tiene su celda.
    expect(screen.getByText('Lupita')).toBeInTheDocument()
  })

  it('junta dos maestros de la misma materia y grupo en una sola celda', async () => {
    await montar()
    elegirTurno(2)
    await screen.findByText('Especialidad: PROGRAMACION')

    expect(screen.getByText('Carlos, Diana')).toBeInTheDocument()
  })

  it('suma en el renglon de cierre las horas de las materias del bloque', async () => {
    await montar()
    elegirTurno(2)
    await screen.findByText('Especialidad: PROGRAMACION')

    // Cada bloque cierra con su propio "Total de horas", asi que se busca el que suma 12:
    // Grado 1 = 6 + 4 + 2.
    const cierres = screen.getAllByText('Total de horas').map(t => t.closest('tr') as HTMLElement)
    const cierreGrado1 = cierres.find(tr => within(tr).queryByText('12'))
    expect(cierreGrado1).toBeTruthy()
  })

  it('REGRESION: ignora las versiones viejas del horario y las filas de otro turno', async () => {
    await montar()
    elegirTurno(2)
    await screen.findByText('Especialidad: PROGRAMACION')

    // Si no se filtrara por version, cada corrida guardada meteria sus filas y la matriz saldria
    // multiplicada. Si no se filtrara por turno, se mezclarian grupos de dos turnos.
    expect(screen.queryByText('FUERA DE VERSION')).not.toBeInTheDocument()
    expect(screen.queryByText('FUERA DE TURNO')).not.toBeInTheDocument()
  })

  it('da su propia tarjeta a los grupos sin especialidad', async () => {
    await montar()
    elegirTurno(2)

    expect(await screen.findByText('Especialidad: (sin especialidad)')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '5°X' })).toBeInTheDocument()
  })

  it('avisa cuando el horario vigente no tiene clases', async () => {
    obtenerTodosHorarios.mockResolvedValue({ data: [] })
    await montar()
    elegirTurno(2)

    expect(
      await screen.findByText('No hay clases en el horario vigente para estos grupos.'),
    ).toBeInTheDocument()
  })
})
