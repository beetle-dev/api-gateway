FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx400m", "-Xms256m", "-Dspring.profiles.active=prd", "-jar", "app.jar"]