package com.MellianBot;

import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
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

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private MusicManager musicManager;
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private YouTube youTubeService;
    private YouTubeSearcher youTubeSearcher;
    private String spotifyClientId;
    private String spotifyClientSecret;
    private String spotifyAccessToken;
    private static Dotenv dotenv;


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
    }

    // Point d'entrée principal de l'application
    public static void main(String[] args) throws Exception {
        dotenv = Dotenv.configure()
                .directory("src/main/resources")
                .filename(".env")
                .load();
        String discordToken = dotenv.get("DISCORD_TOKEN");
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
        this.spotifyClientId = dotenv.get("SPOTIFY_CLIENT_ID");
        this.spotifyClientSecret = dotenv.get("SPOTIFY_CLIENT_SECRET");

        System.out.println("SPOTIFY_CLIENT_ID récupéré : " + (spotifyClientId != null ? "Oui" : "Non"));
        System.out.println("SPOTIFY_CLIENT_SECRET récupéré : " + (spotifyClientSecret != null ? "Oui" : "Non"));

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
        if (spotifyClientId == null || spotifyClientSecret == null) {
            System.out.println("Client ID ou Secret Spotify est manquant.");
            return null;
        }
    
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
                int responseCode = conn.getResponseCode();
                System.out.println("Code de réponse Spotify : " + responseCode);
    
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) response.append(responseLine.trim());
                    JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                    return jsonObject.get("access_token").getAsString();
                } else {
                    System.out.println("Erreur : Code de réponse Spotify " + responseCode);
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
        String[] parts = message.split(" ", 2);
        if (parts.length < 2) {
            event.getChannel().sendMessage("Vous devez fournir un lien ou des mots-clés après la commande !play.").queue();
            return;
        }
        
        String input = parts[1];
        VoiceChannel channel = getUserVoiceChannel(event);
        if (channel == null) return;
    
        connectToVoiceChannel(channel, event);
        if (musicManager == null) initMusicManager(event);
    
        // Déterminer si l'input est un lien ou des mots-clés
        if (input.startsWith("https://open.spotify.com/")) {
            playSpotifyTrack(input, event);
        } else if (input.startsWith("http://") || input.startsWith("https://www.youtube.com") || input.startsWith("https://youtu.be")) {
            playYouTubeTrack(input, event);
        } else {
            // Si ce n'est pas un lien, considérer que ce sont des mots-clés pour rechercher une vidéo sur YouTube
            searchAndPlayYouTubeTrack(input, event);
        }
    }
    
    private void searchAndPlayYouTubeTrack(String keywords, MessageReceivedEvent event) {
        String youtubeUrl = youTubeSearcher.searchYoutube(keywords, musicManager, event.getChannel());
        if (youtubeUrl != null) {
            // Charger la vidéo trouvée dans le gestionnaire de musique
            handleYouTubeLoad(youtubeUrl, event);
        } else {
            event.getChannel().sendMessage("Aucune vidéo YouTube trouvée pour les mots-clés : " + keywords).queue();
        }
    }
    
    // Lit une piste Spotify en recherchant une vidéo YouTube correspondante
    private void playSpotifyTrack(String spotifyUrl, MessageReceivedEvent event) {
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
    private void playYouTubeTrack(String youtubeUrl, MessageReceivedEvent event) {
        handleYouTubeLoad(youtubeUrl, event);
    }

    // Obtient le canal vocal de l'utilisateur qui a envoyé la commande
    private VoiceChannel getUserVoiceChannel(MessageReceivedEvent event) {
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (voiceState == null || !(voiceState.getChannel() instanceof VoiceChannel)) return null;
        return (VoiceChannel) voiceState.getChannel();
    }

    // Connecte le bot au canal vocal spécifié
    private void connectToVoiceChannel(VoiceChannel channel, MessageReceivedEvent event) {
        if (!event.getGuild().getAudioManager().isConnected()) {
            event.getGuild().getAudioManager().openAudioConnection(channel);
            System.out.println("Bot connecté au canal vocal : " + channel.getName());
        } else {
            System.out.println("Bot déjà connecté à un canal vocal.");
        }
    }

    // Initialise le gestionnaire de musique pour le serveur Discord
    private void initMusicManager(MessageReceivedEvent event) {
        this.musicManager = new MusicManager(playerManager, event.getGuild(), event.getJDA(), event.getChannel().asTextChannel(), youTubeService);
        event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));
    }

    // Commandes pour contrôler la musique (pause, reprise, saut, arrêt, etc.)
    private void handlePauseCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().pauseTrack();
            event.getChannel().sendMessage("Lecture mise en pause.").queue();
        }
    }

    private void handleResumeCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().resumeTrack();
            event.getChannel().sendMessage("Lecture reprise.").queue();
        }
    }

    private void handleSkipCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().skipTrack();
            event.getChannel().sendMessage("Piste sautée.").queue();
        }
    }

    private void handleStopCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().stopTrack();
            event.getChannel().sendMessage("Lecture arrêtée et file d'attente vidée.").queue();
        }
    }

    private void handleClearCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().clearQueue();
            event.getChannel().sendMessage("File d'attente vidée.").queue();
        }
    }

    private void handleQueueCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().showQueue();
        }
    }

    private void handleCurrentCommand(MessageReceivedEvent event) {
        if (musicManager != null) {
            musicManager.getScheduler().showCurrentTrack(event.getChannel());
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
        String helpMessage = "Commandes disponibles :\n" +
                "!play <lien ou mots-clés> : Joue la musique demandée.\n" +
                "!current : affiche les infos de la piste en cours." +
                "!loop : Permet de jouer en boucle la track en cours." +
                "!pause : Met la lecture en pause.\n" +
                "!resume : Reprend la lecture.\n" +
                "!skip : Saute la piste en cours.\n" +
                "!first : Permet de placer une track en premier de la file d'attente." +
                "!stop : Arrête la lecture et vide la file d'attente.\n" +
                "!clear : Vide la file d'attente sans arrêter la piste en cours.\n" +
                "!queue : Affiche les pistes présentes dans la file d'attente.\n" +
                "!current : Affiche la piste en cours de lecture.\n" +
                "!help : Affiche toutes les commandes.";
        event.getChannel().sendMessage(helpMessage).queue();
    }

    // Traite le chargement des informations YouTube et ajoute les informations de la piste dans la file d'attente
    private void handleYouTubeLoad(String youtubeUrl, MessageReceivedEvent event) {
        String[] streamData = getYoutubeStreamUrlAndTitle(youtubeUrl);
        if (streamData != null) {
            String streamUrl = streamData[0];
            String title = streamData[1];
            String duration = streamData[3];
            String videoId = extractYoutubeVideoId(youtubeUrl);
            String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
            String videoUrl = youtubeUrl;
    
            // Récupère les informations de l'artiste pour la vidéo YouTube
            TrackInfo videoInfo = musicManager.getScheduler().getYoutubeVideoInfo(videoId);
            String artist = videoInfo.getArtist(); 

            TrackInfo trackInfo = new TrackInfo(title, duration, artist, thumbnailUrl, videoUrl);
            handleAudioLoadResult(streamUrl, trackInfo, event);
        } else {
            event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
        }
    }

    // Gère le chargement d'une piste audio dans le gestionnaire de musique
    private void handleAudioLoadResult(String streamUrl, TrackInfo trackInfo, MessageReceivedEvent event) {
        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(trackInfo);
                event.getChannel().sendMessage("Ajouté à la file d'attente : " + trackInfo.getTitle()).queue();
                musicManager.getScheduler().queueTrack(track);
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
            ProcessBuilder urlBuilder = new ProcessBuilder("yt-dlp", "-f", "bestaudio", "--get-url", videoUrl);
            Process urlProcess = urlBuilder.start();
            BufferedReader urlReader = new BufferedReader(new InputStreamReader(urlProcess.getInputStream()));
            String streamUrl = urlReader.readLine();
            int urlExitCode = urlProcess.waitFor();

            if (urlExitCode == 0 && streamUrl != null && !streamUrl.isEmpty()) {
                ProcessBuilder titleBuilder = new ProcessBuilder("yt-dlp", "--get-title", "--get-duration", "--get-thumbnail", videoUrl);
                Process titleProcess = titleBuilder.start();
                BufferedReader titleReader = new BufferedReader(new InputStreamReader(titleProcess.getInputStream()));

                String title = titleReader.readLine();
                String thumbnailUrl = titleReader.readLine();
                String duration = titleReader.readLine();
                int titleExitCode = titleProcess.waitFor();

                if (titleExitCode == 0 && title != null && !title.isEmpty()) {
                    return new String[]{streamUrl, title, thumbnailUrl, duration};
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Extrait l'ID de la vidéo depuis une URL YouTube
    public String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu.be/|youtube.com/embed/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) return matcher.group();
        return null;
    }
}
