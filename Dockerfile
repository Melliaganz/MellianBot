# Étape 1 : Construire l'application
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests

# Étape 2 : Créer l'image finale
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/MellianBot-1.1-jar-with-dependencies.jar /app/bot.jar
CMD ["java", "-jar", "/app/bot.jar"]
