FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system openpulsechecker && adduser --system --ingroup openpulsechecker openpulsechecker
COPY --from=builder /build/target/open-pulse-checker-0.1.0-SNAPSHOT.jar /app/app.jar
USER openpulsechecker
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
