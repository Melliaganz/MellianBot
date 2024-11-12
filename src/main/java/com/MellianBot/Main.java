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
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private final AudioPlayerManager playerManager;
    private MusicManager musicManager;
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private YouTube youTubeService;
    private YouTubeSearcher youTubeSearcher;
    private String spotifyClientId;
    private String spotifyClientSecret;
    private String spotifyAccessToken;

    public Main() {
        this.playerManager = new DefaultAudioPlayerManager();
        Properties properties = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Désolé, impossible de trouver config.properties. Utilisation des valeurs par défaut.");
                setDefaultProperties(properties);
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.spotifyClientId = properties.getProperty("spotify.client.id", "defaultClientId");
        this.spotifyClientSecret = properties.getProperty("spotify.client.secret", "defaultClientSecret");
        this.spotifyAccessToken = getSpotifyAccessToken();
        if (spotifyAccessToken != null) {
            SpotifySourceManager spotifySourceManager = new SpotifySourceManager(
                    new String[]{"user-read-email", "playlist-read-private", "streaming"},
                    spotifyClientId,
                    spotifyClientSecret,
                    spotifyAccessToken,
                    playerManager
            );
            playerManager.registerSourceManager((AudioSourceManager) spotifySourceManager);
        } else {
            System.out.println("Impossible d'obtenir un token d'accès Spotify.");
        }
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        this.youTubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                .setApplicationName("MellianBot")
                .build();
        this.youTubeSearcher = new YouTubeSearcher(youTubeService);
    }

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Désolé, impossible de trouver config.properties. Utilisation des valeurs par défaut.");
                setDefaultProperties(properties);
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String discordToken = properties.getProperty("discord.api.key", "defaultDiscordToken");
        JDABuilder builder = JDABuilder.createDefault(discordToken,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                GatewayIntent.SCHEDULED_EVENTS);
        builder.setActivity(Activity.watching("comment faire avec !help"));
        builder.addEventListeners(new Main());
        builder.build();
    }

    private static void setDefaultProperties(Properties properties) {
        properties.setProperty("spotify.client.id", "defaultClientId");
        properties.setProperty("spotify.client.secret", "defaultClientSecret");
        properties.setProperty("discord.api.key", "defaultDiscordToken");
    }

    public String getSpotifyAccessToken() {
        String auth = spotifyClientId + ":" + spotifyClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                URL url = new URL("https://accounts.spotify.com/api/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                String urlParameters = "grant_type=client_credentials";
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = urlParameters.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                    return jsonObject.get("access_token").getAsString();
                } else {
                    System.out.println("Erreur : Code de réponse Spotify " + conn.getResponseCode());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            retryCount++;
            System.out.println("Retrying to fetch Spotify access token... (Attempt " + (retryCount + 1) + "/3)");
        }
        System.out.println("Échec de l'obtention du token d'accès Spotify après plusieurs tentatives.");
        return null;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (message.startsWith("!play")) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                event.getChannel().sendMessage("Vous devez fournir un lien ou des mots-clés après la commande !play.").queue();
                return;
            }
            String input = parts[1];
            GuildVoiceState voiceState = event.getMember().getVoiceState();
            AudioChannelUnion channelUnion = voiceState.getChannel();
            if (channelUnion instanceof VoiceChannel) {
                VoiceChannel channel = (VoiceChannel) channelUnion;
                if (!event.getGuild().getAudioManager().isConnected()) {
                    event.getGuild().getAudioManager().openAudioConnection(channel);
                    System.out.println("Bot connecté au canal vocal : " + channel.getName());
                } else {
                    System.out.println("Bot déjà connecté à un canal vocal.");
                }
                if (musicManager == null) {
                    musicManager = new MusicManager(playerManager, event.getGuild(), event.getJDA(), event.getChannel().asTextChannel(), youTubeService);
                }
                event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));

                if (input.startsWith("https://open.spotify.com/")) {
                    System.out.println("Traitement du lien Spotify en recherche YouTube : " + input);
                    String spotifyTrackTitle = getTrackTitleFromSpotify(input);
                    if (spotifyTrackTitle != null) {
                        String youtubeUrl = youTubeSearcher.searchYoutube(spotifyTrackTitle, musicManager, event.getChannel());
                        if (youtubeUrl != null) {
                            String[] streamData = getYoutubeStreamUrlAndTitle(youtubeUrl);
                            if (streamData != null) {
                                String streamUrl = streamData[0];
                                String title = streamData[1];
                                String duration = streamData[3];
                                String thumbnailUrl = streamData[2];
                                String videoUrl = youtubeUrl;
                                String artist = spotifyTrackTitle.split(" ", 2)[1];

                                TrackInfo trackInfo = new TrackInfo(title, duration, artist, thumbnailUrl, videoUrl);
                                handleAudioLoadResult(streamUrl, trackInfo, event);
                            } else {
                                event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
                            }
                        } else {
                            event.getChannel().sendMessage("Aucun résultat YouTube trouvé pour cette piste Spotify.").queue();
                        }
                    } else {
                        event.getChannel().sendMessage("Impossible de récupérer les informations de la piste Spotify.").queue();
                    }
                } else if (input.startsWith("http://") || input.startsWith("https://www.youtube.com") || input.startsWith("https://youtu.be")) {
                    String[] streamData = getYoutubeStreamUrlAndTitle(input);
                    if (streamData != null) {
                        String streamUrl = streamData[0];
                        String title = streamData[1];
                        String duration = streamData[3];
                        String videoId = extractYoutubeVideoId(input);
                        String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
                        String videoUrl = input;

                        TrackInfo videoInfo = musicManager.getScheduler().getYoutubeVideoInfo(videoId);
                        String artist = videoInfo.getArtist();

                        // Création de l'objet TrackInfo
                        TrackInfo trackInfo = new TrackInfo(title, duration, artist, thumbnailUrl, videoUrl);
                        handleAudioLoadResult(streamUrl, trackInfo, event);
                    } else {
                        event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
                    }
                }
            }
        } else if (message.startsWith("!pause")) {
            if (musicManager != null) {
                musicManager.getScheduler().pauseTrack();
                event.getChannel().sendMessage("Lecture mise en pause.").queue();
            }
        } else if (message.startsWith("!resume")) {
            if (musicManager != null) {
                musicManager.getScheduler().resumeTrack();
                event.getChannel().sendMessage("Lecture reprise.").queue();
            }
        } else if (message.startsWith("!skip")) {
            if (musicManager != null) {
                musicManager.getScheduler().skipTrack();
                event.getChannel().sendMessage("Piste sautée.").queue();
            }
        } else if (message.startsWith("!stop")) {
            if (musicManager != null) {
                musicManager.getScheduler().stopTrack();
                event.getChannel().sendMessage("Lecture arrêtée et file d'attente vidée.").queue();
            }
        } else if (message.startsWith("!clear")) {
            if (musicManager != null) {
                musicManager.getScheduler().clearQueue();
                event.getChannel().sendMessage("File d'attente vidée.").queue();
            }
        } else if (message.startsWith("!queue")) {
            if (musicManager != null) {
                musicManager.getScheduler().showQueue();
            }
        } else if (message.startsWith("!current")) {
            if (musicManager != null) {
                musicManager.getScheduler().showCurrentTrack(event.getChannel());
            }
        } else if (message.startsWith("!loop")) {
            if (musicManager != null) {
                boolean currentLooping = musicManager.getScheduler().isLooping();
                musicManager.getScheduler().setLooping(!currentLooping);
                
                // Envoyer uniquement le message indiquant que la boucle a été activée/désactivée sans renvoyer les informations de la piste
                event.getChannel().sendMessage("Lecture en boucle " + (!currentLooping ? "activée" : "désactivée") + ".").queue();
            }
        } else if (message.startsWith("!help")) {
            String helpMessage = "Commandes disponibles :\n" +
                    "!play <lien ou mots-clés> : Joue la musique demandée.\n" +
                    "!current : affiche les infos de la piste en cours." +
                    "!loop : Permet de jouer en boucle la track en cours." +
                    "!pause : Met la lecture en pause.\n" +
                    "!resume : Reprend la lecture.\n" +
                    "!skip : Saute la piste en cours.\n" +
                    "!stop : Arrête la lecture et vide la file d'attente.\n" +
                    "!clear : Vide la file d'attente sans arrêter la piste en cours.\n" +
                    "!queue : Affiche les pistes présentes dans la file d'attente.\n" +
                    "!current : Affiche la piste en cours de lecture.\n" +
                    "!help : Affiche toutes les commandes.";
            event.getChannel().sendMessage(helpMessage).queue();
        }
    }

    private void handleAudioLoadResult(String streamUrl, TrackInfo trackInfo, MessageReceivedEvent event) {
        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(trackInfo);
                event.getChannel().sendMessage("Ajouté à la file d'attente : " + trackInfo.getTitle()).queue();
                musicManager.getScheduler().queue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                event.getChannel().sendMessage("Playlist détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                for (AudioTrack track : playlist.getTracks()) {
                    musicManager.getScheduler().queue(track);
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
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
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
    
                // Correction de l'ordre des informations
                String title = titleReader.readLine();          // Le titre de la vidéo
                String thumbnailUrl = titleReader.readLine();   // L'URL de la miniature
                String duration = titleReader.readLine();       // La durée de la vidéo
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
    

    public String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu.be/|youtube.com/embed/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
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
