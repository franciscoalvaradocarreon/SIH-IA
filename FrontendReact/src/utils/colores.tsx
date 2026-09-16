/**
 * Lista de colores sugeridos para materias y asignaciones
 */
export const COLORES_SUGERIDOS = [
  '#B71C1C', // Rojo oscuro
  '#E91E63', // Rosa
  '#9C27B0', // Púrpura
  '#673AB7', // Púrpura oscuro
  '#3F51B5', // Índigo
  '#2196F3', // Azul
  '#03A9F4', // Azul claro
  '#00BCD4', // Cian
  '#009688', // Verde azulado
  '#4CAF50', // Verde
  '#8BC34A', // Verde claro
  '#CDDC39', // Lima
  '#FFEB3B', // Amarillo
  '#FFC107', // Ámbar
  '#FF9800', // Naranja
  '#FF5722', // Naranja oscuro
  '#795548', // Marrón
  '#9E9E9E', // Gris
  '#607D8B', // Azul grisáceo
  '#000000', // Negro
  '#FFFFFF', // Blanco
  '#1A237E', // Índigo oscuro
  '#004D40', // Verde oscuro
  '#1B5E20', // Verde oscuro
];

/**
 * Genera un color hexadecimal aleatorio de la lista de colores sugeridos
 */
export const generarColorAleatorio = (): string => {
  return COLORES_SUGERIDOS[Math.floor(Math.random() * COLORES_SUGERIDOS.length)];
};

/**
 * Verifica si un color es válido (formato hexadecimal)
 */
export const esColorValido = (color: string): boolean => {
  return /^#[0-9A-Fa-f]{6}$/.test(color);
};

/**
 * Componente para mostrar un círculo de color en la UI
 */
export const CirculoColor: React.FC<{ 
  color: string; 
  className?: string;
  onClick?: () => void;
  selected?: boolean;
}> = ({ color, className = '', onClick, selected = false }) => {
  return (
    <button type="button" onClick={onClick}
      className={`w-7 h-7 rounded-full border-2 transition-all duration-200 hover:scale-110 ${
        selected
          ? 'border-blue-500 ring-2 ring-blue-300 ring-offset-2 scale-110'
          : 'border-gray-300 dark:border-gray-600 hover:border-gray-400'
      } ${className}`}
      style={{ backgroundColor: color }}
      title={color}
    />
  );
};