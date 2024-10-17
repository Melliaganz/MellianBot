package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.VideoListResponse;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

public class YouTubeSearcher {
    private final YouTube youTube;

    // Constructor accepting YouTube service
    public YouTubeSearcher(YouTube youTube) {
        this.youTube = youTube;
    }

    // Méthode pour rechercher une vidéo via une requête (ex: un titre de chanson)
    public String searchYoutube(String query, MusicManager musicManager, MessageChannelUnion channel) {
        String youtubeApiKey = getYoutubeApiKey();
        if (youtubeApiKey == null) return null;

        try {
            // Création de la requête pour rechercher des vidéos
            YouTube.Search.List request = youTube.search().list("id,snippet");
            request.setQ(query);
            request.setType("video");
            request.setFields("items(id/videoId,snippet/title)");
            request.setKey(youtubeApiKey);  // Utiliser la clé YouTube API
            request.setMaxResults(1L);  // Limite à 1 résultat

            SearchListResponse response = request.execute();
            List<SearchResult> results = response.getItems();

            if (results.isEmpty()) {
                channel.sendMessage("Aucun résultat trouvé.").queue();
                return null;
            }

            String videoId = results.get(0).getId().getVideoId();
            return "https://www.youtube.com/watch?v=" + videoId;

        } catch (IOException e) {
            e.printStackTrace();
            channel.sendMessage("Une erreur est survenue lors de la recherche.").queue();
            return null;
        }
    }

    // Méthode pour obtenir le titre d'une vidéo via son ID
    public String getTitleByVideoId(String videoId) {
        String youtubeApiKey = getYoutubeApiKey();
        if (youtubeApiKey == null) return null;

        try {
            // Création de la requête pour obtenir des informations sur la vidéo via son ID
            YouTube.Videos.List request = youTube.videos().list("snippet");
            request.setId(videoId);  // Ajout de l'ID de la vidéo pour filtrer la requête
            request.setKey(youtubeApiKey);  // Utilisation de la clé YouTube API

            // Exécuter la requête
            VideoListResponse response = request.execute();
            if (!response.getItems().isEmpty()) {
                // Récupérer et retourner le titre de la vidéo
                return response.getItems().get(0).getSnippet().getTitle();
            } else {
                System.out.println("Aucune vidéo trouvée pour l'ID donné.");
                return null;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    // Méthode pour obtenir la clé API YouTube à partir du fichier de configuration
    private String getYoutubeApiKey() {
        Properties properties = new Properties();
        try (InputStream input = YouTubeSearcher.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return null;
            }
            properties.load(input);
            return properties.getProperty("youtube.api.key");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
