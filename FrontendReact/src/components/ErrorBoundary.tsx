import React from 'react';
import { MdError } from 'react-icons/md';

/**
 * RED DE SEGURIDAD DE UN BLOQUE DE LA PANTALLA.
 *
 * En React, una excepción durante el render desmonta TODO el árbol: la pantalla se queda en blanco y
 * el usuario no sabe qué pasó. Este componente de clase encierra un trozo de interfaz y, si algo
 * revienta dentro, pinta un aviso visible con el mensaje del error en lugar de dejar la página vacía.
 *
 * Solo protege el render (y los ciclos de vida) de sus hijos: NO captura errores de manejadores de
 * eventos, de promesas ni de código asíncrono. Por eso los datos se blindan además uno por uno.
 */
export interface ErrorBoundaryProps {
  children: React.ReactNode;
  /** Nombre del bloque protegido, para el aviso y para el log. */
  titulo?: string;
  /** Se llama al capturar el error (para registrar en consola, telemetría, etc.). */
  onError?: (error: Error, info: React.ErrorInfo) => void;
  /**
   * Si cambia de valor, el aviso se limpia y se vuelve a intentar pintar a los hijos: así una nueva
   * pre-validación no se queda con el error de la anterior.
   */
  resetKey?: unknown;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/** El mensaje del error, sin depender de que sea un Error de verdad. */
function textoDelError(error: unknown): string {
  if (error instanceof Error) return error.message || error.name || 'Error sin mensaje';
  if (typeof error === 'string') return error;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error(`[ErrorBoundary${this.props.titulo ? ` · ${this.props.titulo}` : ''}]`, error, info);
    this.props.onError?.(error, info);
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  reiniciar = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const titulo = this.props.titulo
      ? `No se pudo mostrar ${this.props.titulo}`
      : 'No se pudo mostrar este bloque';

    return (
      <div className="rounded-lg border border-gray-400 bg-white px-4 py-2.5 text-xs text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200">
        <p className="mb-1 flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
          <span className="flex shrink-0 items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-semibold text-red-800 dark:bg-red-900/40 dark:text-red-300">
            <MdError /> error
          </span>
          {titulo}
        </p>
        <p className="text-xs text-gray-600 dark:text-gray-300">
          La pre-validación siguió funcionando: el resto de los chequeos están ahí arriba. Este bloque
          se puede reintentar sin recargar la página.
        </p>
        <p className="mt-2 rounded-md bg-red-50 px-2 py-1.5 font-mono text-[11px] break-words text-red-800 dark:bg-red-900/20 dark:text-red-300">
          {textoDelError(error)}
        </p>
        <button
          onClick={this.reiniciar}
          className="mt-2 inline-flex items-center gap-1 rounded-md border border-gray-400 px-4 py-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          Reintentar
        </button>
      </div>
    );
  }
}

export default ErrorBoundary;
