import React from 'react';

interface SwitchToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  labelOff?: string;
  labelOn?: string;
  color?: 'blue' | 'green' | 'yellow' | 'red' | 'purple';
  disabled?: boolean;
  size?: 'sm' | 'md' | 'lg';
  /** Tamaño del texto del label. Si no se indica, se deriva de `size`. */
  textSize?: 'xs' | 'sm' | 'base' | 'lg' | 'xl';
  sinLabel?: boolean;
  className?: string;
  layout?: 'horizontal' | 'vertical';
  align?: 'left' | 'center' | 'right';
}

const COLORES = {
  blue:   'bg-blue-600 dark:bg-blue-500',
  green:  'bg-green-600 dark:bg-green-500',
  yellow: 'bg-yellow-500 dark:bg-yellow-600',
  red:    'bg-red-600 dark:bg-red-500',
  purple: 'bg-purple-600 dark:bg-purple-500',
};

const TAMANIOS = {
  sm: { track: 'h-5 w-9',  thumb: 'h-4 w-4', translate: 'translate-x-4', texto: 'text-xs' },
  md: { track: 'h-6 w-11', thumb: 'h-5 w-5', translate: 'translate-x-5', texto: 'text-sm' },
  lg: { track: 'h-7 w-14', thumb: 'h-6 w-6', translate: 'translate-x-7', texto: 'text-base' },
};

const TAMANIOS_TEXTO = {
  xs:   'text-xs',
  sm:   'text-sm',
  base: 'text-base',
  lg:   'text-lg',
  xl:   'text-xl',
};

const ALINEACIONES = {
  left:   'items-start text-left',
  center: 'items-center text-center',
  right:  'items-end text-right',
};

export const SwitchToggle: React.FC<SwitchToggleProps> = ({
  checked,
  onChange,
  label,
  labelOff = 'Inactivo',
  labelOn = 'Activo',
  color = 'blue',
  disabled = false,
  size = 'md',
  textSize,
  sinLabel = false,
  className = '',
  layout = 'horizontal',
  align = 'left',
}) => {
  const t = TAMANIOS[size];
  const toggle = () => !disabled && onChange(!checked);
  const texto = label ?? (checked ? labelOn : labelOff);

  // 🔥 Si no se pasa textSize, se deriva del tamaño del switch
  const claseTexto = textSize ? TAMANIOS_TEXTO[textSize] : t.texto;

  const switchButton = (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={toggle}
      className={`
        relative inline-flex flex-shrink-0 cursor-pointer rounded-full
        border-2 border-transparent transition-colors duration-200 ease-in-out
        focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
        dark:focus:ring-offset-gray-800
        ${t.track}
        ${checked ? COLORES[color] : 'bg-gray-200 dark:bg-gray-600'}
        ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
      `}
    >
      <span className="sr-only">{label || labelOn}</span>
      <span
        className={`
          pointer-events-none inline-block transform rounded-full
          bg-white shadow ring-0 transition duration-200 ease-in-out
          ${t.thumb}
          ${checked ? t.translate : 'translate-x-0'}
        `}
      />
    </button>
  );

  const labelSpan = !sinLabel && (
    <span
      onClick={toggle}
      className={`
        ${claseTexto} font-medium select-none transition-colors
        ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
        ${checked
          ? 'text-gray-800 dark:text-gray-200'
          : 'text-gray-500 dark:text-gray-400'}
      `}
    >
      {texto}
    </span>
  );

  if (layout === 'vertical') {
    return (
      <div className={`flex flex-col gap-1 ${ALINEACIONES[align]} ${className}`}>
        {labelSpan}
        {switchButton}
      </div>
    );
  }

  return (
    <div className={`flex items-center gap-3 ${className}`}>
      {switchButton}
      {labelSpan}
    </div>
  );
};