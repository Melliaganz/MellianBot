package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Deque;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    
    private final Deque<AudioTrack> queue;  // Changement vers Deque
    private TextChannel textChannel;
    private AudioTrack currentTrack;
    private boolean looping = false;
    private final YouTube youTubeService;
    private final JDA jda;

    public TrackScheduler(AudioPlayer player, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = player;
        this.queue = new LinkedBlockingDeque<>();  // Utilisation de LinkedBlockingDeque
        this.textChannel = textChannel;
        this.youTubeService = youTubeService;
        this.jda = jda;
    }

    // Setters and Configuration Methods
    public void setChannel(TextChannel textChannel) {
        this.textChannel = textChannel;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    // Track Queueing and Playback Control
    public void queueTrack(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        } else {
            setCurrentTrack(track);
        }
    }

    public void queueTrackAtFirst(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offerFirst(track);  
        } else {
            setCurrentTrack(track);
        }
    }

    private void setCurrentTrack(AudioTrack track) {
        currentTrack = track;
        updateTrackInfo(track);
        updateBotActivity(track);
        sendNowPlayingMessage(track);
    }

    public void skipTrack() {
        playNextTrack();
    }

    public void pauseTrack() {
        player.setPaused(true);
        sendMessage("Lecture mise en pause.");
    }

    public void resumeTrack() {
        player.setPaused(false);
        sendMessage("Lecture reprise.");
    }

    public void stopTrack() {
        player.stopTrack();
        queue.clear();
        sendMessage("Lecture arrêtée et file d'attente vidée.");
        disconnectIfQueueEmpty();
        updateBotActivityToDefault();
    }

    public void clearQueue() {
        queue.clear();
        sendMessage("File d'attente vidée.");
    }

    public void showQueue() {
        if (queue.isEmpty()) {
            sendMessage("La file d'attente est vide.");
        } else {
            String queueMessage = "File d'attente :\n" + queue.stream()
                .map(track -> "- " + getTrackTitle(track))
                .collect(Collectors.joining("\n"));
            sendMessage(queueMessage);
        }
    }

    public void showCurrentTrack(MessageChannelUnion messageChannelUnion) {
        if (currentTrack != null) {
            sendNowPlayingMessage(currentTrack);
        } else {
            sendMessage("Aucune piste n'est actuellement en cours de lecture.");
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (looping) {
                player.startTrack(track.makeClone(), false);
            } else {
                playNextTrack();
            }
        }
    }

    private void playNextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.startTrack(nextTrack, false);
            setCurrentTrack(nextTrack);
        } else {
            disconnectIfQueueEmpty();
            updateBotActivityToDefault();
        }
    }

    // YouTube and Track Information
    private void updateTrackInfo(AudioTrack track) {
        String videoId = extractYoutubeVideoId(track.getInfo().uri);
        if (videoId != null) {
            track.setUserData(getYoutubeVideoInfo(videoId));
        }
    }

    private String extractYoutubeVideoId(String url) {
        Matcher matcher = Pattern.compile("(?<=watch\\?v=|youtu\\.be/|youtube\\.com/embed/)[^&]+").matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    TrackInfo getYoutubeVideoInfo(String videoId) {
        try {
            YouTube.Videos.List request = youTubeService.videos().list("snippet,contentDetails")
                    .setId(videoId)
                    .setFields("items(snippet(title, channelTitle, thumbnails), contentDetails(duration))")
                    .setKey(getYoutubeApiKey());

            VideoListResponse response = request.execute();
            if (response.getItems().isEmpty()) return TrackInfo.getDefaultInfo();

            Video video = response.getItems().get(0);
            VideoSnippet snippet = video.getSnippet();
            String duration = formatDuration(video.getContentDetails().getDuration());

            return new TrackInfo(snippet.getTitle(), duration, snippet.getChannelTitle(),
                    snippet.getThumbnails().getHigh().getUrl(), "https://www.youtube.com/watch?v=" + videoId);
        } catch (IOException e) {
            e.printStackTrace();
            return TrackInfo.getDefaultInfo();
        }
    }

    private String getYoutubeApiKey() {
        try (InputStream input = TrackScheduler.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) return null;
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("youtube.api.key");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private String formatDuration(String isoDuration) {
        Duration duration = Duration.parse(isoDuration);
        return String.format("%d:%02d", duration.toMinutes(), duration.minusMinutes(duration.toMinutes()).getSeconds());
    }

    // Bot Activity and Messaging
    private void updateBotActivity(AudioTrack track) {
        String title = (track.getUserData() instanceof TrackInfo) ? ((TrackInfo) track.getUserData()).getTitle() : "Unknown Title";
        jda.getPresence().setActivity(Activity.listening(title));
    }

    private void updateBotActivityToDefault() {
        jda.getPresence().setActivity(Activity.watching("comment faire avec !help"));
    }

    private void sendNowPlayingMessage(AudioTrack track) {
        TrackInfo trackInfo = (track.getUserData() instanceof TrackInfo) ? (TrackInfo) track.getUserData() : TrackInfo.getDefaultInfo();
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("En cours de lecture", trackInfo.getVideoUrl())
                .setDescription("**Titre :** " + trackInfo.getTitle() + "\n**Artiste/Chaine :** " + trackInfo.getArtist() + "\n**Durée :** " + trackInfo.getDuration())
                .setThumbnail(trackInfo.getThumbnailUrl())
                .setColor(Color.GREEN);

        textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
    }

    private String getTrackTitle(AudioTrack track) {
        TrackInfo info = (TrackInfo) track.getUserData();
        return info != null ? info.getTitle() : track.getInfo().title;
    }

    private void sendMessage(String message) {
        if (textChannel != null) {
            textChannel.sendMessage(message).queue();
        }
    }

    private void disconnectIfQueueEmpty() {
        if (queue.isEmpty() && player.getPlayingTrack() == null && textChannel.getGuild().getAudioManager().isConnected()) {
            sendMessage("File d'attente vide. Déconnexion du canal vocal.");
            textChannel.getGuild().getAudioManager().closeAudioConnection();
        }
    }
}

class TrackInfo {
    private final String title;
    private final String duration;
    private final String artist;
    private final String thumbnailUrl;
    private final String videoUrl;

    public TrackInfo(String title, String duration, String artist, String thumbnailUrl, String videoUrl) {
        this.title = title;
        this.duration = duration;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.videoUrl = videoUrl;
    }

    public String getTitle() { return title; }
    public String getDuration() { return duration; }
    public String getArtist() { return artist; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getVideoUrl() { return videoUrl; }

    public static TrackInfo getDefaultInfo() {
        return new TrackInfo("Unknown Title", "Unknown Duration", "Unknown Channel", "https://via.placeholder.com/150", "https://youtube.com");
    }
}
