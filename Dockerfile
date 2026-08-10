# Stage 1: Build the application using Maven with Eclipse Temurin JDK 11
FROM maven:3.8.6-eclipse-temurin-11 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY mps-transformation-awscdk ./mps-transformation-awscdk

# Build the project, using multiple threads and skipping tests
RUN mvn -T 2C -q clean package -DskipTests

# Download MPS and the JetBrains Runtime into the MPS project (build/mps-bundle)
RUN cd mps-transformation-awscdk && sh gradlew prepareMps

# Remove JBR tarball and MPS/plugin zips
RUN rm -rf /app/mps-transformation-awscdk/build/download /root/.gradle

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:11-jre

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app /app

# MPS 2022.2's generator requires JetBrains Runtime 17. 
ENV MPS_HOME=/app/mps-transformation-awscdk/build/mps-bundle/mps
ENV JAVA_HOME=/app/mps-transformation-awscdk/build/mps-bundle/mps/jbr

EXPOSE 8088

CMD ["java", "-jar", "/app/target/cdk-mps-plugin.jar"]
