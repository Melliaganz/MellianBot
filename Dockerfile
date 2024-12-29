# Étape 1 : Construire l'application
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests

# Étape 2 : Créer l'image finale
FROM openjdk:17-jdk-slim
WORKDIR /app

# Installer Python3 et yt-dlp
RUN apt-get update && \
    apt-get install -y python3 wget && \
    wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp

COPY --from=build /app/target/MellianBot-1.2-jar-with-dependencies.jar /app/bot.jar
CMD ["java", "-jar", "/app/bot.jar"]
