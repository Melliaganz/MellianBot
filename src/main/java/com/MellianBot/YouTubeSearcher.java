package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.VideoListResponse;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.io.IOException;
import java.util.List;

public class YouTubeSearcher {
    private final YouTube youTube;

    public YouTubeSearcher(YouTube youTube) {
        this.youTube = youTube;
    }

    public String searchYoutube(String query, MusicManager musicManager, MessageChannelUnion channel) {
        String youtubeApiKey = getYoutubeApiKey();
        if (youtubeApiKey == null) {
            channel.sendMessage("Erreur : Clé API YouTube non définie.").queue();
            return null;
        }

        try {
            YouTube.Search.List request = youTube.search().list("id,snippet");
            request.setQ(query);
            request.setType("video");
            request.setFields("items(id/videoId,snippet/title)");
            request.setKey(youtubeApiKey);
            request.setMaxResults(1L);

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

    public String getTitleByVideoId(String videoId) {
        String youtubeApiKey = getYoutubeApiKey();
        if (youtubeApiKey == null) {
            System.out.println("Erreur : Clé API YouTube non définie.");
            return null;
        }

        try {
            YouTube.Videos.List request = youTube.videos().list("snippet");
            request.setId(videoId);
            request.setKey(youtubeApiKey);

            VideoListResponse response = request.execute();
            if (!response.getItems().isEmpty()) {
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


    private String getYoutubeApiKey() {
        String apiKey = System.getenv("YOUTUBE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Erreur : La clé API YouTube (YOUTUBE_API_KEY) est manquante. Assurez-vous qu'elle est définie dans les variables d'environnement.");
        }
        return apiKey;
    }

}