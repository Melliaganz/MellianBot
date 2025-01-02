package com.MellianBot;

import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private final BotMusicService botMusicService;
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private MusicManager musicManager;
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private YouTube youTubeService;
    private YouTubeSearcher youTubeSearcher;
    private String spotifyClientId;
    private String spotifyClientSecret;
    private String spotifyAccessToken;


    // Constructeur : initialise les configurations et les sources d'audio
    public Main() {
        loadProperties();
        initializeSpotify(); 
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        
        // Initialise l'API YouTube pour effectuer des recherches
        this.youTubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                .setApplicationName("MellianBot")
                .build();
        this.youTubeSearcher = new YouTubeSearcher(youTubeService);

        // Initialise BotMusicService avec le service YouTube
        this.botMusicService = new BotMusicService(youTubeService);
    }

    // Point d'entrée principal de l'application
    public static void main(String[] args) throws Exception {
        String discordToken = System.getenv("DISCORD_TOKEN");
        if (discordToken == null || discordToken.isEmpty()) {
            throw new IllegalArgumentException("Le token Discord est manquant. Assurez-vous que la variable d'environnement DISCORD_TOKEN est définie.");
        }
    
        JDABuilder builder = JDABuilder.createDefault(discordToken, 
            GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
            GatewayIntent.SCHEDULED_EVENTS);
        builder.setActivity(Activity.watching("comment faire avec !help"));
        builder.addEventListeners(new Main());
        builder.build();
    }
    


    // Charge les informations de configuration pour Spotify
    private void loadProperties() {
        this.spotifyClientId = System.getenv("SPOTIFY_CLIENT_ID");
        this.spotifyClientSecret = System.getenv("SPOTIFY_CLIENT_SECRET");

        if (spotifyClientId == null || spotifyClientId.isEmpty() || spotifyClientSecret == null || spotifyClientSecret.isEmpty()) {
            throw new IllegalArgumentException("Les identifiants Spotify (SPOTIFY_CLIENT_ID ou SPOTIFY_CLIENT_SECRET) sont manquants. Assurez-vous que les variables d'environnement sont définies.");
        }

        this.spotifyAccessToken = getSpotifyAccessToken();
    }
    

    // Initialise le gestionnaire de source pour Spotify
    private void initializeSpotify() {
        if (spotifyAccessToken != null) {
            SpotifySourceManager spotifySourceManager = new SpotifySourceManager(
                new String[]{"user-read-email", "playlist-read-private", "streaming"},
                spotifyClientId, spotifyClientSecret, spotifyAccessToken, playerManager
            );
            playerManager.registerSourceManager((AudioSourceManager) spotifySourceManager);
        } else {
            System.out.println("Impossible d'obtenir un token d'accès Spotify.");
        }
    }

    // Récupère un token d'accès Spotify en effectuant une requête d'authentification
    public String getSpotifyAccessToken() {
        String auth = spotifyClientId + ":" + spotifyClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        for (int retryCount = 0; retryCount < 3; retryCount++) {
            try {
                URL url = new URL("https://accounts.spotify.com/api/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("grant_type=client_credentials".getBytes("utf-8"));
                }
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) response.append(responseLine.trim());
                    JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                    return jsonObject.get("access_token").getAsString();
                } else {
                    System.out.println("Erreur : Code de réponse Spotify " + conn.getResponseCode());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Retrying to fetch Spotify access token... (Attempt " + (retryCount + 1) + "/3)");
        }
        System.out.println("Échec de l'obtention du token d'accès Spotify après plusieurs tentatives.");
        return null;
    }
    

    // Gère les commandes reçues dans les messages Discord
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (message.startsWith("!play")) handlePlayCommand(message, event);
        else if (message.startsWith("!pause")) handlePauseCommand(event);
        else if (message.startsWith("!resume")) handleResumeCommand(event);
        else if (message.startsWith("!skip")) handleSkipCommand(event);
        else if (message.startsWith("!stop")) handleStopCommand(event);
        else if (message.startsWith("!clear")) handleClearCommand(event);
        else if (message.startsWith("!queue")) handleQueueCommand(event);
        else if (message.startsWith("!current")) handleCurrentCommand(event);
        else if (message.startsWith("!loop")) handleLoopCommand(event);
        else if (message.startsWith("!help")) handleHelpCommand(event);
    }

    // Gère la commande !play pour lire une piste ou une vidéo
   private void handlePlayCommand(String message, MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();

        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
        String[] parts = message.split(" ", 2);

        if (parts.length < 2) {
            event.getChannel().sendMessage("Vous devez fournir un lien ou des mots-clés après la commande !play.").queue();
            return;
        }

        String input = parts[1];
        VoiceChannel channel = getUserVoiceChannel(event);
        if (channel == null) return;

        connectToVoiceChannel(channel, event, musicManager);
        
        if (input.startsWith("https://open.spotify.com/")) {
            playSpotifyTrack(input, event, musicManager);
        } else if (input.startsWith("http://") || input.startsWith("https://www.youtube.com") || input.startsWith("https://youtu.be")) {
            playYouTubeTrack(input, event, musicManager);
        } else {
            searchAndPlayYouTubeTrack(input, event, musicManager);
        }
    }
    
    private void searchAndPlayYouTubeTrack(String keywords, MessageReceivedEvent event, MusicManager musicManager) {
        String youtubeUrl = youTubeSearcher.searchYoutube(keywords, musicManager, event.getChannel());
        if (youtubeUrl != null) {
            // Charger la vidéo trouvée dans le gestionnaire de musique
            handleYouTubeLoad(youtubeUrl, event);
        } else {
            event.getChannel().sendMessage("Aucune vidéo YouTube trouvée pour les mots-clés : " + keywords).queue();
        }
    }
    
    // Lit une piste Spotify en recherchant une vidéo YouTube correspondante
    private void playSpotifyTrack(String spotifyUrl, MessageReceivedEvent event, MusicManager musicManager) {
        String spotifyTrackTitle = getTrackTitleFromSpotify(spotifyUrl);
        if (spotifyTrackTitle == null) {
            event.getChannel().sendMessage("Impossible de récupérer les informations de la piste Spotify.").queue();
            return;
        }
        String youtubeUrl = youTubeSearcher.searchYoutube(spotifyTrackTitle, musicManager, event.getChannel());
        if (youtubeUrl != null) {
            handleYouTubeLoad(youtubeUrl, event);
        } else {
            event.getChannel().sendMessage("Aucun résultat YouTube trouvé pour cette piste Spotify.").queue();
        }
    }

    // Lit une vidéo YouTube en fonction de l'URL fournie
    private void playYouTubeTrack(String youtubeUrl, MessageReceivedEvent event, MusicManager musicManager) {
        if (youtubeUrl.contains("list=")) {
            handleYouTubePlaylist(youtubeUrl, event, musicManager); // Charge uniquement la première vidéo
        } else {
            handleYouTubeLoad(youtubeUrl, event); // Charge une vidéo unique
        }
    }
    private void handleYouTubePlaylist(String playlistUrl, MessageReceivedEvent event, MusicManager musicManager) {
        try {
            // Commande pour extraire tous les IDs des vidéos de la playlist
            ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp", "--flat-playlist", "--get-id", "--no-check-certificate", playlistUrl
            );
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    
            String videoId;
            while ((videoId = reader.readLine()) != null) {
                String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                handleYouTubeLoad(videoUrl, event);
            }
    
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                event.getChannel().sendMessage("Erreur lors du chargement de la playlist.").queue();
            } else {
                event.getChannel().sendMessage("Playlist chargée avec succès !").queue();
            }
        } catch (IOException | InterruptedException e) {
            event.getChannel().sendMessage("Erreur pendant le chargement de la playlist : " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }
    
    // Obtient le canal vocal de l'utilisateur qui a envoyé la commande
    private VoiceChannel getUserVoiceChannel(MessageReceivedEvent event) {
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (voiceState == null || !(voiceState.getChannel() instanceof VoiceChannel)) return null;
        return (VoiceChannel) voiceState.getChannel();
    }

    // Connecte le bot au canal vocal spécifié
    private void connectToVoiceChannel(VoiceChannel channel, MessageReceivedEvent event, MusicManager musicManager) {
        if (!event.getGuild().getAudioManager().isConnected()) {
            event.getGuild().getAudioManager().openAudioConnection(channel);
            System.out.println("Bot connecté au canal vocal : " + channel.getName());
            event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));
        } else {
            System.out.println("Bot déjà connecté à un canal vocal.");
        }
    }

    // Commandes pour contrôler la musique (pause, reprise, saut, arrêt, etc.)
    private void handlePauseCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        musicManager.getScheduler().pauseTrack();
        event.getChannel().sendMessage("Lecture mise en pause.").queue();
    }
    

    private void handleResumeCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        musicManager.getScheduler().resumeTrack();
        event.getChannel().sendMessage("Lecture reprise.").queue();
    }
    
    private void handleSkipCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        musicManager.getScheduler().skipTrack();
        event.getChannel().sendMessage("Piste sautée.").queue();
    }
    
    private void handleStopCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        musicManager.getScheduler().stopTrack();
        event.getChannel().sendMessage("Lecture arrêtée et file d'attente vidée.").queue();
    }
    

    private void handleClearCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        musicManager.getScheduler().clearQueue();
        event.getChannel().sendMessage("File d'attente vidée.").queue();
    }
    

    private void handleQueueCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("Cette commande n'est disponible que dans les serveurs.").queue();
            return;
        }
        
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
        
        if (musicManager != null) {
            musicManager.getScheduler().showQueue(textChannel);
        } else {
            textChannel.sendMessage("Aucune file d'attente trouvée pour ce serveur.").queue();
        }
    }
    
    
    private void handleCurrentCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel(); // Conversion explicite en TextChannel
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        if (musicManager != null) {
            musicManager.getScheduler().showCurrentTrack(textChannel);
        } else {
            textChannel.sendMessage("Aucune piste en cours de lecture.").queue();
        }
    }
    
    

    private void handleLoopCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            boolean currentLooping = musicManager.getScheduler().isLooping();
            musicManager.getScheduler().setLooping(!currentLooping);
            event.getChannel().sendMessage("Lecture en boucle " + (!currentLooping ? "activée" : "désactivée") + ".").queue();
        }
    }

    private void handleHelpCommand(MessageReceivedEvent event) {
        String helpMessage = "```md\n" +
                "# Commandes disponibles :\n" +
                "### Musique :\n" +
                "- [!play <lien ou mots-clés>] : Joue la musique demandée.\n" +
                "- [!pause] : Met la lecture en pause.\n" +
                "- [!resume] : Reprend la lecture.\n" +
                "- [!skip] : Saute la piste en cours.\n" +
                "- [!stop] : Arrête la lecture et vide la file d'attente.\n" +
                "- [!clear] : Vide la file d'attente sans arrêter la piste en cours.\n" +
                "- [!queue] : Affiche les pistes présentes dans la file d'attente.\n" +
                "- [!current] : Affiche la piste en cours de lecture.\n" +
                "- [!loop] : Active ou désactive la lecture en boucle de la piste en cours.\n\n" +
                "### Général :\n" +
                "- [!help] : Affiche toutes les commandes.\n" +
                "```\n";
    
        event.getChannel().sendMessage(helpMessage).queue();
    }
    

    // Traite le chargement des informations YouTube et ajoute les informations de la piste dans la file d'attente
    private void handleYouTubeLoad(String youtubeUrl, MessageReceivedEvent event) {
        String[] streamData = getYoutubeStreamUrlAndTitle(youtubeUrl);
    
        if (streamData == null || streamData.length < 1 || streamData[0] == null) {
            event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
            return;
        }
    
        String streamUrl = streamData[0];
        String videoId = extractYoutubeVideoId(youtubeUrl);
        TrackInfo videoInfo = getYoutubeVideoInfo(videoId);
    
        if (videoInfo == null) {
            videoInfo = new TrackInfo("Titre inconnu", "Durée inconnue", "Artiste inconnu", "https://via.placeholder.com/150", youtubeUrl);
        }
    
        displayTrackInfo(videoInfo, event); // Display detailed track information
        handleAudioLoadResult(streamUrl, videoInfo, event);
    }
    private void displayTrackInfo(TrackInfo trackInfo, MessageReceivedEvent event) {
        String message = "**🎵 Now Playing:**\n" +
                         "**Titre:** " + trackInfo.getTitle() + "\n" +
                         "**Artiste:** " + trackInfo.getArtist() + "\n" +
                         "**Durée:** " + trackInfo.getDuration() + "\n";
        event.getChannel().sendMessage(message).queue();
    }
    private void loadTrackFromStreamUrl(String streamUrl, MessageReceivedEvent event) {
        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                MusicManager musicManager = botMusicService.getMusicManager(event.getGuild(), event.getChannel().asTextChannel(), event.getJDA());
                musicManager.getScheduler().queueTrack(track);
                event.getChannel().sendMessage("Ajouté à la file d'attente : **" + track.getInfo().title + "**").queue();
            }
    
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                event.getChannel().sendMessage("Erreur : Une playlist n'est pas attendue ici.").queue();
            }
    
            @Override
            public void noMatches() {
                event.getChannel().sendMessage("Aucune piste trouvée pour le lien donné.").queue();
            }
    
            @Override
            public void loadFailed(FriendlyException exception) {
                event.getChannel().sendMessage("Erreur lors du chargement de la piste : " + exception.getMessage()).queue();
            }
        });
    }
    // Gère le chargement d'une piste audio dans le gestionnaire de musique
    private void handleAudioLoadResult(String streamUrl, TrackInfo trackInfo, MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();
        JDA jda = event.getJDA();
        MusicManager musicManager = botMusicService.getMusicManager(guild, textChannel, jda);
    
        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(trackInfo);
                musicManager.getScheduler().queueTrack(track);
                event.getChannel().sendMessage("🎶 Ajouté à la file d'attente : **" + trackInfo.getTitle() + "**").queue();
            }
    
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                event.getChannel().sendMessage("Playlist détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                for (AudioTrack track : playlist.getTracks()) {
                    musicManager.getScheduler().queueTrack(track);
                }
            }
    
            @Override
            public void noMatches() {
                event.getChannel().sendMessage("Aucune piste trouvée.").queue();
            }
    
            @Override
            public void loadFailed(FriendlyException exception) {
                event.getChannel().sendMessage("Erreur lors du chargement de la piste : " + exception.getMessage()).queue();
            }
        });
    }
    // Récupère le titre de la piste depuis un lien Spotify
    private String getTrackTitleFromSpotify(String spotifyUrl) {
        try {
            String trackId = spotifyUrl.split("track/")[1].split("\\?")[0];
            URL url = new URL("https://api.spotify.com/v1/tracks/" + trackId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + spotifyAccessToken);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) response.append(responseLine.trim());
                JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                String title = jsonObject.get("name").getAsString();
                String artist = jsonObject.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                return title + " " + artist;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Utilise yt-dlp pour obtenir le flux audio, le titre, la durée, et la miniature de la vidéo YouTube
    private String[] getYoutubeStreamUrlAndTitle(String videoUrl) {
        try {
            // Use --playlist-items 1 to fetch only the first video
            ProcessBuilder urlBuilder = new ProcessBuilder(
                "yt-dlp", "-f", "bestaudio[ext=webm][acodec=opus]", "--get-url", "--playlist-items", "1", "--no-check-certificate", "--geo-bypass", videoUrl
            );
    
            urlBuilder.redirectErrorStream(true); // Redirect errors to the input stream
            Process process = urlBuilder.start();
    
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String streamUrl = reader.readLine(); // Read the first audio stream URL
    
            int exitCode = process.waitFor();
            if (exitCode != 0 || streamUrl == null) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
                System.err.println("Error with yt-dlp: " + errorOutput);
                return null;
            }
    
            // Optional: Get metadata via YouTube API
            String videoId = extractYoutubeVideoId(videoUrl);
            TrackInfo videoInfo = getYoutubeVideoInfo(videoId);
            return new String[]{streamUrl, videoInfo.getTitle(), videoInfo.getThumbnailUrl(), videoInfo.getDuration()};
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }
    
// Méthode pour extraire l'ID de la vidéo
private String extractYoutubeVideoId(String url) {
    Matcher matcher = Pattern.compile("(?<=watch\\?v=|youtu\\.be/|youtube\\.com/embed/)[^&]+").matcher(url);
    return matcher.find() ? matcher.group() : null;
}

// Méthode pour récupérer les informations de la vidéo via l'API YouTube
private TrackInfo getYoutubeVideoInfo(String videoId) {
    try {
        YouTube.Videos.List request = youTubeService.videos().list("snippet,contentDetails");
        request.setId(videoId);
        request.setKey(System.getenv("YOUTUBE_API_KEY"));
        request.setFields("items(snippet(title, channelTitle, thumbnails), contentDetails(duration))");

        VideoListResponse response = request.execute();
        if (response.getItems().isEmpty()) {
            return TrackInfo.getDefaultInfo();
        }

        Video video = response.getItems().get(0);
        String title = video.getSnippet().getTitle();
        String artist = video.getSnippet().getChannelTitle();
        String thumbnailUrl = video.getSnippet().getThumbnails().getHigh().getUrl();
        String duration = formatDuration(video.getContentDetails().getDuration());

        return new TrackInfo(title, duration, artist, thumbnailUrl, "https://www.youtube.com/watch?v=" + videoId);
    } catch (IOException e) {
        e.printStackTrace();
        return TrackInfo.getDefaultInfo();
    }
}


// Formater la durée ISO 8601 en minutes:secondes
private String formatDuration(String isoDuration) {
    Duration duration = Duration.parse(isoDuration);
    return String.format("%d:%02d", duration.toMinutes(), duration.minusMinutes(duration.toMinutes()).getSeconds());
}

}