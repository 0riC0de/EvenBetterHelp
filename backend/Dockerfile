FROM gradle:9.5.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle installDist --no-daemon
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home helpdesk
WORKDIR /app
COPY --from=build /workspace/build/install/helpdesk-service/ ./
USER helpdesk
EXPOSE 8080
ENTRYPOINT ["/app/bin/helpdesk-service"]
