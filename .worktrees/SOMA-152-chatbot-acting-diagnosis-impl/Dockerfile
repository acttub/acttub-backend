FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system acttub && useradd --system --gid acttub --home-dir /app acttub
RUN mkdir -p /app/storage/videos && chown -R acttub:acttub /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER acttub
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
