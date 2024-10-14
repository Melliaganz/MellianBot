package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import com.MellianBot.MusicManager;

public class YouTubeSearcher {
    private final YouTube youTube;

    // Constructor accepting YouTube service
    public YouTubeSearcher(YouTube youTube) {
        this.youTube = youTube;
    }

    public String searchYoutube(String query, MusicManager musicManager, MessageChannelUnion channel) {
        // Load API keys from config.properties
        Properties properties = new Properties();
        try (InputStream input = YouTubeSearcher.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return null;
            }
            // Load properties file
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Get the YouTube API key from properties
        String youtubeApiKey = properties.getProperty("youtube.api.key");

        try {
            YouTube.Search.List request = youTube.search().list("id,snippet");
            request.setQ(query);
            request.setType("video");
            request.setFields("items(id/videoId,snippet/title)");
            request.setKey(youtubeApiKey); // Use the YouTube API key from properties
            request.setMaxResults(1L);

            SearchListResponse response = request.execute();
            List<SearchResult> results = response.getItems();

            if (results.isEmpty()) {
                channel.sendMessage("No results found.").queue();
                return null;
            }

            String videoId = results.get(0).getId().getVideoId();
            return "https://www.youtube.com/watch?v=" + videoId;

        } catch (IOException e) {
            e.printStackTrace();
            channel.sendMessage("An error occurred while searching.").queue();
            return null;
        }
    }
}