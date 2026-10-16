# Single parametrised image for the four applications.
#   docker build --build-arg MODULE=wm-central --build-arg APP_NAME=WM_Central -t wm-central .

# ---------- build ----------
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
# POMs first: the dependency layer is cached and only rebuilt when a POM changes
COPY pom.xml .
COPY wm-common/pom.xml         wm-common/
COPY wm-central/pom.xml        wm-central/
COPY wm-ws-engine/pom.xml      wm-ws-engine/
COPY wm-ws-monitor/pom.xml     wm-ws-monitor/
COPY wm-field-operator/pom.xml wm-field-operator/
ARG MODULE
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline -DexcludeGroupIds=es.ua.sd.wm
COPY . .
RUN mvn -B -pl ${MODULE} -am package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
ARG MODULE
ARG APP_NAME
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${APP_NAME}.jar /app/${APP_NAME}.jar
ENV APP_NAME=${APP_NAME}
# exec: java becomes PID 1 and receives SIGTERM on "docker stop"
ENTRYPOINT ["sh", "-c", "exec java -jar /app/${APP_NAME}.jar \"$@\"", "--"]
