// src/utils/imagenes.ts

/** Base del backend para resolver las rutas relativas que sirve él mismo (/uploads/...). */
const BACKEND_URL = String(import.meta.env.VITE_BACKEND_URL ?? '').replace(/\/+$/, '');

/**
 * Devuelve la URL solo si su esquema es seguro; en caso contrario, null.
 *
 * Motivo: el campo `fotoUrl` se puede escribir a mano en los formularios. Sin esta
 * comprobación, un valor como `data:image/svg+xml;base64,...` o `javascript:...`
 * termina dentro de un atributo `src`. Con `data:` un SVG puede contener scripts,
 * y cualquier URL externa filtra la IP y el Referer del usuario a un tercero.
 */
export const safeImgSrc = (url?: string | null): string | null => {
  if (!url) return null;
  const valor = url.trim();
  if (!valor) return null;

  // Permitido: http(s) absoluto
  if (/^https?:\/\//i.test(valor)) return valor;

  // Rechazado: cualquier otro esquema (data:, javascript:, vbscript:, file:, blob:)
  if (/^[a-z][a-z0-9+.-]*:/i.test(valor)) return null;

  // Rechazado: protocol-relative (//host/ruta)
  if (valor.startsWith('//')) return null;

  // Permitido: ruta relativa al backend
  return valor.startsWith('/') ? valor : `/${valor}`;
};

/** Igual que safeImgSrc, pero resuelve las rutas relativas contra el backend. */
export const urlFoto = (url?: string | null): string | null => {
  const segura = safeImgSrc(url);
  if (!segura) return null;
  if (/^https?:\/\//i.test(segura)) return segura;
  return `${BACKEND_URL}${segura}`;
};
