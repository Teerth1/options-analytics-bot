# ============================================
# Stage 1: BUILD
# Uses Maven image to compile and package the app
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first (for dependency caching)
COPY pom.xml .

# Download dependencies (cached if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the JAR (skip tests - CI runs them separately)
RUN mvn clean package -DskipTests

# ============================================
# Stage 2: RUN
# Uses lightweight JRE image (smaller final size)
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the built JAR from stage 1
# Copy only the built JAR from stage 1
COPY --from=builder /app/target/*.jar app.jar


# Expose port 8080
EXPOSE 8080

# Run the application
# Railway private networking requires IPv6
ENTRYPOINT ["java", "-Djava.net.preferIPv6Addresses=true", "-jar", "app.jar"]
