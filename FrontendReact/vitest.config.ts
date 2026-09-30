import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

/**
 * Configuracion de las PRUEBAS del frontend (Vitest).
 *
 * Se APOYA en vite.config.ts en lugar de duplicarlo: mergeConfig une las dos configuraciones, asi
 * que alias, plugins y proxy siguen siendo los mismos. Si manana cambia Vite, las pruebas lo siguen
 * sin tocarse.
 *
 * Por que jsdom y no un navegador de verdad: jsdom es un DOM simulado dentro de Node, arranca en
 * milisegundos y no necesita Chrome instalado, asi que puede correr en la CI en cada push. Los
 * flujos que si necesitan navegador (capturas, rutas, integracion real) van aparte, con Playwright.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // Que cuenta como prueba.
      include: ['src/**/*.test.{ts,tsx}'],
      // Se ejecuta una vez por archivo de pruebas: matchers de jest-dom y apaños de jsdom.
      setupFiles: ['./src/pruebas/setup.ts'],
      // Sin globals: cada prueba importa describe/it/expect de 'vitest'. Asi el `tsc -b` de
      // `npm run build` no necesita declarar tipos extra en tsconfig.
      globals: false,
    },
  }),
)
