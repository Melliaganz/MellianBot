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

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private TextChannel textChannel;
    private AudioTrack currentTrack;
    private boolean looping = false;
    private YouTube youTubeService;
    private JDA jda;

    public TrackScheduler(AudioPlayer player, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = player;
        this.textChannel = textChannel;
        this.youTubeService = youTubeService;
        this.queue = new LinkedBlockingQueue<>();
        this.jda = jda;
    }

    public void setChannel(TextChannel textChannel) {
        this.textChannel = textChannel;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        } else {
            currentTrack = track;
            String videoId = extractYoutubeVideoId(track.getInfo().uri);
            if (videoId != null) {
                TrackInfo videoInfo = getYoutubeVideoInfo(videoId);
                track.setUserData(videoInfo);
            }
            updateBotActivity(track);
            sendNowPlayingMessage(track);
        }
    }
    public void updateBotActivityToDefault() {
        jda.getPresence().setActivity(Activity.watching("comment faire avec !help"));
        System.out.println("Aucune piste en cours. Activité du bot réinitialisée.");
    }
    public void queueFirst(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            LinkedBlockingQueue<AudioTrack> newQueue = new LinkedBlockingQueue<>();
            newQueue.offer(track); // Ajoute la nouvelle piste au début
            newQueue.addAll(queue); // Ajoute toutes les autres pistes après
            queue.clear(); // Vide l'ancienne file d'attente
            queue.addAll(newQueue); // Remplace la file d'attente par la nouvelle
        } else {
            currentTrack = track;
            String videoId = extractYoutubeVideoId(track.getInfo().uri);
            if (videoId != null) {
                TrackInfo videoInfo = getYoutubeVideoInfo(videoId);
                track.setUserData(videoInfo);
            }
            updateBotActivity(track);
            sendNowPlayingMessage(track);
        }
    }
    
    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.startTrack(nextTrack, false);
            currentTrack = nextTrack;
            updateBotActivity(nextTrack);
            sendNowPlayingMessage(nextTrack);
        } else {
            disconnectIfQueueEmpty();
            updateBotActivityToDefault();
        }
    }
    

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (looping) {
                player.startTrack(track.makeClone(), false);
            } else {
                nextTrack();
                // Si aucune piste suivante n'est trouvée, réinitialiser l'activité
                if (queue.isEmpty()) {
                    updateBotActivityToDefault();
                }
            }
        }
    }
    

    public void skipTrack() {
        nextTrack();
    }

    public void pauseTrack() {
        player.setPaused(true);
        if (textChannel != null) {
            textChannel.sendMessage("Lecture mise en pause.").queue();
        }
    }

    public void resumeTrack() {
        player.setPaused(false);
        if (textChannel != null) {
            textChannel.sendMessage("Lecture reprise.").queue();
        }
    }

    public void stopTrack() {
        player.stopTrack();
        queue.clear();
        if (textChannel != null) {
            textChannel.sendMessage("Lecture arrêtée et file d'attente vidée.").queue();
        }
        disconnectIfQueueEmpty();
        updateBotActivityToDefault();
    }
    

    public void clearQueue() {
        queue.clear();
        if (textChannel != null) {
            textChannel.sendMessage("File d'attente vidée.").queue();
        }
    }

    public void showQueue() {
        if (queue.isEmpty()) {
            textChannel.sendMessage("La file d'attente est vide.").queue();
            return;
        }
        List<String> trackList = queue.stream().map(track -> {
            TrackInfo info = (TrackInfo) track.getUserData();
            return "- " + (info != null ? info.getTitle() : track.getInfo().title);
        }).collect(Collectors.toList());

        String queueMessage = "File d'attente :\n" + String.join("\n", trackList);
        textChannel.sendMessage(queueMessage).queue();
    }

    public void showCurrentTrack(MessageChannelUnion messageChannelUnion) {
        if (currentTrack != null) {
            sendNowPlayingMessage(currentTrack);
        } else {
            textChannel.sendMessage("Aucune piste n'est actuellement en cours de lecture.").queue();
        }
    }

    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public TrackInfo getYoutubeVideoInfo(String videoId) {
        try {
            YouTube.Videos.List request = youTubeService.videos().list("snippet,contentDetails");
            request.setId(videoId);
            request.setFields("items(snippet(title, channelTitle, thumbnails), contentDetails(duration))");
            
            // Ajouter la clé d'API ici
            String youtubeApiKey = getYoutubeApiKey();
            if (youtubeApiKey == null) {
                System.out.println("Erreur : Clé d'API YouTube non trouvée.");
                return new TrackInfo("Unknown Title", "Unknown Duration", "Unknown Channel", "https://via.placeholder.com/150", "https://youtube.com");
            }
            request.setKey(youtubeApiKey);
    
            VideoListResponse response = request.execute();
            if (response.getItems().isEmpty()) {
                return new TrackInfo("Unknown Title", "Unknown Duration", "Unknown Channel", "https://via.placeholder.com/150", "https://youtube.com");
            }
    
            Video video = response.getItems().get(0);
            VideoSnippet snippet = video.getSnippet();
            String duration = formatDuration(video.getContentDetails().getDuration());
    
            String title = snippet.getTitle();
            String channelTitle = snippet.getChannelTitle();
            String thumbnailUrl = snippet.getThumbnails().getHigh().getUrl();
    
            return new TrackInfo(title, duration, channelTitle, thumbnailUrl, "https://www.youtube.com/watch?v=" + videoId);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new TrackInfo("Unknown Title", "Unknown Duration", "Unknown Channel", "https://via.placeholder.com/150", "https://youtube.com");
    }
    
    private String getYoutubeApiKey() {
        Properties properties = new Properties();
        try (InputStream input = TrackScheduler.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Désolé, impossible de trouver config.properties");
                return null;
            }
            properties.load(input);
            return properties.getProperty("youtube.api.key");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void sendNowPlayingMessage(AudioTrack track) {
        Object userData = track.getUserData();
        String trackTitle = "Unknown Title";
        String trackAuthor = "Unknown Artist/Channel";
        String trackDuration = "Unknown Duration";
        String thumbnailUrl = "https://via.placeholder.com/150";
        String originalUrl = track.getInfo().uri;
    
        if (userData instanceof TrackInfo) {
            TrackInfo trackInfo = (TrackInfo) userData;
            trackTitle = trackInfo.getTitle();
            trackAuthor = trackInfo.getArtist();
            trackDuration = trackInfo.getDuration();
            thumbnailUrl = trackInfo.getThumbnailUrl();
            originalUrl = trackInfo.getVideoUrl();
        }
    
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("En cours de lecture", originalUrl)
                .setDescription("**Titre :** " + trackTitle + "\n**Artiste/Chaine :** " + trackAuthor + "\n**Durée :** " + trackDuration)
                .setThumbnail(thumbnailUrl)
                .setColor(Color.GREEN);
    
        if (textChannel != null) {
            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
        }
    }

    public void updateBotActivity(AudioTrack track) {
        Object userData = track.getUserData();
        String title = "Unknown Title";

        if (userData instanceof TrackInfo) {
            TrackInfo trackInfo = (TrackInfo) userData;
            title = trackInfo.getTitle();
        }

        jda.getPresence().setActivity(Activity.listening(title));
        System.out.println("Lecture en cours : " + title);
    }

    private String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu\\.be/|youtube\\.com/embed/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    private String formatDuration(String isoDuration) {
        Duration duration = Duration.parse(isoDuration);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        return String.format("%d:%02d", minutes, seconds);
    }

    private void disconnectIfQueueEmpty() {
        if (queue.isEmpty() && player.getPlayingTrack() == null) {
            if (textChannel != null) {
                textChannel.sendMessage("File d'attente vide. Déconnexion du canal vocal.").queue();
            }
            if (textChannel.getGuild().getAudioManager().isConnected()) {
                textChannel.getGuild().getAudioManager().closeAudioConnection();
            }
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

    public String getTitle() {
        return title;
    }

    public String getDuration() {
        return duration;
    }

    public String getArtist() {
        return artist;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }
}
