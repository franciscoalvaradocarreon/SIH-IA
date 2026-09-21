--
-- PostgreSQL database dump
--

\restrict F5H7ofQpbCTNMrNIO1MGnMZTjJMn58ZBZM7eEs3cGLanq3zX235fQyOEEWeX1pt

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: sih; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sih;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: asignacion; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.asignacion (
    asignacion_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    materia_id bigint NOT NULL,
    maestro_id bigint NOT NULL,
    horas integer NOT NULL,
    color_hex character varying(7),
    activo boolean DEFAULT true,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    aula_id integer NOT NULL,
    distribucion character varying(15),
    semestre_id bigint NOT NULL,
    turno_id bigint NOT NULL
);


--
-- Name: asignacion_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.asignacion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asignacion_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.asignacion_id_seq OWNED BY sih.asignacion.asignacion_id;


--
-- Name: aulas; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.aulas (
    aula_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    nombre character varying(50) NOT NULL,
    edificio character varying(50),
    piso character varying(50),
    descripcion text,
    activo boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    semestre_id bigint NOT NULL,
    turno_id bigint NOT NULL,
    taller boolean DEFAULT false
);


--
-- Name: aulas_aula_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.aulas_aula_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aulas_aula_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.aulas_aula_id_seq OWNED BY sih.aulas.aula_id;


--
-- Name: aulas_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.aulas_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aulas_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.aulas_semestre_id_seq OWNED BY sih.aulas.semestre_id;


--
-- Name: disponibilidad_grupo; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.disponibilidad_grupo (
    disponibilidad_grupo_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    turno_horario_id bigint NOT NULL,
    semestre_id bigint NOT NULL,
    disponible boolean DEFAULT true NOT NULL,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: disponibilidad_grupo_disponibilidad_grupo_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.disponibilidad_grupo_disponibilidad_grupo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disponibilidad_grupo_disponibilidad_grupo_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.disponibilidad_grupo_disponibilidad_grupo_id_seq OWNED BY sih.disponibilidad_grupo.disponibilidad_grupo_id;


--
-- Name: disponibilidad_maestro; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.disponibilidad_maestro (
    disponibilidad_maestro_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    maestro_id bigint NOT NULL,
    turno_horario_id bigint NOT NULL,
    disponible boolean DEFAULT false,
    semestre_id bigint NOT NULL,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: disponibilidad_maestro_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.disponibilidad_maestro_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disponibilidad_maestro_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.disponibilidad_maestro_id_seq OWNED BY sih.disponibilidad_maestro.disponibilidad_maestro_id;


--
-- Name: disponibilidad_maestro_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.disponibilidad_maestro_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disponibilidad_maestro_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.disponibilidad_maestro_semestre_id_seq OWNED BY sih.disponibilidad_maestro.semestre_id;


--
-- Name: escuelas; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.escuelas (
    escuela_id bigint NOT NULL,
    nombre character varying(150) NOT NULL,
    direccion character varying(255),
    telefono character varying(20),
    activo boolean DEFAULT true,
    logo_url character varying(255),
    clave character varying(30),
    nombre_largo character varying(150),
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: especialidad; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.especialidad (
    especialidad_id bigint NOT NULL,
    nombre character varying(50) NOT NULL,
    semestre_id bigint NOT NULL,
    turno_id bigint NOT NULL
);


--
-- Name: especialidad_especialidad_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.especialidad ALTER COLUMN especialidad_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.especialidad_especialidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999999
    CACHE 1
);


--
-- Name: especialidad_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.especialidad_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: especialidad_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.especialidad_semestre_id_seq OWNED BY sih.especialidad.semestre_id;


--
-- Name: especialidad_turno_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.especialidad_turno_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: especialidad_turno_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.especialidad_turno_id_seq OWNED BY sih.especialidad.turno_id;


--
-- Name: grupos; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.grupos (
    grupo_id integer NOT NULL,
    escuela_id integer NOT NULL,
    nombre character varying(20) NOT NULL,
    grado integer NOT NULL,
    turno_id bigint NOT NULL,
    capacidad integer DEFAULT 0,
    activo boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    especialidad_id integer,
    semestre_id bigint NOT NULL,
    CONSTRAINT grupos_grado_check CHECK (((grado >= 1) AND (grado <= 6)))
);


--
-- Name: grupos_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.grupos_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupos_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.grupos_id_seq OWNED BY sih.grupos.grupo_id;


--
-- Name: grupos_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.grupos_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupos_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.grupos_semestre_id_seq OWNED BY sih.grupos.semestre_id;


--
-- Name: horario; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.horario (
    horario_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    asignacion_id bigint NOT NULL,
    turno_horario_id bigint NOT NULL,
    aula_id bigint NOT NULL,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    version integer DEFAULT 1,
    semestre_id bigint NOT NULL,
    maestro_id bigint NOT NULL
);


--
-- Name: horario_horario_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.horario_horario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: horario_horario_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.horario_horario_id_seq OWNED BY sih.horario.horario_id;


--
-- Name: horario_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.horario_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: horario_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.horario_semestre_id_seq OWNED BY sih.horario.semestre_id;


--
-- Name: maestros; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.maestros (
    maestro_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    nombre character varying(80) NOT NULL,
    apellidos character varying(100) NOT NULL,
    email character varying(100),
    foto_url character varying(100),
    activo boolean DEFAULT true,
    telefono character varying(40),
    titulo character varying(20),
    semestre_id bigint NOT NULL,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    turno_id bigint NOT NULL,
    apodo character varying(15)
);


--
-- Name: maestros_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.maestros_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maestros_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.maestros_semestre_id_seq OWNED BY sih.maestros.semestre_id;


--
-- Name: materias; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.materias (
    materia_id integer NOT NULL,
    escuela_id integer NOT NULL,
    nombre character varying(100) NOT NULL,
    clave character varying(50) NOT NULL,
    descripcion text,
    creditos integer DEFAULT 0,
    color_hex character varying(7) DEFAULT '#808080'::character varying,
    activo boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    horas_semana integer NOT NULL,
    semestre_id bigint NOT NULL,
    turno_id bigint NOT NULL
);


--
-- Name: materias_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.materias_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materias_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.materias_id_seq OWNED BY sih.materias.materia_id;


--
-- Name: materias_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.materias_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materias_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.materias_semestre_id_seq OWNED BY sih.materias.semestre_id;


--
-- Name: menu; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.menu (
    menu_id bigint NOT NULL,
    pariente_id bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    ruta character varying(255),
    icono character varying(50),
    menu_orden integer DEFAULT 0 NOT NULL,
    activo boolean DEFAULT true,
    creado time without time zone DEFAULT now(),
    nivel integer DEFAULT 1 NOT NULL
);


--
-- Name: menu_menu_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.menu ALTER COLUMN menu_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.menu_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 99999
    CACHE 1
);


--
-- Name: password_reset_token; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.password_reset_token (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    expira_en timestamp without time zone NOT NULL,
    usado boolean DEFAULT false NOT NULL,
    creado timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: password_reset_token_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.password_reset_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_reset_token_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.password_reset_token_id_seq OWNED BY sih.password_reset_token.id;


--
-- Name: rol_menu; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.rol_menu (
    rol_id bigint NOT NULL,
    menu_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.roles (
    rol_id bigint NOT NULL,
    nombre character varying(30) NOT NULL,
    descripcion character varying(150)
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.roles ALTER COLUMN rol_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999
    CACHE 1
);


--
-- Name: schools_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.escuelas ALTER COLUMN escuela_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.schools_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 10000
    CACHE 1
);


--
-- Name: semestre; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.semestre (
    semestre_id bigint NOT NULL,
    escuela_id integer NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion text,
    activo boolean DEFAULT true,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE semestre; Type: COMMENT; Schema: sih; Owner: -
--

COMMENT ON TABLE sih.semestre IS 'Períodos académicos (semestres)';


--
-- Name: COLUMN semestre.nombre; Type: COMMENT; Schema: sih; Owner: -
--

COMMENT ON COLUMN sih.semestre.nombre IS 'Ej: "Semestre 2025-1", "Enero-Junio 2025"';


--
-- Name: COLUMN semestre.activo; Type: COMMENT; Schema: sih; Owner: -
--

COMMENT ON COLUMN sih.semestre.activo IS 'Indica si es el semestre actual';


--
-- Name: semestre_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.semestre_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: semestre_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.semestre_semestre_id_seq OWNED BY sih.semestre.semestre_id;


--
-- Name: turno; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.turno (
    turno_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    nombre character varying(40) NOT NULL,
    description text,
    activo boolean DEFAULT true,
    semestre_id bigint
);


--
-- Name: shifts_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.turno ALTER COLUMN turno_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.shifts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9999
    CACHE 1
);


--
-- Name: teachers_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.maestros ALTER COLUMN maestro_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.teachers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 1000000
    CACHE 1
);


--
-- Name: turno_horario; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.turno_horario (
    turno_horario_id bigint NOT NULL,
    turno_id bigint NOT NULL,
    dia_semana bigint NOT NULL,
    hora_inicio time(0) without time zone NOT NULL,
    hora_fin time(0) without time zone NOT NULL,
    descanso boolean DEFAULT false,
    orden integer DEFAULT 0,
    semestre_id bigint NOT NULL
);


--
-- Name: turno_horario_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.turno_horario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: turno_horario_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.turno_horario_id_seq OWNED BY sih.turno_horario.turno_horario_id;


--
-- Name: turno_horario_semestre_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

CREATE SEQUENCE sih.turno_horario_semestre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: turno_horario_semestre_id_seq; Type: SEQUENCE OWNED BY; Schema: sih; Owner: -
--

ALTER SEQUENCE sih.turno_horario_semestre_id_seq OWNED BY sih.turno_horario.semestre_id;


--
-- Name: usuarios; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.usuarios (
    usuario_id bigint NOT NULL,
    usuario character varying(50) NOT NULL,
    password_hash character varying(255) NOT NULL,
    nombre_completo character varying(150) NOT NULL,
    email character varying(100),
    foto_url character varying(255),
    activo boolean DEFAULT true,
    creado time without time zone DEFAULT CURRENT_TIMESTAMP,
    ultimo_acceso timestamp without time zone,
    google_sub character varying(255)
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.usuarios ALTER COLUMN usuario_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 10000
    CACHE 1
);


--
-- Name: usuario_escuela_rol; Type: TABLE; Schema: sih; Owner: -
--

CREATE TABLE sih.usuario_escuela_rol (
    usuario_escuela_rol_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    rol_id bigint NOT NULL,
    activo boolean DEFAULT true,
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: usuario_escuela_rol_usuario_escuela_rol_id_seq; Type: SEQUENCE; Schema: sih; Owner: -
--

ALTER TABLE sih.usuario_escuela_rol ALTER COLUMN usuario_escuela_rol_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sih.usuario_escuela_rol_usuario_escuela_rol_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999
    CACHE 1
);


--
-- Name: asignacion asignacion_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion ALTER COLUMN asignacion_id SET DEFAULT nextval('sih.asignacion_id_seq'::regclass);


--
-- Name: aulas aula_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas ALTER COLUMN aula_id SET DEFAULT nextval('sih.aulas_aula_id_seq'::regclass);


--
-- Name: disponibilidad_grupo disponibilidad_grupo_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo ALTER COLUMN disponibilidad_grupo_id SET DEFAULT nextval('sih.disponibilidad_grupo_disponibilidad_grupo_id_seq'::regclass);


--
-- Name: disponibilidad_maestro disponibilidad_maestro_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro ALTER COLUMN disponibilidad_maestro_id SET DEFAULT nextval('sih.disponibilidad_maestro_id_seq'::regclass);


--
-- Name: disponibilidad_maestro semestre_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro ALTER COLUMN semestre_id SET DEFAULT nextval('sih.disponibilidad_maestro_semestre_id_seq'::regclass);


--
-- Name: grupos grupo_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos ALTER COLUMN grupo_id SET DEFAULT nextval('sih.grupos_id_seq'::regclass);


--
-- Name: grupos semestre_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos ALTER COLUMN semestre_id SET DEFAULT nextval('sih.grupos_semestre_id_seq'::regclass);


--
-- Name: horario horario_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario ALTER COLUMN horario_id SET DEFAULT nextval('sih.horario_horario_id_seq'::regclass);


--
-- Name: materias materia_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias ALTER COLUMN materia_id SET DEFAULT nextval('sih.materias_id_seq'::regclass);


--
-- Name: password_reset_token id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.password_reset_token ALTER COLUMN id SET DEFAULT nextval('sih.password_reset_token_id_seq'::regclass);


--
-- Name: semestre semestre_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.semestre ALTER COLUMN semestre_id SET DEFAULT nextval('sih.semestre_semestre_id_seq'::regclass);


--
-- Name: turno_horario turno_horario_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno_horario ALTER COLUMN turno_horario_id SET DEFAULT nextval('sih.turno_horario_id_seq'::regclass);


--
-- Name: turno_horario semestre_id; Type: DEFAULT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno_horario ALTER COLUMN semestre_id SET DEFAULT nextval('sih.turno_horario_semestre_id_seq'::regclass);


--
-- Name: grupos Escuela_semestre_especialidad_turno_nombre_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT "Escuela_semestre_especialidad_turno_nombre_unique" UNIQUE (escuela_id, semestre_id, especialidad_id, turno_id, nombre);


--
-- Name: aulas Escuela_semestre_turno_nombre; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas
    ADD CONSTRAINT "Escuela_semestre_turno_nombre" UNIQUE (escuela_id, semestre_id, turno_id, nombre);


--
-- Name: escuelas PK_school_id; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.escuelas
    ADD CONSTRAINT "PK_school_id" PRIMARY KEY (escuela_id);


--
-- Name: especialidad Semestre_turno_nombre; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.especialidad
    ADD CONSTRAINT "Semestre_turno_nombre" UNIQUE (semestre_id, turno_id, nombre);


--
-- Name: maestros Teachers_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.maestros
    ADD CONSTRAINT "Teachers_pkey" PRIMARY KEY (maestro_id);


--
-- Name: turno_horario Turno_Horario_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno_horario
    ADD CONSTRAINT "Turno_Horario_pkey" PRIMARY KEY (turno_horario_id);


--
-- Name: asignacion asignacion_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT asignacion_pkey PRIMARY KEY (asignacion_id);


--
-- Name: aulas aulas_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas
    ADD CONSTRAINT aulas_pkey PRIMARY KEY (aula_id);


--
-- Name: turno_horario chk_dia; Type: CHECK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE sih.turno_horario
    ADD CONSTRAINT chk_dia CHECK (((dia_semana >= 1) AND (dia_semana <= 5))) NOT VALID;


--
-- Name: disponibilidad_grupo disponibilidad_grupo_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT disponibilidad_grupo_pkey PRIMARY KEY (disponibilidad_grupo_id);


--
-- Name: disponibilidad_maestro disponibilidad_maestro_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro
    ADD CONSTRAINT disponibilidad_maestro_pkey PRIMARY KEY (disponibilidad_maestro_id);


--
-- Name: usuarios email_Unk; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuarios
    ADD CONSTRAINT "email_Unk" UNIQUE (email) INCLUDE (email);


--
-- Name: materias escuela_semestre_turno_clave; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias
    ADD CONSTRAINT escuela_semestre_turno_clave UNIQUE (escuela_id, semestre_id, turno_id, clave);


--
-- Name: maestros escuela_semestre_turno_nombre_apellido_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.maestros
    ADD CONSTRAINT escuela_semestre_turno_nombre_apellido_unique UNIQUE (escuela_id, semestre_id, turno_id, nombre, apellidos);


--
-- Name: especialidad especialidad_pk; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.especialidad
    ADD CONSTRAINT especialidad_pk PRIMARY KEY (especialidad_id);


--
-- Name: disponibilidad_grupo grupo_turno_semestre_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT grupo_turno_semestre_unique UNIQUE (grupo_id, turno_horario_id, semestre_id);


--
-- Name: grupos grupos_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT grupos_pkey PRIMARY KEY (grupo_id);


--
-- Name: horario horario_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_pkey PRIMARY KEY (horario_id);


--
-- Name: materias materias_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias
    ADD CONSTRAINT materias_pkey PRIMARY KEY (materia_id);


--
-- Name: menu menu_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.menu
    ADD CONSTRAINT menu_pkey PRIMARY KEY (menu_id);


--
-- Name: password_reset_token password_reset_token_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.password_reset_token
    ADD CONSTRAINT password_reset_token_pkey PRIMARY KEY (id);


--
-- Name: password_reset_token password_reset_token_token_hash_key; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.password_reset_token
    ADD CONSTRAINT password_reset_token_token_hash_key UNIQUE (token_hash);


--
-- Name: rol_menu rol_menu_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.rol_menu
    ADD CONSTRAINT rol_menu_pkey PRIMARY KEY (rol_id, menu_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (rol_id);


--
-- Name: semestre semestre_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.semestre
    ADD CONSTRAINT semestre_pkey PRIMARY KEY (semestre_id);


--
-- Name: turno shifts_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno
    ADD CONSTRAINT shifts_pkey PRIMARY KEY (turno_id);


--
-- Name: turno turno_escuela_semestre_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno
    ADD CONSTRAINT turno_escuela_semestre_unique UNIQUE (turno_id, escuela_id, semestre_id);


--
-- Name: turno_horario turno_horario_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno_horario
    ADD CONSTRAINT turno_horario_unique UNIQUE (turno_id, dia_semana, hora_inicio);


--
-- Name: semestre uk_escuela_nombre; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.semestre
    ADD CONSTRAINT uk_escuela_nombre UNIQUE (escuela_id, nombre);


--
-- Name: usuarios users_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuarios
    ADD CONSTRAINT users_pkey PRIMARY KEY (usuario_id);


--
-- Name: usuario_escuela_rol usuario_escuela_rol_pkey; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuario_escuela_rol
    ADD CONSTRAINT usuario_escuela_rol_pkey PRIMARY KEY (usuario_escuela_rol_id);


--
-- Name: usuario_escuela_rol usuario_escuela_rol_unique; Type: CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuario_escuela_rol
    ADD CONSTRAINT usuario_escuela_rol_unique UNIQUE (usuario_id, escuela_id, rol_id) INCLUDE (usuario_id, escuela_id, rol_id);


--
-- Name: idx_asignacion_grupo; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_asignacion_grupo ON sih.asignacion USING btree (grupo_id);


--
-- Name: idx_asignacion_maestro; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_asignacion_maestro ON sih.asignacion USING btree (maestro_id);


--
-- Name: idx_disponibilidad_maestro_dia; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_disponibilidad_maestro_dia ON sih.disponibilidad_maestro USING btree (turno_horario_id);


--
-- Name: idx_disponibilidad_maestro_maestro; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_disponibilidad_maestro_maestro ON sih.disponibilidad_maestro USING btree (maestro_id);


--
-- Name: idx_disponibilidad_maestro_turno; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_disponibilidad_maestro_turno ON sih.disponibilidad_maestro USING btree (turno_horario_id);


--
-- Name: idx_especialidad_semestre; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_especialidad_semestre ON sih.especialidad USING btree (semestre_id);


--
-- Name: idx_especialidad_turno; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_especialidad_turno ON sih.especialidad USING btree (turno_id);


--
-- Name: idx_horario_asignacion; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_horario_asignacion ON sih.horario USING btree (asignacion_id);


--
-- Name: idx_horario_aula; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_horario_aula ON sih.horario USING btree (aula_id);


--
-- Name: idx_horario_grupo; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_horario_grupo ON sih.horario USING btree (grupo_id);


--
-- Name: idx_horario_maestro; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_horario_maestro ON sih.horario USING btree (maestro_id, semestre_id);


--
-- Name: idx_horario_turno_horario; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_horario_turno_horario ON sih.horario USING btree (turno_horario_id);


--
-- Name: idx_maestros_semestre; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_maestros_semestre ON sih.maestros USING btree (semestre_id);


--
-- Name: idx_maestros_turno; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_maestros_turno ON sih.maestros USING btree (turno_id);


--
-- Name: idx_prt_token_hash; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_prt_token_hash ON sih.password_reset_token USING btree (token_hash);


--
-- Name: idx_prt_usuario_activo; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_prt_usuario_activo ON sih.password_reset_token USING btree (usuario_id) WHERE (usado = false);


--
-- Name: idx_semestre_escuela; Type: INDEX; Schema: sih; Owner: -
--

CREATE INDEX idx_semestre_escuela ON sih.semestre USING btree (escuela_id);


--
-- Name: aulas Semestre_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas
    ADD CONSTRAINT "Semestre_id_fkey" FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: aulas Turno_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas
    ADD CONSTRAINT "Turno_id_fkey" FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: usuario_escuela_rol escuela_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuario_escuela_rol
    ADD CONSTRAINT escuela_fkey FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) NOT VALID;


--
-- Name: aulas escuela_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.aulas
    ADD CONSTRAINT escuela_id_fkey FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: grupos especialidad_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT especialidad_id_fkey FOREIGN KEY (especialidad_id) REFERENCES sih.especialidad(especialidad_id) NOT VALID;


--
-- Name: especialidad especialidad_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.especialidad
    ADD CONSTRAINT especialidad_id_fkey FOREIGN KEY (especialidad_id) REFERENCES sih.especialidad(especialidad_id) NOT VALID;


--
-- Name: asignacion fk_carga_aula; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_aula FOREIGN KEY (aula_id) REFERENCES sih.aulas(aula_id);


--
-- Name: asignacion fk_carga_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: asignacion fk_carga_grupo; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_grupo FOREIGN KEY (grupo_id) REFERENCES sih.grupos(grupo_id) ON DELETE CASCADE;


--
-- Name: asignacion fk_carga_maestro; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_maestro FOREIGN KEY (maestro_id) REFERENCES sih.maestros(maestro_id) ON DELETE CASCADE;


--
-- Name: asignacion fk_carga_materia; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_materia FOREIGN KEY (materia_id) REFERENCES sih.materias(materia_id) ON DELETE RESTRICT;


--
-- Name: asignacion fk_carga_semestre; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_semestre FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: asignacion fk_carga_turno_id; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.asignacion
    ADD CONSTRAINT fk_carga_turno_id FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: disponibilidad_maestro fk_disp_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro
    ADD CONSTRAINT fk_disp_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: disponibilidad_grupo fk_disp_grupo_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT fk_disp_grupo_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id);


--
-- Name: disponibilidad_grupo fk_disp_grupo_grupo; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT fk_disp_grupo_grupo FOREIGN KEY (grupo_id) REFERENCES sih.grupos(grupo_id);


--
-- Name: disponibilidad_grupo fk_disp_grupo_semestre; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT fk_disp_grupo_semestre FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id);


--
-- Name: disponibilidad_grupo fk_disp_grupo_turno_horario; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_grupo
    ADD CONSTRAINT fk_disp_grupo_turno_horario FOREIGN KEY (turno_horario_id) REFERENCES sih.turno_horario(turno_horario_id);


--
-- Name: disponibilidad_maestro fk_disp_maestro; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro
    ADD CONSTRAINT fk_disp_maestro FOREIGN KEY (maestro_id) REFERENCES sih.maestros(maestro_id) ON DELETE CASCADE;


--
-- Name: disponibilidad_maestro fk_disp_semestre_id; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro
    ADD CONSTRAINT fk_disp_semestre_id FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: disponibilidad_maestro fk_disp_turno_horario; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.disponibilidad_maestro
    ADD CONSTRAINT fk_disp_turno_horario FOREIGN KEY (turno_horario_id) REFERENCES sih.turno_horario(turno_horario_id) ON DELETE CASCADE;


--
-- Name: especialidad fk_especialidad_semestre; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.especialidad
    ADD CONSTRAINT fk_especialidad_semestre FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id);


--
-- Name: especialidad fk_especialidad_turno; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.especialidad
    ADD CONSTRAINT fk_especialidad_turno FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id);


--
-- Name: grupos fk_grupo_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT fk_grupo_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: maestros fk_maestros_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.maestros
    ADD CONSTRAINT fk_maestros_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id);


--
-- Name: maestros fk_maestros_semestre; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.maestros
    ADD CONSTRAINT fk_maestros_semestre FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id);


--
-- Name: maestros fk_maestros_turno; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.maestros
    ADD CONSTRAINT fk_maestros_turno FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: materias fk_materia_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias
    ADD CONSTRAINT fk_materia_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: materias fk_materia_turno; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias
    ADD CONSTRAINT fk_materia_turno FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: semestre fk_semestre_escuela; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.semestre
    ADD CONSTRAINT fk_semestre_escuela FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id);


--
-- Name: grupos fk_turno; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT fk_turno FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: turno_horario fk_turno_horario; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno_horario
    ADD CONSTRAINT fk_turno_horario FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) NOT VALID;


--
-- Name: horario horario_asignacion_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_asignacion_id_fkey FOREIGN KEY (asignacion_id) REFERENCES sih.asignacion(asignacion_id) ON DELETE CASCADE;


--
-- Name: horario horario_aula_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_aula_id_fkey FOREIGN KEY (aula_id) REFERENCES sih.aulas(aula_id) ON DELETE CASCADE;


--
-- Name: horario horario_escuela_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_escuela_id_fkey FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE;


--
-- Name: horario horario_grupo_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES sih.grupos(grupo_id) ON DELETE CASCADE;


--
-- Name: horario horario_maestro_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_maestro_id_fkey FOREIGN KEY (maestro_id) REFERENCES sih.maestros(maestro_id) NOT VALID;


--
-- Name: horario horario_semestre_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_semestre_id_fkey FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: horario horario_turno_horario_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.horario
    ADD CONSTRAINT horario_turno_horario_id_fkey FOREIGN KEY (turno_horario_id) REFERENCES sih.turno_horario(turno_horario_id) ON DELETE CASCADE;


--
-- Name: password_reset_token password_reset_token_usuario_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.password_reset_token
    ADD CONSTRAINT password_reset_token_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES sih.usuarios(usuario_id) ON DELETE CASCADE;


--
-- Name: usuario_escuela_rol rol_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuario_escuela_rol
    ADD CONSTRAINT rol_fkey FOREIGN KEY (rol_id) REFERENCES sih.roles(rol_id) NOT VALID;


--
-- Name: rol_menu rol_menu_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.rol_menu
    ADD CONSTRAINT rol_menu_id_fkey FOREIGN KEY (rol_id) REFERENCES sih.roles(rol_id) ON DELETE CASCADE;


--
-- Name: rol_menu rol_menu_menu_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.rol_menu
    ADD CONSTRAINT rol_menu_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES sih.menu(menu_id) ON DELETE CASCADE;


--
-- Name: turno school_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno
    ADD CONSTRAINT school_id_fkey FOREIGN KEY (escuela_id) REFERENCES sih.escuelas(escuela_id) NOT VALID;


--
-- Name: turno semestre_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.turno
    ADD CONSTRAINT semestre_id_fkey FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: grupos semestre_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.grupos
    ADD CONSTRAINT semestre_id_fkey FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: materias semestre_id_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.materias
    ADD CONSTRAINT semestre_id_fkey FOREIGN KEY (semestre_id) REFERENCES sih.semestre(semestre_id) NOT VALID;


--
-- Name: usuario_escuela_rol usuario_fkey; Type: FK CONSTRAINT; Schema: sih; Owner: -
--

ALTER TABLE ONLY sih.usuario_escuela_rol
    ADD CONSTRAINT usuario_fkey FOREIGN KEY (usuario_id) REFERENCES sih.usuarios(usuario_id) NOT VALID;


--
-- PostgreSQL database dump complete
--

\unrestrict F5H7ofQpbCTNMrNIO1MGnMZTjJMn58ZBZM7eEs3cGLanq3zX235fQyOEEWeX1pt

