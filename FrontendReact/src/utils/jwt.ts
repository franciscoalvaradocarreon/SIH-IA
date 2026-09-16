// src/utils/jwt.ts

/**
 * Utilidades para leer el JWT en el cliente.
 *
 * IMPORTANTE: aquí NO se valida la firma (el cliente no tiene la clave y no debe
 * tenerla). El backend es quien verifica la firma y los permisos. Esto sirve
 * únicamente para dos cosas de experiencia de usuario:
 *   1. No arrancar la aplicación con una sesión ya caducada.
 *   2. Saber qué roles tiene el usuario para no mostrarle pantallas que el
 *      backend le va a rechazar con 403.
 */

export interface PayloadJwt {
  sub?: string;
  usuarioId?: number;
  roles?: string[];
  escuelaId?: number;
  escuelaIds?: number[];
  iat?: number;
  exp?: number;
}

/** Decodifica el payload del JWT (base64url -> JSON). Devuelve null si no es legible. */
export const decodificarJwt = (token: string | null | undefined): PayloadJwt | null => {
  if (!token) return null;
  try {
    const partes = token.split('.');
    if (partes.length !== 3) return null;

    // base64url -> base64 + relleno
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');

    const bytes = Uint8Array.from(atob(relleno), (caracter) => caracter.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as PayloadJwt;
  } catch {
    return null;
  }
};

/** true si el token no existe, no es legible o ya expiró. */
export const tokenExpirado = (token: string | null | undefined): boolean => {
  const payload = decodificarJwt(token);
  if (!payload?.exp) return true;
  return payload.exp * 1000 <= Date.now();
};

/** Roles incluidos en el token (lista vacía si no hay o no es legible). */
export const rolesDelToken = (token: string | null | undefined): string[] => {
  const payload = decodificarJwt(token);
  return Array.isArray(payload?.roles) ? payload!.roles! : [];
};

/** Milisegundos que faltan para que expire el token (null si no es legible). */
export const milisegundosParaExpirar = (token: string | null | undefined): number | null => {
  const payload = decodificarJwt(token);
  if (!payload?.exp) return null;
  return payload.exp * 1000 - Date.now();
};
