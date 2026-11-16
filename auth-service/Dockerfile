# ---- build stage ---------------------------------------------------------
# Compila auth-service (e le sue dipendenze di reactor: parent + identity-common)
# usando il wrapper Maven del progetto, così la versione di Maven è la stessa usata in locale.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY identity-common identity-common
COPY auth-service auth-service
# Il parent pom dichiara tutti i moduli del reactor: servono i loro pom perché Maven
# possa validare il progetto, anche se -pl auth-service li esclude dalla compilazione.
COPY config-server/pom.xml config-server/pom.xml
COPY book-service/pom.xml book-service/pom.xml
COPY member-service/pom.xml member-service/pom.xml
COPY loan-service/pom.xml loan-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

# Il checkout di sviluppo è su Windows: normalizza i fine riga (CRLF -> LF) dei file
# POSIX e rendi eseguibile lo script, così il build non dipende dal sistema operativo.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties \
    && chmod +x mvnw

# La cache di BuildKit conserva ~/.m2 tra una build e l'altra: le dipendenze
# vengono scaricate solo al primo build o quando cambiano i pom.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -pl auth-service -am -DskipTests package

# ---- runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/auth-service/target/*.jar app.jar

USER app

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
