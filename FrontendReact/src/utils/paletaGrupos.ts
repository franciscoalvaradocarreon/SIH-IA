/**
 * Identidad visual de los grupos del tablero manual: un color y un patrón.
 *
 * <h2>Por qué color + patrón y no sólo color</h2>
 * En un turno hay 20+ grupos y el color es la pista principal para saber a qué grupo
 * pertenece un pin que está en la caja (ahí no hay renglón ni columna que lo ubique).
 * El color solo no alcanza: el rojo y el verde, el rosa y el rojo, el azul y el morado o el
 * cyan y el verde se confunden entre sí, sobre todo con daltonismo rojo-verde (~8 % de los
 * hombres). El patrón agrega un segundo canal: aunque dos tonos se parezcan, la diagonal o
 * la cruz los separa. De paso, el tablero sigue siendo legible impreso en blanco y negro.
 *
 * <h2>Combinaciones</h2>
 * 8 colores × 3 patrones = 24 combinaciones, repartidas por la posición del grupo en la lista
 * del turno: el color cambia en cada posición y el patrón cada 8. Así dos grupos consecutivos
 * (columnas vecinas del tablero) siempre difieren en TONO, que es lo que más se distingue a
 * primera vista, y sólo comparten color los que están a 8 o más posiciones.
 *
 * A partir de 24 grupos la pareja se repite. El blanco se descartó: sobre el fondo blanco del
 * tablero no se ve.
 */

/** Patrón que se dibuja encima del color. */
export type PatronGrupo = 'solido' | 'diagonal' | 'cruz';

/** Colores base, en el orden en que se reparten. */
const COLORES = [
  '#dc2626', // rojo
  '#eab308', // amarillo
  '#16a34a', // verde
  '#2563eb', // azul
  '#111827', // negro
  '#7c3aed', // morado
  '#ec4899', // rosa
  '#06b6d4', // cyan
];

/** Nombre de cada color, para leyendas y textos accesibles. */
const NOMBRES = ['rojo', 'amarillo', 'verde', 'azul', 'negro', 'morado', 'rosa', 'cyan'];

/** Patrones, en el orden en que se reparten. */
const PATRONES: PatronGrupo[] = ['solido', 'diagonal', 'cruz'];

const TINTA_CLARA = '#ffffff';
const TINTA_OSCURA = '#111827';

/** Índice utilizable: entero >= 0. */
function normalizar(indice: number): number {
  return Number.isFinite(indice) && indice > 0 ? Math.floor(indice) : 0;
}

/** Luminancia relativa (WCAG) de un color hex. */
function luminancia(hex: string): number {
  const limpio = (hex || '').replace('#', '');
  if (limpio.length !== 6) return 0;
  const r = parseInt(limpio.slice(0, 2), 16) / 255;
  const g = parseInt(limpio.slice(2, 4), 16) / 255;
  const b = parseInt(limpio.slice(4, 6), 16) / 255;
  const lin = (v: number) => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

/** Razón de contraste WCAG entre dos luminancias. */
function contraste(a: number, b: number): number {
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

/** Color base del grupo según su posición en el turno. */
export function colorDeGrupo(indice: number): string {
  return COLORES[normalizar(indice) % COLORES.length];
}

/** Patrón del grupo según su posición en el turno. */
export function patronDeGrupo(indice: number): PatronGrupo {
  return PATRONES[Math.floor(normalizar(indice) / COLORES.length) % PATRONES.length];
}

/** Nombre del color asignado, para la leyenda. */
export function nombreDeGrupo(indice: number): string {
  return NOMBRES[normalizar(indice) % NOMBRES.length];
}

/**
 * Tinta del patrón: blanco o casi negro, el que más contraste dé con el color base. Se mide en
 * vez de decidirse a ojo porque el amarillo y el cyan son claros y ahí el blanco no se ve.
 */
export function tintaPatron(hex: string): string {
  const l = luminancia(hex);
  return contraste(l, 1) >= contraste(l, luminancia(TINTA_OSCURA)) ? TINTA_CLARA : TINTA_OSCURA;
}

/** Texto legible sobre el color dado (blanco en los oscuros, casi negro en los claros). */
export function textoContraste(hex: string): string {
  return luminancia(hex) > 0.45 ? TINTA_OSCURA : TINTA_CLARA;
}

/** Identidad completa de un grupo. */
export interface IdentidadGrupo {
  color: string;
  patron: PatronGrupo;
  tinta: string;
  nombre: string;
}

/** Color, patrón, tinta y nombre del grupo en una sola llamada. */
export function identidadGrupo(indice: number): IdentidadGrupo {
  const color = colorDeGrupo(indice);
  return { color, patron: patronDeGrupo(indice), tinta: tintaPatron(color), nombre: nombreDeGrupo(indice) };
}