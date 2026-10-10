# Ktor backend
# Build context: the REPOSITORY ROOT (the server needs the Gradle wrapper and root build files)
#   docker build -f server/Dockerfile -t smart-finance-server .
# On Railway: leave Root Directory empty and set the service variable
#   RAILWAY_DOCKERFILE_PATH=server/Dockerfile

# Base images are pinned by digest so rebuilds use exactly the image we tested.
# The tag is kept for readability; the digest is what Docker actually uses.
# To update: pull the new tag, copy its digest from the build log, test, commit.

# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk@sha256:5d6042fb8cdc14d614e4e421f52e3211fc5aeadddce44cbf9de8ed37c791f824 AS build
WORKDIR /app

# Gradle wrapper and build config first, so the Gradle download is cached
# between builds as long as these files don't change.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# The project also includes Android / KMP modules, which would need an Android SDK
# just to configure. Rewrite settings so this image only builds :server.
# (Only affects the copy inside the image, not the repo.)
RUN sed -i '/^include(/d' settings.gradle.kts \
 && echo 'include(":server")' >> settings.gradle.kts \
 && sed -i 's/\r$//' gradlew \
 && chmod +x gradlew \
 && ./gradlew --version --no-daemon

COPY server ./server

# Smaller heap than gradle.properties asks for (4 GB), so the build fits in
# typical build machines; run the Kotlin compiler in-process instead of a 3 GB daemon.
RUN ./gradlew :server:installDist --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" \
      -Pkotlin.compiler.execution.strategy=in-process

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre@sha256:207ecae0b2b104dfc6dfa763d1e9cd2041cb344800a180e24cb1a28ed533ff90
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /app/server/build/install/server ./

USER app

# Let the JVM size its heap from the container's memory limit,
# and exit (so Railway restarts it) instead of limping on after an OOM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Ktor reads PORT (Railway sets it); 8080 is the fallback.
EXPOSE 8080
CMD ["./bin/server"]