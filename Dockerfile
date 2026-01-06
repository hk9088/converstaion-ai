# Stage 1: Build the application
FROM gradle:8.10-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle bootJar --no-daemon  # Use bootJar for Spring Boot executable JAR

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]