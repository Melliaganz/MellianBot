package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Color;
import java.io.IOException;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private Guild guild;
    private final BlockingQueue<AudioTrack> queue;
    private JDA jda;
    private TextChannel textChannel;
    private AudioTrack currentTrack;
    private boolean looping = false;
    private YouTube youTubeService;


    public TrackScheduler(AudioPlayer player, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = player;
        this.guild = guild;
        this.jda = jda;
        this.textChannel = textChannel;
        this.youTubeService = youTubeService; // Passez le service YouTube pour récupérer les informations
        this.queue = new LinkedBlockingQueue<>();
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    public void setJDA(JDA jda) {
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
    
            // Extraire l'ID de la vidéo depuis l'URL
            String videoId = extractYoutubeVideoId(track.getInfo().uri);
            if (videoId != null) {
                // Récupérer les informations de la vidéo YouTube
                String[] videoInfo = getYoutubeVideoInfo(videoId);
                // Définir les informations de la vidéo comme userData en s'assurant que c'est un tableau de chaînes
                track.setUserData(videoInfo);  // Assurez-vous que videoInfo est bien de type String[]
            }
    
            updateBotActivity(track);
            sendNowPlayingMessage(track);
        }
    }
    
    

    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            currentTrack = nextTrack;
            player.startTrack(nextTrack, false);
            updateBotActivity(nextTrack);
            sendNowPlayingMessage(nextTrack);
        } else {
            if (player.getPlayingTrack() == null && guild != null) {
                guild.getAudioManager().closeAudioConnection();
                jda.getPresence().setActivity(Activity.watching("un bon gros boulard"));
            }
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (looping) {
                player.startTrack(track.makeClone(), false);
            } else {
                nextTrack();
            }
        }
    }

    public void skipTrack() {
        nextTrack();
    }

    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }
    private String[] getYoutubeVideoInfo(String videoId) {
        try {
            // Créez une demande avec "snippet" comme partie
            YouTube.Videos.List request = youTubeService.videos().list("snippet");
            
            // Utilisez Collections.singletonList pour passer la liste des IDs de vidéos
            request.setId(videoId); // Changez cela pour utiliser la seule vidéo ID
    
            request.setFields("items(snippet(title, channelTitle, thumbnails))");
            
            Video video = request.execute().getItems().get(0);
            VideoSnippet snippet = video.getSnippet();
    
            String title = snippet.getTitle();
            String channelTitle = snippet.getChannelTitle();
            String thumbnailUrl = snippet.getThumbnails().getHigh().getUrl();
    
            return new String[]{title, channelTitle, thumbnailUrl};
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String[]{"Unknown Title", "Unknown Channel", "https://via.placeholder.com/150"};
    }
    
    private void sendNowPlayingMessage(AudioTrack track) {
        // Récupération directe des informations depuis AudioTrack
        String trackTitle = track.getInfo().title != null ? track.getInfo().title : "Unknown Title";
        String trackAuthor = track.getInfo().author != null ? track.getInfo().author : "Unknown Artist/Channel";
        String videoId = extractYoutubeVideoId(track.getInfo().uri);
        String thumbnailUrl = videoId != null ? "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg" : "https://via.placeholder.com/150";
    
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("En cours de lecture", track.getInfo().uri)
                .setDescription("**Titre :** " + trackTitle + "\n**Artiste/Chaine :** " + trackAuthor)
                .setThumbnail(thumbnailUrl)
                .setColor(Color.GREEN);
    
        if (textChannel != null) {
            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
        }
    }
    
    

    private void updateBotActivity(AudioTrack track) {
        String trackTitle = (String) track.getUserData();
        if (trackTitle == null) {
            trackTitle = "Titre inconnu";
        }
        jda.getPresence().setActivity(Activity.listening(trackTitle));
    }

    private String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu.be\\/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.find() ? matcher.group() : null;
    }
}
