// src/components/MarcaGrupo.tsx
import React from 'react';
import type { PatronGrupo } from '../utils/paletaGrupos';

/**
 * Marca del grupo: un rectángulo con su color y su patrón, para las casillas del tablero.
 *
 * Se dibuja en SVG con medidas en píxeles reales (no con un viewBox escalado) para que los
 * trazos del patrón conserven su grosor a cualquier tamaño y sigan leyéndose en barras de
 * 30 x 20 px. El contorno oscuro es lo que hace visible un color claro sobre el fondo blanco.
 *
 * El patrón NO es decoración: es el segundo canal de identificación (ver `paletaGrupos.ts`).
 */

/**
 * Id del clip con el que se recorta el patrón a la forma de la marca. Se repite entre
 * instancias del mismo color y patrón, y no pasa nada: la geometría es idéntica.
 */
const idClip = (color: string, patron: string) => `marca-${(color || 'gris').replace('#', '')}-${patron}`;

interface Props {
  /** Color base del grupo (hex). */
  color: string;
  patron?: PatronGrupo;
  /** Tinta de los trazos del patrón. */
  tinta?: string;
  /** Alto en px: es la medida que manda, porque la marca vive en un renglón de la tabla. */
  tamano?: number;
  /** Ancho en px. Si no se indica, sale del alto (1.5x). */
  ancho?: number;
  className?: string;
  /** Texto para lectores de pantalla. */
  titulo?: string;
}

const MarcaGrupo: React.FC<Props> = ({
  color,
  patron = 'solido',
  tinta = '#ffffff',
  tamano = 20,
  ancho,
  className = '',
  titulo,
}) => {
  const alto = tamano;
  const base = ancho ?? Math.round(tamano * 1.5);
  const radio = Math.max(2, Math.round(alto * 0.18));
  const grosor = Math.max(2, alto * 0.17);
  // Tres trazos a 45°, separados un tercio de la diagonal de la marca: la misma regla que en el
  // pin, para que "diagonal" signifique lo mismo en toda la pantalla.
  const separacion = (base + alto) / Math.SQRT2 / 3;
  const diagonales = patron === 'diagonal' ? [-separacion, 0, separacion] : [];
  // La diagonal que pasa por el centro corta el borde superior en x = (ancho + alto) / 2.
  const corteArriba = (base + alto) / 2;
  const id = idClip(color, patron);
  const cx = base / 2;
  const cy = alto / 2;

  return (
    <svg
      width={base}
      height={alto}
      viewBox={`0 0 ${base} ${alto}`}
      className={className}
      role="img"
      aria-label={titulo ?? 'grupo'}
    >
      <rect
        x="0.5"
        y="0.5"
        width={base - 1}
        height={alto - 1}
        rx={radio}
        fill={color}
        stroke="rgba(0,0,0,0.35)"
        strokeWidth="1"
      />
      {patron !== 'solido' && (
        <>
          <defs>
            <clipPath id={id}>
              <rect x="0.5" y="0.5" width={base - 1} height={alto - 1} rx={radio} />
            </clipPath>
          </defs>
          <g clipPath={`url(#${id})`} stroke={tinta} strokeWidth={grosor} strokeLinecap="round">
            {diagonales.map((d) => {
              const x = corteArriba + d * Math.SQRT2;
              return <line key={d} x1={x - alto} y1={alto} x2={x} y2={0} />;
            })}
            {patron === 'cruz' && (
              <>
                <line x1={cx} y1={-1} x2={cx} y2={alto + 1} />
                <line x1={-1} y1={cy} x2={base + 1} y2={cy} />
              </>
            )}
          </g>
        </>
      )}
    </svg>
  );
};

export default MarcaGrupo;