FROM eclipse-temurin:17-jdk
RUN apt-get update && apt-get install -y ghdl
WORKDIR /app
COPY . /app
RUN ./mvnw clean package -DskipTests
ENV KAFKA_LISTENER_ENABLED=true
CMD ["java", "-jar", "target/app.jar"]
