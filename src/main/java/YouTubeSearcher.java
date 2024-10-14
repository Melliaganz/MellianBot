package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.io.IOException;
import java.util.List;

public class YouTubeSearcher {
    private final YouTube youTube;

    // Constructor accepting YouTube service
    public YouTubeSearcher(YouTube youTube) {
        this.youTube = youTube;
    }

    public String searchYoutube(String query, MusicManager musicManager, MessageChannelUnion channel) {
        // Implementation for searching YouTube
        try {
            YouTube.Search.List request = youTube.search().list("id,snippet");
            request.setQ(query);
            request.setType("video");
            request.setFields("items(id/videoId,snippet/title)");
            request.setKey("AIzaSyBPixun39-XmfbolaX_PfA92AFna2YRhhA"); // Use your YouTube API key
            request.setMaxResults(1L);

            SearchListResponse response = request.execute();
            List<SearchResult> results = response.getItems();

            if (results.isEmpty()) {
                channel.sendMessage("No results found.").queue();
                return null;
            }

            String videoId = results.get(0).getId().getVideoId();
            return "https://www.youtube.com/watch?v=" + videoId; // Return the video URL

        } catch (IOException e) {
            e.printStackTrace();
            channel.sendMessage("An error occurred while searching.").queue();
            return null;
        }
    }
}
