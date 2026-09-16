// src/utils/iconos.ts
import * as IconsMd from 'react-icons/md';
import * as IconsPi from 'react-icons/pi';
import * as IconsAi from "react-icons/ai";
import { createElement } from 'react';

// 🔥 Mapeo centralizado de todos los iconos disponibles
export const ICONOS_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
    // === Material Design ===
    'MdDashboard': IconsMd.MdDashboard,
    'MdListAlt': IconsMd.MdListAlt,
    'MdPerson': IconsMd.MdPerson,
    'MdBook': IconsMd.MdBook,
    'MdGroup': IconsMd.MdGroup,
    'MdSchedule': IconsMd.MdSchedule,
    'MdAutoAwesome': IconsMd.MdAutoAwesome,
    'MdEdit': IconsMd.MdEdit,
    'MdRocket': IconsMd.MdRocket,
    'MdVisibility': IconsMd.MdVisibility,
    'MdPrint': IconsMd.MdPrint,
    'MdTableRows': IconsMd.MdTableRows,
    'MdAssignment': IconsMd.MdAssignment,
    'MdHome': IconsMd.MdHome,
    'MdSettings': IconsMd.MdSettings,
    'MdPeople': IconsMd.MdPeople,
    'MdEvent': IconsMd.MdEvent,
    'MdLogout': IconsMd.MdLogout,
    'MdMenu': IconsMd.MdMenu,
    'MdMenuOpen': IconsMd.MdMenuOpen,
    'MdAdminPanelSettings': IconsMd.MdAdminPanelSettings,
    'MdOutlineDoorFront': IconsMd.MdOutlineDoorFront,
    'MdMenuBook': IconsMd.MdMenuBook,
    'MdOutlinePersonalInjury': IconsMd.MdOutlinePersonalInjury,
    'MdOutlineShapeLine': IconsMd.MdOutlineShapeLine,
    'MdOutlineSubject': IconsMd.MdOutlineSubject,
    'MdOutlinePersonPin': IconsMd.MdOutlinePersonPin,
    'MdSchool': IconsMd.MdSchool,
    'MdLock': IconsMd.MdLock,
    'MdSecurity': IconsMd.MdSecurity,
    'MdEmail': IconsMd.MdEmail,
    'MdPhone': IconsMd.MdPhone,
    'MdPhoto': IconsMd.MdPhoto,
    'MdDelete': IconsMd.MdDelete,
    'MdAdd': IconsMd.MdAdd,
    'MdSearch': IconsMd.MdSearch,
    'MdCheckCircle': IconsMd.MdCheckCircle,
    'MdCancel': IconsMd.MdCancel,
    'MdRefresh': IconsMd.MdRefresh,
    'MdArrowForward': IconsMd.MdArrowForward,
    'MdSave': IconsMd.MdSave,
    'MdCategory': IconsMd.MdCategory,
    'MdClass': IconsMd.MdClass,
    'MdMeetingRoom': IconsMd.MdMeetingRoom,
    'MdElevator': IconsMd.MdElevator,
    'MdLocationOn': IconsMd.MdLocationOn,
    'MdKey': IconsMd.MdKey,
    'MdDarkMode': IconsMd.MdDarkMode,
    'MdLightMode': IconsMd.MdLightMode,
    'MdLockOpen': IconsMd.MdLockOpen,
    'MdPersonAdd': IconsMd.MdPersonAdd,
    'MdPersonRemove': IconsMd.MdPersonRemove,
    'MdCheck': IconsMd.MdCheck,
    'MdClose': IconsMd.MdClose,
    'MdDone': IconsMd.MdDone,
    'MdClear': IconsMd.MdClear,
    'MdArrowDropDown': IconsMd.MdArrowDropDown,
    'MdArrowDropUp': IconsMd.MdArrowDropUp,

    // === Phosphor Icons ===
    'PiBuildingApartment': IconsPi.PiBuildingApartment,
    'PiRowsPlusBottomDuotone': IconsPi.PiRowsPlusBottomDuotone,
    'PiUsers': IconsPi.PiUsers,
    'PiBookOpen': IconsPi.PiBookOpen,
    'PiCalendar': IconsPi.PiCalendar,
    'PiHouse': IconsPi.PiHouse,
    'PiGear': IconsPi.PiGear,
    'PiUser': IconsPi.PiUser,
    'PiUsersThree': IconsPi.PiUsersThree,
    'PiClock': IconsPi.PiClock,
    'PiPrinter': IconsPi.PiPrinter,
    'PiTable': IconsPi.PiTable,
    'PiFile': IconsPi.PiFile,
    'PiFolder': IconsPi.PiFolder,
    'PiPaperPlane': IconsPi.PiPaperPlane,
    'PiPhone': IconsPi.PiPhone,
    'PiEnvelope': IconsPi.PiEnvelope,
    'PiLock': IconsPi.PiLock,
    'PiKey': IconsPi.PiKey,
    'PiArrowRight': IconsPi.PiArrowRight,
    'PiArrowLeft': IconsPi.PiArrowLeft,
    'PiPlus': IconsPi.PiPlus,
    'PiMinus': IconsPi.PiMinus,
    'PiCheck': IconsPi.PiCheck,
    'AiTwotoneSchedule': IconsAi.AiTwotoneSchedule
};

// 🔥 Lista de nombres de iconos disponibles
export const ICONOS_DISPONIBLES = Object.keys(ICONOS_MAP);

// 🔥 Componente para renderizar icono
export const IconRenderer: React.FC<{ 
    name: string; 
    className?: string 
}> = ({ name, className = "w-5 h-5" }) => {
    const IconComponent = ICONOS_MAP[name];
    if (!IconComponent) {
        return createElement(
            'span',
            { className: `inline-flex items-center justify-center ${className} text-xs font-bold text-gray-400` },
            name.slice(0, 2).toUpperCase(),
        );
    }
    return createElement(IconComponent, { className });
};

// 🔥 Componente para preview de iconos en listas
export const IconPreview: React.FC<{ 
    name: string; 
    className?: string 
}> = ({ name, className = "w-5 h-5" }) => {
    const IconComponent = ICONOS_MAP[name];
    if (!IconComponent) {
        return createElement('span', { className: 'text-xs text-gray-400' }, name);
    }
    return createElement(IconComponent, { className });
};