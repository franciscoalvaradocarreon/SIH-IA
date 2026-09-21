// src/components/PinHorario.tsx
import React from 'react';
import type { PatronGrupo } from '../utils/paletaGrupos';

/**
 * Pin (chincheta) del tablero manual de horarios.
 *
 * Se dibuja como SVG en línea en vez de usar imágenes: escala sin pixelarse, toma el
 * color del grupo por parámetro y no agrega peticiones ni dependencias.
 *
 * Partes, pensadas para que se distingan a simple vista sobre el corcho:
 *  - cabeza circular con degradado radial (volumen, como una chincheta real),
 *  - brillo especular arriba a la izquierda,
 *  - aguja cónica hacia abajo que "entra" en la celda,
 *  - anillo punteado cuando el pin está SIN COLOCAR (vive en la caja),
 *  - anillo rojo cuando el pin participa en un choque (maestro, grupo o aula),
 *  - patrón del grupo (diagonal o cruz) sobre la cabeza: con 20+ grupos el color solo no basta.
 *
 * El color nunca es la única señal: la etiqueta que acompaña al pin lleva la clave de
 * la materia y, en la caja, también el grupo.
 */
export type EstadoPin = 'colocado' | 'pendiente';

interface Props {
  /** Color del grupo (hex). */
  color: string;
  /** Patrón del grupo, dibujado encima del color (ver `paletaGrupos.ts`). */
  patron?: PatronGrupo;
  /** Tinta de los trazos del patron. */
  tinta?: string;
  estado?: EstadoPin;
  /** true si el pin está en un choque: se le pinta un anillo rojo. */
  conflicto?: boolean;
  /** Ancho en px; la altura se calcula sola. */
  tamano?: number;
  className?: string;
  /** Texto para lectores de pantalla y tooltip. */
  titulo?: string;
}

const PinHorario: React.FC<Props> = ({
  color,
  patron = 'solido',
  tinta = '#ffffff',
  estado = 'colocado',
  conflicto = false,
  tamano = 26,
  className = '',
  titulo,
}) => {
  const sufijo = (color || 'gris').replace('#', '');
  const idGradiente = `pin-grad-${sufijo}`;
  const idClipPatron = `pin-clip-${sufijo}`;
  const conPatron = patron !== 'solido';
  const alto = Math.round(tamano * 1.28);

  return (
    <svg
      viewBox="0 0 24 31"
      width={tamano}
      height={alto}
      className={className}
      role="img"
      aria-label={titulo ?? 'pin'}
    >
      {titulo && <title>{titulo}</title>}
      <defs>
        {!conPatron && (
          <radialGradient id={idGradiente} cx="34%" cy="28%" r="78%">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.9" />
            <stop offset="38%" stopColor={color} />
            <stop offset="100%" stopColor={color} stopOpacity="0.72" />
          </radialGradient>
        )}
        {conPatron && (
          <clipPath id={idClipPatron}>
            <circle cx="12" cy="12" r="10.4" />
          </clipPath>
        )}
      </defs>

      {/* aguja */}
      <path d="M12 19 L13.15 30.4 L10.85 30.4 Z" fill="#94a3b8" stroke="rgba(0,0,0,0.22)" strokeWidth="0.5" />

      {/* anillo de "sin colocar" */}
      {estado === 'pendiente' && (
        <circle cx="12" cy="12" r="12.4" fill="none" stroke={color} strokeWidth="1.5" strokeDasharray="3 2.5" />
      )}

      {/* cabeza */}
      <circle cx="12" cy="12" r="10.4" fill={conPatron ? color : `url(#${idGradiente})`} stroke="rgba(0,0,0,0.32)" strokeWidth="1" />

      {/* patrón: segundo canal de identificación, por si el tono no basta */}
      {conPatron && (
        <g clipPath={`url(#${idClipPatron})`} stroke={tinta} strokeWidth="3" strokeLinecap="round">
          {patron === 'diagonal' && (
            <>
              <line x1="-9.8" y1="24" x2="14.2" y2="0" />
              <line x1="0" y1="24" x2="24" y2="0" />
              <line x1="9.8" y1="24" x2="33.8" y2="0" />
            </>
          )}
          {patron === 'cruz' && (
            <>
              <line x1="12" y1="0" x2="12" y2="24" />
              <line x1="0" y1="12" x2="24" y2="12" />
            </>
          )}
        </g>
      )}

      {/* brillo */}
      {!conPatron && <ellipse cx="8.3" cy="8" rx="3.1" ry="2.1" fill="#ffffff" opacity="0.55" transform="rotate(-25 8.3 8)" />}

      {/* choque */}
      {conflicto && (
        <circle cx="12" cy="12" r="11.5" fill="none" stroke="#dc2626" strokeWidth="2.3" />
      )}
    </svg>
  );
};

export default PinHorario;
