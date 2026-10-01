import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ManualUsuario from '../ManualUsuario'
import { MANUAL, seccionesParaRol, tituloManual, versionParaRol } from '../../manual/manuales'

/**
 * PRUEBAS DEL MANUAL DE USUARIO.
 *
 * Lo que se fija aquí es la regla que se pidió: el ADMIN ve la versión del administrador y
 * CUALQUIER otro rol (coordinador, director, maestro o solo lectura) ve la del coordinador. Se
 * comprueba en las dos capas: la función que decide (versionParaRol/seccionesParaRol) y la pantalla
 * que lo pinta.
 */

// Los roles cambian de una prueba a otra, así que viven en un estado que se puede reescribir.
const estado = vi.hoisted(() => ({ roles: ['COORDINADOR'] as string[] }))

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ roles: estado.roles, rolesEscuelaActiva: estado.roles }),
}))

const montar = () =>
  render(
    <MemoryRouter>
      <ManualUsuario />
    </MemoryRouter>,
  )

describe('Manual de usuario', () => {
  beforeEach(() => {
    estado.roles = ['COORDINADOR']
  })

  it('el administrador ve la version del administrador, con las secciones de administracion', () => {
    estado.roles = ['ADMIN']
    montar()

    expect(screen.getByRole('heading', { level: 1, name: tituloManual('administrador') })).toBeInTheDocument()
    expect(screen.getByText('Versión administrador')).toBeInTheDocument()
    // Una seccion que solo existe en la version del administrador.
    expect(screen.getByRole('heading', { level: 2, name: /Usuarios y roles/ })).toBeInTheDocument()
  })

  it('el coordinador ve la version del coordinador y NO las secciones de administracion', () => {
    estado.roles = ['COORDINADOR']
    montar()

    expect(screen.getByRole('heading', { level: 1, name: tituloManual('coordinador') })).toBeInTheDocument()
    expect(screen.getByText('Versión coordinador')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: /Catálogos/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: /Usuarios y roles/ })).not.toBeInTheDocument()
  })

  it('cualquier otro rol (director, maestro, solo lectura) tambien ve la del coordinador', () => {
    for (const rol of ['DIRECTOR', 'MAESTRO', 'VIEWER']) {
      expect(versionParaRol([rol])).toBe('coordinador')
    }
    expect(versionParaRol(['ADMIN', 'COORDINADOR'])).toBe('administrador')

    estado.roles = ['MAESTRO']
    montar()
    expect(screen.getByRole('heading', { level: 1, name: tituloManual('coordinador') })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: /Menús y Rol-Menú/ })).not.toBeInTheDocument()
  })

  it('las secciones del administrador son las del coordinador mas las marcadas soloAdmin', () => {
    const soloAdmin = MANUAL.filter(s => s.soloAdmin)
    const deTodos = MANUAL.filter(s => !s.soloAdmin)

    // El manual debe traer de las dos: si algun dia todas fueran de un solo tipo, esto avisa.
    expect(soloAdmin.length).toBeGreaterThan(0)
    expect(deTodos.length).toBeGreaterThan(0)

    expect(seccionesParaRol(['ADMIN'])).toHaveLength(MANUAL.length)
    expect(seccionesParaRol(['COORDINADOR'])).toHaveLength(deTodos.length)
    expect(seccionesParaRol(['COORDINADOR']).some(s => s.soloAdmin)).toBe(false)
  })

  it('el indice y las secciones salen del mismo arreglo (no pueden desfasarse)', () => {
    estado.roles = ['ADMIN']
    const { container } = montar()

    // Una sección dibujada por cada sección del contenido (el id es el ancla del índice)...
    for (const seccion of MANUAL) {
      expect(container.querySelector(`#${seccion.id}`), `falta la seccion ${seccion.id}`).not.toBeNull()
    }
    // ...y un enlace de índice por cada una, apuntando a esas anclas.
    const enlaces = container.querySelectorAll('nav a[href^="#"]')
    expect(enlaces).toHaveLength(MANUAL.length)
  })
})
