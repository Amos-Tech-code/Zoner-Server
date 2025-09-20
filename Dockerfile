# Build stage
FROM gradle:8.4-jdk17 AS build

# Set working directory and copy files with correct permissions
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

# Build the fat JAR
RUN gradle buildFatJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

# Install required FFmpeg/JavaCV dependencies
RUN apt-get update && apt-get install -y \
    libxcb1 \
    libxcb-shm0 \
    libxcb-xfixes0 \
    libxcb-shape0 \
    libx11-6 \
    libxext6 \
    libgl1 \
    libasound2 \
 && rm -rf /var/lib/apt/lists/*
 
# Expose port
EXPOSE 8080

# Create app directory and copy JAR
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/zoner-prototype.jar

# Entrypoint
ENTRYPOINT ["java", "-jar", "/app/zoner-prototype.jar"]
