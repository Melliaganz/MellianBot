# Utiliser une image Java avec Maven pour la construction
FROM maven:3.8-openjdk-17 AS build

# Définir le répertoire de travail
WORKDIR /app

# Copier les fichiers source
COPY . .

# Construire le projet avec Maven
RUN mvn clean package

# Utiliser une image Java pour exécuter le bot
FROM openjdk:17-jdk-slim

# Copier le fichier JAR construit dans l'image
COPY --from=build /app/target/MellianBot-1.0-jar-with-dependencies.jar /app/bot.jar

# Lancer le bot
CMD ["java", "-jar", "/app/bot.jar"]
