# ---------- Build Stage ----------
    FROM maven:3.9.9-eclipse-temurin-21 AS build

    WORKDIR /app
    
    # Copy pom and download dependencies
    COPY pom.xml .
    RUN mvn dependency:go-offline
    
    # Copy source code
    COPY src ./src
    
    # Build the jar
    RUN mvn clean package -DskipTests
    
    
    # ---------- Runtime Stage ----------
    FROM eclipse-temurin:21-jdk
    
    WORKDIR /app
    
    # Copy jar from build stage
    COPY --from=build /app/target/*.jar app.jar
    
    # Expose port
    EXPOSE 8080
    
    # Run application
    ENTRYPOINT ["java", "-jar", "app.jar"]