# Build the jar first (from the repo root):  ./gradlew :server:buildFatJar
# Then:  docker build -t fithall-server -f server/Dockerfile .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY server/build/libs/fithall-server.jar /app/fithall-server.jar
# Run as an unprivileged user.
RUN useradd --system --no-create-home fithall
USER fithall
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/fithall-server.jar"]
