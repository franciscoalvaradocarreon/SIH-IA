import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ICONOS_DISPONIBLES, ICONOS_MAP, IconPreview, IconRenderer } from '../iconos'

/**
 * PRUEBAS DEL STOCK DE ICONOS (src/utils/iconos.ts).
 *
 * El stock es lo que alimenta el selector de iconos del CRUD de Menús, así que un nombre que no
 * esté en el mapa se ve en la barra lateral como las dos primeras letras ("MD") en vez del icono.
 * Estas pruebas fijan las dos cosas que importan: que los iconos nuevos estén dados de alta y que
 * el respaldo siga funcionando cuando el nombre no existe.
 */

/** Los iconos del tablero manual que se dieron de alta para el "diseño del horario a mano". */
const NUEVOS = [
  'MdDesignServices',
  'MdViewKanban',
  'MdEditCalendar',
  'MdPanTool',
  'MdSwapHoriz',
]

describe('Stock de iconos', () => {
  it('trae dados de alta los iconos del tablero manual', () => {
    for (const nombre of NUEVOS) {
      expect(ICONOS_MAP[nombre], `${nombre} deberia estar en el mapa`).toBeDefined()
      expect(ICONOS_DISPONIBLES).toContain(nombre)
    }
  })

  it('todos los nombres del stock resuelven a un componente', () => {
    for (const nombre of ICONOS_DISPONIBLES) {
      expect(ICONOS_MAP[nombre], `${nombre} no resuelve a un componente`).toBeTruthy()
    }
    // La lista y el mapa salen del mismo objeto, pero conviene fijarlo: el selector de iconos del
    // CRUD de Menús se alimenta de ICONOS_DISPONIBLES. El piso es holgado a propósito.
    expect(ICONOS_DISPONIBLES).toEqual(Object.keys(ICONOS_MAP))
    expect(ICONOS_DISPONIBLES.length).toBeGreaterThanOrEqual(85)
  })

  it('IconRenderer dibuja el icono cuando el nombre existe', () => {
    const { container } = render(<IconRenderer name="MdDesignServices" />)

    // Material Design renderiza un <svg>; si cayera al respaldo habría un <span> con letras.
    expect(container.querySelector('svg')).not.toBeNull()
    expect(screen.queryByText('MD')).not.toBeInTheDocument()
  })

  it('IconRenderer cae a las dos primeras letras cuando el nombre no existe', () => {
    render(<IconRenderer name="MdQueNoExiste" />)

    expect(screen.getByText('MD')).toBeInTheDocument()
  })

  it('IconPreview muestra el nombre tal cual cuando no lo conoce', () => {
    render(<IconPreview name="Inventado" />)

    expect(screen.getByText('Inventado')).toBeInTheDocument()
  })
})
