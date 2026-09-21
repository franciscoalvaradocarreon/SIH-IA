import type { Horario, TurnoHorario } from '../types';

export const DIAS_SEMANA = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
] as const;

/** Valor que el backend usa como "sin color asignado". */
export const COLOR_SIN_ASIGNAR = '#808080';

export const formatearHora = (hora: string): string => hora.substring(0, 5);

export const tieneColor = (colorHex?: string): colorHex is string =>
  !!colorHex && colorHex !== COLOR_SIN_ASIGNAR;

export const getBackgroundColor = (colorHex?: string): string => {
  if (!tieneColor(colorHex)) return 'transparent';
  const hex = colorHex.slice(1);
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, 0.1)`;
};

export const getBorderColor = (colorHex?: string): string | undefined =>
  tieneColor(colorHex) ? colorHex : undefined;

/** Construye filas únicas por horaInicio a partir de los bloques del turno. */
export const construirBloquesFilas = (bloques: TurnoHorario[]): TurnoHorario[] => {
  const mapa = new Map<string, TurnoHorario>();
  for (const b of bloques) {
    if (!mapa.has(b.horaInicio)) mapa.set(b.horaInicio, b);
  }
  return [...mapa.values()].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
};

export const indexarHorarios = (horarios: Horario[]): Map<string, Horario[]> => {
  const m = new Map<string, Horario[]>();
  for (const h of horarios) {
    const key = `${h.diaSemana}|${formatearHora(h.horaInicio)}`;
    const arr = m.get(key);
    if (arr) arr.push(h);
    else m.set(key, [h]);
  }
  return m;
};

/** Normaliza respuestas que pueden venir como array plano o anidadas en `.data`. */
export function extraerLista<T>(respuesta: unknown): T[] {
  if (Array.isArray(respuesta)) return respuesta as T[];
  const data = (respuesta as { data?: unknown } | null)?.data;
  if (Array.isArray(data)) return data as T[];
  if (data && Array.isArray((data as { data?: unknown }).data)) {
    return (data as { data: T[] }).data;
  }
  return [];
}