/**
 * Preparacion comun de las pruebas del frontend.
 *
 * Se ejecuta una vez por archivo de pruebas. Hace tres cosas:
 *
 *   1. Registra los matchers de jest-dom (toBeInTheDocument, toBeDisabled, toHaveTextContent...),
 *      que son los que hacen legibles las aserciones sobre el DOM.
 *   2. Desmonta lo montado entre pruebas (ver abajo: hace falta hacerlo a mano).
 *   3. Tapa los huecos de jsdom. jsdom no trae matchMedia, ResizeObserver ni scrollIntoView, y los
 *      componentes que los usan se caen al montarse con un "is not a function" que no tiene nada
 *      que ver con lo que se esta probando. Se sustituyen por dobles vacios.
 */
import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// Testing Library se limpia sola SOLO si encuentra un afterEach global. Como aqui las pruebas
// importan sus funciones de 'vitest' (globals: false), hay que registrar el desmontaje a mano: sin
// esto, cada render se suma al DOM del anterior y las busquedas por texto encuentran varias copias.
afterEach(() => {
  cleanup()
})

// matchMedia: lo consultan los componentes que respetan el ancho de pantalla o el modo oscuro.
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (consulta: string) => ({
      matches: false,
      media: consulta,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  })
}

// ResizeObserver: lo usan los contenedores que se miden a si mismos.
if (!('ResizeObserver' in globalThis)) {
  class ResizeObserverFalso {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(globalThis, 'ResizeObserver', {
    writable: true,
    value: ResizeObserverFalso,
  })
}

// scrollIntoView: jsdom no lo implementa y revienta al mover el foco a un elemento fuera de vista.
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = () => {}
}
