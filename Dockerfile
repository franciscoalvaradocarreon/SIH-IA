# syntax=docker/dockerfile:1
# ============================================================================
# SIH-IA en un solo contenedor.
#
# Decision de diseno: el WAR ya lleva el front de React embebido en
# classpath:/static, asi que NO hay dos servidores ni CORS que coordinar. Quien
# atiende el puerto publico es Caddy (reverse proxy + HTTPS), definido en
# docker-compose.yml.
#
# Construccion en tres etapas para que la imagen final no lleve Node, Maven ni
# el codigo fuente: solo un JRE y el WAR.
# ============================================================================


# ---------- Etapa 1: compilar el frontend (React + Vite) ----------
FROM node:22-alpine AS front
WORKDIR /front

# Primero solo los manifiestos: si el codigo cambia pero no las dependencias,
# Docker reutiliza esta capa y se salta el 'npm ci'.
COPY FrontendReact/package.json FrontendReact/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY FrontendReact/ ./

# 'vite build' directo en lugar de 'npm run build': este ultimo ejecuta antes
# 'tsc -b' y un error de tipos en cualquier archivo ajeno al cambio frenaria el
# despliegue. El type-check vive en el CI; el bundle generado es identico.
RUN node node_modules/vite/bin/vite.js build


# ---------- Etapa 2: compilar el backend y empaquetar el WAR ----------
FROM maven:3.9-eclipse-temurin-21 AS back
WORKDIR /build

COPY BackendJava/pom.xml ./

# El cache de BuildKit conserva ~/.m2 entre builds: la primera vez descarga
# todas las dependencias, las siguientes arrancan en segundos.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY BackendJava/src ./src

# El front compilado entra en el classpath del WAR. Se borra antes para que no
# queden archivos de un build anterior (el mismo cuidado que en publicar.ps1):
# un bundle viejo con el mismo nombre de archivo produce una UI fantasma.
RUN rm -rf ./src/main/resources/static
COPY --from=front /front/dist/ ./src/main/resources/static/

RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests


# ---------- Etapa 3: runtime ----------
FROM eclipse-temurin:21-jre-alpine

# Identidad del build. Se pasan con --build-arg (docker-compose.yml los toma
# del .env y el pipeline los rellena con el tag y el commit). Los valores
# acaban en GET /api/version, que es lo que consulta el monitor.
ARG APP_VERSION=dev
ARG GIT_COMMIT=desconocido
ARG FECHA_BUILD=desconocido

# tzdata: sin el, TZ se ignora y los 'creado' quedan en UTC.
# wget: lo usa el HEALTHCHECK (viene con busybox, se declara por claridad).
RUN apk add --no-cache tzdata wget \
 && addgroup -S sih && adduser -S -G sih sih

ENV TZ=America/Mexico_City \
    APP_VERSION=$APP_VERSION \
    APP_COMMIT=$GIT_COMMIT \
    APP_CONSTRUIDO=$FECHA_BUILD
WORKDIR /app

COPY --from=back /build/target/SIH-IA-1.0.war /app/sih.war

# Los uploads de fotos de maestros viven fuera del WAR: si estuvieran dentro, se
# perderian en cada actualizacion. docker-compose.yml los monta como volumen.
RUN mkdir -p /var/lib/sih/uploads/maestros \
 && chown -R sih:sih /var/lib/sih /app

USER sih
EXPOSE 8080

# '/' es publico (el shell de React); '/api/**' exige token. Un 200 en '/' ya
# demuestra que Spring arranco, sirvio estaticos y la aplicacion esta viva.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

# MaxRAMPercentage: la JVM respeta el limite de memoria del contenedor en vez de
# calcular sobre la RAM de la maquina anfitriona (que en Oracle son 24 GB).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/sih.war"]
