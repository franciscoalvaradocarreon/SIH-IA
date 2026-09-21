import React, { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { MdMeetingRoom, MdGroup, MdWarning, MdPerson } from 'react-icons/md';
import type { Horario, TurnoHorario } from '../types';
import {
  DIAS_SEMANA,
  formatearHora,
  getBackgroundColor,
  getBorderColor,
  tieneColor,
} from '../utils/horarioUtils';

export type VarianteFila = 'grupo' | 'maestro' | 'aula';

interface FilaBloqueHorarioProps {
  bloque: TurnoHorario;
  horarioIndex: Map<string, Horario[]>;
  /**
   * Define qué info secundaria se muestra en cada celda:
   *  - 'grupo'  (default): aulaNombre  (vista del grupo → ¿dónde es?)
   *  - 'maestro':          aulaNombre  (vista del maestro → ¿dónde es?)
   *  - 'aula':             grupoNombre (vista del aula → ¿quién está?)
   */
  variante?: VarianteFila;
}

// ────────────────────────────────────────────────────────────
// Tooltip renderizado en un portal (document.body) para que
// NINGÚN contenedor con overflow lo recorte.
// ────────────────────────────────────────────────────────────
interface TooltipPortalProps {
  contenido: React.ReactNode;
  children: React.ReactNode;
}

const TooltipPortal: React.FC<TooltipPortalProps> = ({ contenido, children }) => {
  const [posicion, setPosicion] = useState<{
    top: number;
    left: number;
    /** true → mostrar debajo de la celda. false → mostrar encima. */
    abajo: boolean;
  } | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  const mostrar = () => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();

    // Si hay menos de 140px por encima, mostramos el tooltip debajo.
    const abajo = rect.top < 140;

    setPosicion({
      // 8px de separación entre la celda y el tooltip
      top: abajo ? rect.bottom + 8 : rect.top - 8,
      left: rect.left + rect.width / 2,
      abajo,
    });
  };

  const ocultar = () => setPosicion(null);

  // Cerrar el tooltip al hacer scroll: con position: fixed, si el
  // usuario scrollea, la posición queda descolocada respecto a la celda.
  useEffect(() => {
    if (!posicion) return;
    const onScroll = () => setPosicion(null);
    // useCapture=true para capturar scroll en contenedores anidados
    window.addEventListener('scroll', onScroll, true);
    return () => window.removeEventListener('scroll', onScroll, true);
  }, [posicion]);

  return (
    <>
      <div
        ref={ref}
        onMouseEnter={mostrar}
        onMouseLeave={ocultar}
        className="relative"
      >
        {children}
      </div>

      {posicion &&
        createPortal(
          <div
            role="tooltip"
            style={{
              position: 'fixed',
              top: posicion.top,
              left: posicion.left,
              // Centrado horizontal + desplazamiento vertical según arriba/abajo
              transform: `translateX(-50%) ${
                posicion.abajo ? '' : 'translateY(-100%)'
              }`,
              // Por encima de todo, incluidos modales (que suelen usar z-50)
              zIndex: 9999,
            }}
            className="
              pointer-events-none
              bg-gray-900 dark:bg-gray-100
              text-white dark:text-gray-900
              rounded-lg px-3 py-2 shadow-2xl ring-1 ring-black/10 dark:ring-white/10
              min-w-[180px] max-w-[260px] text-left
            "
          >
            {contenido}

            {/* Flechita que apunta a la celda */}
            <div
              className="absolute w-2 h-2 rotate-45 bg-gray-900 dark:bg-gray-100"
              style={{
                left: '50%',
                // translateX para centrar; rotate-45 ya está en la clase
                transform: 'translateX(-50%) rotate(45deg)',
                // Si el tooltip está debajo, la flecha va arriba (top: -4).
                // Si está arriba, la flecha va abajo (bottom: -4).
                ...(posicion.abajo ? { top: -4 } : { bottom: -4 }),
              }}
            />
          </div>,
          document.body
        )}
    </>
  );
};

// ────────────────────────────────────────────────────────────
// Contenido del tooltip: maestro + materia + aula/grupo
// ────────────────────────────────────────────────────────────
const ContenidoTooltip: React.FC<{
  horario: Horario;
  variante: VarianteFila;
}> = ({ horario, variante }) => (
  <>
    <div className="flex items-center gap-1.5 mb-1">
      <MdPerson className="text-sm flex-shrink-0 opacity-80" />
      <span className="font-semibold text-xs truncate">
        {horario.maestroNombre}
      </span>
    </div>
  </>
);

// ────────────────────────────────────────────────────────────
// Fila del horario
// ────────────────────────────────────────────────────────────
const FilaBloqueHorario: React.FC<FilaBloqueHorarioProps> = React.memo(
  ({ bloque, horarioIndex, variante = 'grupo' }) => {
    // ── Descanso ──
    if (bloque.descanso === true) {
      return (
        <tr className="bg-amber-50/70 dark:bg-amber-900/15">
          <td className="px-3 py-1 whitespace-nowrap text-base font-medium text-gray-700 dark:text-gray-300">
            <div className="flex flex-col items-center leading-tight">
              <span>{formatearHora(bloque.horaInicio)}</span>
              <span className="text-xs text-gray-400">-</span>
              <span>{formatearHora(bloque.horaFin)}</span>
            </div>
          </td>

          <td colSpan={DIAS_SEMANA.length} className="px-3 py-2 text-center">
            <div className="flex items-center justify-center gap-2 text-amber-800 dark:text-amber-300">
              <span className="font-semibold uppercase tracking-wide text-3xl">
                D   e   s   c   a   n   s   o
              </span>
            </div>
          </td>
        </tr>
      );
    }

    const horaNorm = formatearHora(bloque.horaInicio);

    return (
      <tr className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
        <td className="px-3 py-1 whitespace-nowrap text-base font-medium text-gray-700 dark:text-gray-300">
          <div className="flex flex-col items-center leading-tight">
            <span>{formatearHora(bloque.horaInicio)}</span>
            <span className="text-xs text-gray-400">-</span>
            <span>{formatearHora(bloque.horaFin)}</span>
          </div>
        </td>

        {DIAS_SEMANA.map((dia) => {
          const horarios = horarioIndex.get(`${dia.value}|${horaNorm}`) ?? [];

          // ── Celda vacía ──
          if (horarios.length === 0) {
            return (
              <td key={dia.value} className="px-2 py-2 text-center">
                <span className="text-gray-300 dark:text-gray-600 text-xs">-</span>
              </td>
            );
          }

          // ── SOLAPAMIENTO: alerta visual ──
          if (horarios.length > 1) {
            return (
              <td
                key={dia.value}
                className="px-2 py-2 text-center bg-red-100 dark:bg-red-900/40 border-2 border-red-500"
                title={`⚠️ ${horarios.length} clases solapadas en este bloque`}
              >
                <div className="flex items-center justify-center gap-1 mb-1">
                  <MdWarning className="text-red-700 dark:text-red-300 text-lg" />
                  <span className="text-[11px] font-bold text-red-700 dark:text-red-300 uppercase">
                    {horarios.length} solapadas
                  </span>
                </div>
                <div className="space-y-1">
                  {horarios.map((h) => (
                    <div
                      key={h.id}
                      className="text-[10px] text-red-800 dark:text-red-200 leading-tight"
                    >
                      <span className="font-semibold">{h.materiaClave}</span>
                      <span className="mx-1">·</span>
                      <span>
                        {variante === 'aula' ? h.grupoNombre : h.aulaNombre}
                      </span>
                      {variante === 'maestro' && (
                        <>
                          <span className="mx-1">·</span>
                          <span>{h.grupoNombre}</span>
                        </>
                      )}
                      <span className="mx-1">·</span>
                      <span className="opacity-80">{h.maestroNombre}</span>
                    </div>
                  ))}
                </div>
              </td>
            );
          }

          // ── Celda normal (1 sola clase) ──
          const horario = horarios[0];
          const colorHex = horario.colorHex;
          const tieneColorLocal = tieneColor(colorHex);

          const etiquetaSecundaria =
            variante === 'aula' ? horario.grupoNombre : horario.aulaNombre;

          const IconoSecundario = variante === 'aula' ? MdGroup : MdMeetingRoom;

          return (
            <td key={dia.value} className="px-2 py-2 text-center">
              <TooltipPortal
                contenido={<ContenidoTooltip horario={horario} variante={variante} />}
              >
                <div
                  className={`rounded-lg p-1.5 text-xs transition-all duration-200 ${
                    tieneColorLocal
                      ? 'border-4 hover:shadow-lg hover:scale-105'
                      : 'border border-gray-400 dark:border-gray-600 hover:shadow-md'
                  }`}
                  style={{
                    borderColor: getBorderColor(colorHex),
                    backgroundColor: getBackgroundColor(colorHex),
                  }}
                >
                  <div className="p-1 font-semibold items-center justify-center gap-1 text-[20px] text-gray-500 dark:text-gray-400">
                    {horario.materiaClave}
                  </div>
                  <div className="p-1 flex items-center justify-center gap-1 text-[15px] text-gray-500 dark:text-gray-400">
                    <IconoSecundario className="text-sm" />
                    <span className="truncate max-w-[120px]">
                      {etiquetaSecundaria}
                    </span>
                    {variante === 'maestro' && (
                      <>
                        <span className="text-gray-400 dark:text-gray-500">·</span>
                        <span className="truncate max-w-[120px]">{horario.grupoNombre}</span>
                      </>
                    )}
                  </div>
                </div>
              </TooltipPortal>
            </td>
          );
        })}
      </tr>
    );
  }
);

FilaBloqueHorario.displayName = 'FilaBloqueHorario';

export default FilaBloqueHorario;