package com.MellianBot;

// Importations nécessaires pour les fonctionnalités de YouTube, Discord, et autres
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Deque;

import java.awt.Color;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Classe principale pour gérer les pistes audio et leurs événements
public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player; // Gestionnaire de lecture audio
    private final Deque<AudioTrack> queue; // File d'attente des pistes audio
    private TextChannel textChannel; // Canal texte pour les messages de statut
    private boolean looping = false; // Indicateur de boucle sur la piste actuelle
    private final YouTube youTubeService; // Service YouTube pour récupérer les infos de la vidéo
    private final JDA jda; // Instance JDA pour mettre à jour l'activité du bot

    // Constructeur de TrackScheduler
    public TrackScheduler(AudioPlayer player, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = player;
        this.queue = new LinkedBlockingDeque<>(); // Initialisation de la file d'attente
        this.textChannel = textChannel;
        this.youTubeService = youTubeService;
        this.jda = jda;
    }

    // --- Méthodes de configuration ---

    // Définit le canal texte pour les messages
    public void setChannel(TextChannel textChannel) {
        this.textChannel = textChannel;
    }

    // Active ou désactive le mode boucle
    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    // Retourne l'état du mode boucle
    public boolean isLooping() {
        return looping;
    }

    // --- Gestion de la file d'attente et de la lecture ---

    // Ajoute une piste à la file d'attente ou démarre la lecture si aucune piste en cours
    public void queueTrack(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        } else {
            setCurrentTrack(track);
        }
    }

    // Ajoute une piste en début de file d'attente
    public void queueTrackAtFirst(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offerFirst(track);  
        } else {
            setCurrentTrack(track);
        }
    }

    // Met à jour la piste actuelle et les informations liées
    private void setCurrentTrack(AudioTrack track) {
        updateTrackInfo(track); // Récupère les informations depuis YouTube
        updateBotActivity(track); // Change l'activité du bot pour afficher la piste
        sendNowPlayingMessage(track); // Envoie le message "En cours de lecture"
    }

    // Passe à la piste suivante
    public void skipTrack() {
        playNextTrack();
    }

    // Met la lecture en pause
    public void pauseTrack() {
        player.setPaused(true);
        sendMessage("Lecture mise en pause.");
    }

    // Reprend la lecture
    public void resumeTrack() {
        player.setPaused(false);
        sendMessage("Lecture reprise.");
    }

    // Arrête la lecture et vide la file d'attente
    public void stopTrack() {
        player.stopTrack();
        queue.clear();
        sendMessage("Lecture arrêtée et file d'attente vidée.");
        disconnectIfQueueEmpty();
        updateBotActivityToDefault();
    }

    // Vide la file d'attente sans interrompre la piste actuelle
    public void clearQueue() {
        queue.clear();
        sendMessage("File d'attente vidée.");
    }

    // Affiche les pistes en file d'attente
    public void showQueue(TextChannel channel) {
        if (queue.isEmpty()) {
            channel.sendMessage("La file d'attente est vide.").queue();
            return;
        }
    
        StringBuilder queueMessage = new StringBuilder("**File d'attente :**\n");
        int index = 1;
        for (AudioTrack track : queue) {
            TrackInfo info = (TrackInfo) track.getUserData();
            queueMessage.append(index++).append(". ")
                        .append(info != null ? info.getTitle() : track.getInfo().title)
                        .append(" - ")
                        .append(info != null ? info.getArtist() : "Inconnu")
                        .append("\n");
            
            if (index > 10) { // Limiter l'affichage à 10 pistes
                queueMessage.append("...et plus");
                break;
            }
        }
        channel.sendMessage(queueMessage.toString()).queue();
    }
    
    
    

    // Affiche la piste actuellement en cours de lecture
    public void showCurrentTrack(TextChannel channel) {
        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack == null) {
            channel.sendMessage("Aucune piste en cours de lecture.").queue();
        } else {
            TrackInfo info = (TrackInfo) currentTrack.getUserData();
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("En cours de lecture", info.getVideoUrl())
                .setDescription("**Titre :** " + info.getTitle() + "\n**Artiste/Chaine :** " + info.getArtist() + "\n**Durée :** " + info.getDuration())
                .setThumbnail(info.getThumbnailUrl())
                .setColor(Color.BLUE);
    
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }
  
        
    // Gère la fin de piste en fonction du mode boucle
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

    // Passe à la piste suivante ou met à jour l'activité du bot si la file est vide
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

    // --- Informations YouTube et des pistes ---

    // Met à jour les informations de la piste actuelle à partir de YouTube
    private void updateTrackInfo(AudioTrack track) {
        String videoId = extractYoutubeVideoId(track.getInfo().uri);
        if (videoId != null) {
            track.setUserData(getYoutubeVideoInfo(videoId));
        }
    }

    // Extrait l'identifiant de la vidéo depuis l'URL
    private String extractYoutubeVideoId(String url) {
        Matcher matcher = Pattern.compile("(?<=watch\\?v=|youtu\\.be/|youtube\\.com/embed/)[^&]+").matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    // Récupère les informations de la vidéo YouTube via l'API
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

    // Récupère la clé API YouTube
    private String getYoutubeApiKey() {
        String apiKey = System.getenv("YOUTUBE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Erreur : La clé API YouTube (YOUTUBE_API_KEY) est manquante. Assurez-vous qu'elle est définie dans les variables d'environnement.");
            return null;
        }
        return apiKey;
    }

    // Formate la durée au format minutes:secondes
    private String formatDuration(String isoDuration) {
        Duration duration = Duration.parse(isoDuration);
        return String.format("%d:%02d", duration.toMinutes(), duration.minusMinutes(duration.toMinutes()).getSeconds());
    }

    // --- Activité du bot et messages ---

    // Met à jour l'activité du bot pour refléter la piste en cours
    private void updateBotActivity(AudioTrack track) {
        String title = (track.getUserData() instanceof TrackInfo) ? ((TrackInfo) track.getUserData()).getTitle() : "Unknown Title";
        jda.getPresence().setActivity(Activity.listening(title));
    }

    // Réinitialise l'activité du bot au statut par défaut
    private void updateBotActivityToDefault() {
        jda.getPresence().setActivity(Activity.watching("comment faire avec !help"));
    }

    // Envoie un message "En cours de lecture" avec les informations de la piste actuelle
    private void sendNowPlayingMessage(AudioTrack track) {
        TrackInfo trackInfo = (track.getUserData() instanceof TrackInfo) ? (TrackInfo) track.getUserData() : TrackInfo.getDefaultInfo();
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("En cours de lecture", trackInfo.getVideoUrl())
                .setDescription("**Titre :** " + trackInfo.getTitle() + "\n**Artiste/Chaine :** " + trackInfo.getArtist() + "\n**Durée :** " + trackInfo.getDuration())
                .setThumbnail(trackInfo.getThumbnailUrl())
                .setColor(Color.GREEN);

        textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
    }


    // Envoie un message texte sur le canal texte
    private void sendMessage(String message) {
        if (textChannel != null) {
            textChannel.sendMessage(message).queue();
        }
    }

    // Déconnecte le bot si la file d'attente est vide
    private void disconnectIfQueueEmpty() {
        if (queue.isEmpty() && player.getPlayingTrack() == null && textChannel.getGuild().getAudioManager().isConnected()) {
            sendMessage("File d'attente vide. Déconnexion du canal vocal.");
            textChannel.getGuild().getAudioManager().closeAudioConnection();
        }
    }
}

// Classe pour stocker les informations des pistes
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

    // Méthode statique pour obtenir les informations par défaut
    public static TrackInfo getDefaultInfo() {
        return new TrackInfo("Unknown Title", "Unknown Duration", "Unknown Channel", "https://via.placeholder.com/150", "https://youtube.com");
    }
}
