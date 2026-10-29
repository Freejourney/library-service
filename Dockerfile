# 多模块构建：ARG SERVICE ∈ {account, catalog, lending}
# docker build --build-arg SERVICE=lending -t elibrary-lending .
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

ARG SERVICE=account
ENV SERVICE=${SERVICE}

COPY settings.gradle build.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY common/build.gradle ./common/build.gradle
COPY account/build.gradle ./account/build.gradle
COPY catalog/build.gradle ./catalog/build.gradle
COPY lending/build.gradle ./lending/build.gradle
RUN gradle --no-daemon --console=plain dependencies > /dev/null 2>&1 || true

COPY common ./common
COPY account ./account
COPY catalog ./catalog
COPY lending ./lending

RUN gradle --no-daemon --console=plain :${SERVICE}:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG SERVICE=account
ENV SERVICE=${SERVICE}
COPY --from=build /workspace/${SERVICE}/build/libs/${SERVICE}-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar"]
