/* ============================================================================
 * Tema claro/oscuro al cargar la pagina.
 *
 * Esto ANTES era un <script> en linea dentro de index.html. Se movio a un fichero
 * propio a proposito: una politica CSP con "script-src 'self'" -la inyectan
 * algunos antivirus con escudo web y ciertas extensiones del navegador- bloquea
 * los scripts EN LINEA. Con el script bloqueado, la clase "dark" no se ponia al
 * cargar y el modo oscuro se perdia en cada refresco (solo pasaba en produccion,
 * porque en desarrollo Vite sirve el HTML sin esa politica).
 *
 * Un fichero del mismo origen si pasa ese filtro, asi que el tema vuelve a
 * aplicarse antes de que React pinte nada.
 *
 * Va en el <head> y SIN defer: tiene que ejecutarse antes del primer pintado,
 * que es justo lo que evita el parpadeo en claro.
 *
 * Sin modulos ni sintaxis moderna: se sirve tal cual, sin transpilar.
 * ==========================================================================*/
(function () {
  var guardado = null;
  try {
    guardado = localStorage.getItem('theme');
  } catch (e) {
    /* Almacenamiento bloqueado por el navegador: se usa el tema del sistema. */
  }

  var sistema = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  var elegido = guardado || sistema;

  if (elegido === 'dark') {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
})();
