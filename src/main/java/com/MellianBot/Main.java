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
import java.util.concurrent.BlockingQueue;
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
                System.out.println("Désolé, impossible de trouver config.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.spotifyClientId = properties.getProperty("spotify.client.id");
        this.spotifyClientSecret = properties.getProperty("spotify.client.secret");
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
                System.out.println("Désolé, impossible de trouver config.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String discordToken = properties.getProperty("discord.api.key");
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

    public String getSpotifyAccessToken() {
        String auth = spotifyClientId + ":" + spotifyClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
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
                event.getGuild().getAudioManager().openAudioConnection(channel);
                System.out.println("Bot connecté au canal vocal : " + channel.getName());
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
                                String thumbnailUrl = streamData[2];
                                
                                playerManager.loadItem(streamUrl, new AudioLoadResultHandler(){
                                    @Override
            public void trackLoaded(AudioTrack track) {
                // Set title, author, and thumbnail URL as user data
                track.setUserData(new String[]{title, spotifyTrackTitle, thumbnailUrl});
                
                event.getChannel().sendMessage("Ajouté à la file d'attente (YouTube) : " + title).queue();
                musicManager.getScheduler().queue(track);
            }
                                    

                                    @Override
                                    public void playlistLoaded(AudioPlaylist playlist) {
                                        event.getChannel().sendMessage("Playlist YouTube détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                                        for (AudioTrack track : playlist.getTracks()) {
                                            musicManager.getScheduler().queue(track);
                                        }
                                    }

                                    @Override
                                    public void noMatches() {
                                        event.getChannel().sendMessage("Aucune piste trouvée pour le lien YouTube.").queue();
                                    }

                                    @Override
                                    public void loadFailed(FriendlyException exception) {
                                        event.getChannel().sendMessage("Erreur lors du chargement de la piste YouTube.").queue();
                                    }
                                });
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
                        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
                                String title = track.getInfo().title != null ? track.getInfo().title : "Unknown Title";
                                String author = track.getInfo().author != null ? track.getInfo().author : "Unknown Artist/Channel";
                                String videoId = extractYoutubeVideoId(track.getInfo().uri);
                                String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
                            
                                // Store title, author, and thumbnail URL in user data
                                track.setUserData(new String[]{title, author, thumbnailUrl});
                            
                                event.getChannel().sendMessage("Ajouté à la file d'attente (YouTube) : " + title).queue();
                                musicManager.getScheduler().queue(track);
                            }
                            

                            @Override
                            public void playlistLoaded(AudioPlaylist playlist) {
                                event.getChannel().sendMessage("Playlist YouTube détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                                for (AudioTrack track : playlist.getTracks()) {
                                    musicManager.getScheduler().queue(track);
                                }
                            }

                            @Override
                            public void noMatches() {
                                event.getChannel().sendMessage("Aucune piste trouvée sur YouTube.").queue();
                            }

                            @Override
                            public void loadFailed(FriendlyException exception) {
                                event.getChannel().sendMessage("Erreur lors du chargement de la piste YouTube.").queue();
                            }
                        });
                    } else {
                        event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
                    }
                } else {
                    String url = youTubeSearcher.searchYoutube(input, musicManager, event.getChannel());
                    if (url == null) {
                        event.getChannel().sendMessage("Aucun résultat trouvé sur YouTube.").queue();
                        return;
                    }
                    String[] streamData = getYoutubeStreamUrlAndTitle(url);
                    if (streamData != null) {
                        String streamUrl = streamData[0];
                        String title = streamData[1];
                        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
                                track.setUserData(title);
                                event.getChannel().sendMessage("Ajouté à la file d'attente : " + title).queue();
                                musicManager.getScheduler().queue(track);
                            }

                            @Override
                            public void playlistLoaded(AudioPlaylist playlist) {
                                event.getChannel().sendMessage("Playlist YouTube détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                                for (AudioTrack track : playlist.getTracks()) {
                                    musicManager.getScheduler().queue(track);
                                }
                            }

                            @Override
                            public void noMatches() {
                                event.getChannel().sendMessage("Impossible de trouver cette piste sur YouTube.").queue();
                            }

                            @Override
                            public void loadFailed(FriendlyException exception) {
                                event.getChannel().sendMessage("Erreur lors du chargement de la piste YouTube : " + exception.getMessage()).queue();
                                exception.printStackTrace();
                            }
                        });
                    } else {
                        event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
                    }
                }
            } else {
                event.getChannel().sendMessage("Vous devez être dans un canal vocal pour jouer de la musique !").queue();
            }
        }

        if (message.equals("!help")) {
            String helpMessage = "**Commandes disponibles :**\n" +
                    "`!play <lien ou mots-clés>` : Joue une piste depuis un lien (YouTube ou Spotify) ou effectue une recherche par mots-clés.\n" +
                    "`!queue` : Affiche la file d'attente des pistes.\n" +
                    "`!current` : Affiche la piste en cours de lecture.\n" +
                    "`!pause` : Met la musique en pause.\n" +
                    "`!resume` : Relance la musique en cours.\n"+
                    "`!skip` : Passe à la piste suivante dans la file d'attente.\n" +
                    "`!stop` : Arrête la piste et vide la file d'attente.\n"+
                    "`!restart` : Remet la piste en cours au début.\n" +
                    "`!loop` : Fait boucler la piste en cours.\n" +
                    "`!help` : Affiche ce message d'aide.";
    
            event.getChannel().sendMessage(helpMessage).queue();
            return;
        }
        if (message.equals("!queue")) {
            BlockingQueue<AudioTrack> queue = musicManager.getScheduler().getQueue();
            if (queue.isEmpty()) {
                event.getChannel().sendMessage("La file d'attente est vide.").queue();
            } else {
                StringBuilder queueList = new StringBuilder("**File d'attente :**\n");
                int trackNumber = 1;
                long totalDuration = 0;
        
                for (AudioTrack track : queue) {
                    String trackTitle = (String) track.getUserData();
                    if (trackTitle == null) {
                        trackTitle = "Titre inconnu (" + track.getInfo().uri + ")";
                    }
                    String trackDuration = formatDuration(track.getDuration());
                    totalDuration += track.getDuration();
                    queueList.append("`").append(trackNumber).append(".` ")
                             .append(trackTitle).append(" - ").append(trackDuration).append("\n");
                    trackNumber++;
                }
        
                String formattedTotalDuration = formatDuration(totalDuration);
                queueList.append("\n**Durée totale de la playlist :** ").append(formattedTotalDuration);
                event.getChannel().sendMessage(queueList.toString()).queue();
            }
        }
        if (message.equals("!loop")) {
            boolean isLooping = musicManager.getScheduler().isLooping();
            musicManager.getScheduler().setLooping(!isLooping);
        
            String loopStatus = isLooping ? "désactivée" : "activée";
            event.getChannel().sendMessage("La boucle a été " + loopStatus + " pour la piste en cours.").queue();
            return;
        }
        if (message.equals("!restart")) {
            AudioTrack currentTrack = musicManager.getPlayer().getPlayingTrack();
            if (currentTrack != null) {
                currentTrack.setPosition(0);
                event.getChannel().sendMessage("La piste a été remise au début : " + currentTrack.getInfo().title).queue();
            } else {
                event.getChannel().sendMessage("Aucune piste n'est en cours de lecture pour être remise au début.").queue();
            }
            return;
        }
        
        if (message.equals("!stop")) {
            musicManager.getScheduler().getQueue().clear();
            musicManager.getPlayer().stopTrack();
            musicManager.getPlayer().setPaused(false);
            event.getGuild().getAudioManager().closeAudioConnection();
            event.getChannel().sendMessage("Musique arrêtée et file d'attente vidée.").queue();
            return;
        }
        if (message.equals("!current")) {
            AudioTrack currentTrack = musicManager.getScheduler().getCurrentTrack();
            if (currentTrack != null) {
                String trackTitle = (String) currentTrack.getUserData();
                if (trackTitle == null) {
                    trackTitle = "Titre inconnu";
                }
                
                long position = currentTrack.getPosition();
                long duration = currentTrack.getDuration();
                long timeRemaining = duration - position;
        
                String formattedPosition = formatDuration(position);
                String formattedDuration = formatDuration(duration);
                String formattedRemaining = formatDuration(timeRemaining);
        
                String trackInfo = "**En cours de lecture :**\n" +
                        "Titre : " + trackTitle + "\n" +
                        "Temps écoulé : `" + formattedPosition + "` / `" + formattedDuration + "`\n" +
                        "Temps restant : `" + formattedRemaining + "`";
        
                event.getChannel().sendMessage(trackInfo).queue();
            } else {
                event.getChannel().sendMessage("Aucune musique n'est en cours de lecture.").queue();
            }
        }
        

        if (message.equals("!pause")) {
            musicManager.getPlayer().setPaused(true);
            event.getChannel().sendMessage("Musique mise en pause. Utilisez `!resume` pour reprendre.").queue();
            return;
        }
        if (message.equals("!resume")) {
            if (musicManager.getPlayer().isPaused()) {
                musicManager.getPlayer().setPaused(false);
                event.getChannel().sendMessage("Reprise de la musique.").queue();
            } else {
                event.getChannel().sendMessage("La musique est déjà en cours de lecture.").queue();
            }
            return;
        }
        if (message.startsWith("!skip")) {
            TrackScheduler scheduler = musicManager.getScheduler();
            if (musicManager.getPlayer().getPlayingTrack() != null) {
                scheduler.skipTrack();
                event.getChannel().sendMessage("Piste sautée ! Lecture de la piste suivante.").queue();
            } else {
                event.getChannel().sendMessage("Aucune piste en cours de lecture !").queue();
            }
        }
    }
    private String formatDuration(long duration) {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    public String getTrackTitleFromSpotify(String spotifyUrl) {
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
            // Using yt-dlp to retrieve both audio URL and title
            ProcessBuilder urlBuilder = new ProcessBuilder("yt-dlp", "-f", "bestaudio", "--get-url", videoUrl);
            Process urlProcess = urlBuilder.start();
            BufferedReader urlReader = new BufferedReader(new InputStreamReader(urlProcess.getInputStream()));
            String streamUrl = urlReader.readLine();
            int urlExitCode = urlProcess.waitFor();
    
            if (urlExitCode == 0 && streamUrl != null && !streamUrl.isEmpty()) {
                ProcessBuilder titleBuilder = new ProcessBuilder("yt-dlp", "--get-title", videoUrl);
                Process titleProcess = titleBuilder.start();
                BufferedReader titleReader = new BufferedReader(new InputStreamReader(titleProcess.getInputStream()));
                String title = titleReader.readLine();
                int titleExitCode = titleProcess.waitFor();
    
                if (titleExitCode == 0 && title != null && !title.isEmpty()) {
                    String videoId = extractYoutubeVideoId(videoUrl);
                    String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
                    return new String[]{streamUrl, title, thumbnailUrl}; // Include thumbnail URL
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
    

    public String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu.be\\/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
