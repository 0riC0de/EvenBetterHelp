FROM gradle:9.5.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle installDist --no-daemon
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home gateway && mkdir /session && chown gateway:gateway /session
WORKDIR /app
COPY --from=build /workspace/build/install/cobalt-gateway/ ./
USER gateway
EXPOSE 8090
ENTRYPOINT ["/app/bin/cobalt-gateway"]
