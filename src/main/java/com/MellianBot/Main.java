package com.MellianBot;

import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
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

    public Main() {
        this.playerManager = new DefaultAudioPlayerManager();

        // Charger les clés API Spotify depuis config.properties
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

        // Initialiser le gestionnaire Spotify avec les clés chargées
        SpotifySourceManager spotifySourceManager = new SpotifySourceManager(
                new String[]{"user-read-email", "playlist-read-private", "streaming"},
                spotifyClientId,
                spotifyClientSecret,
                null,  // URI de redirection si nécessaire
                playerManager
        );

        // Enregistrement du gestionnaire Spotify
        playerManager.registerSourceManager(spotifySourceManager);

        // Enregistrement des autres sources audio
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        this.youTubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                .setApplicationName("MellianBot")
                .build();

        this.youTubeSearcher = new YouTubeSearcher(youTubeService);
    }

    public static void main(String[] args) throws Exception {
        // Charger les clés API à partir de config.properties
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

        // Obtenir le token du bot Discord depuis les propriétés
        String discordToken = properties.getProperty("discord.api.key");

        JDABuilder builder = JDABuilder.createDefault(discordToken,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_VOICE_STATES);

        builder.setActivity(Activity.listening("de la musique avec !play"));
        builder.addEventListeners(new Main());
        builder.build();
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

            String input = parts[1];  // Obtenir l'entrée après la commande !play
            GuildVoiceState voiceState = event.getMember().getVoiceState();
            AudioChannelUnion channelUnion = voiceState.getChannel();

            if (channelUnion instanceof VoiceChannel) {
                VoiceChannel channel = (VoiceChannel) channelUnion;
                event.getGuild().getAudioManager().openAudioConnection(channel);

                // Initialiser MusicManager avec JDA et Guild
                if (musicManager == null) {
                    musicManager = new MusicManager(playerManager, event.getGuild(), event.getJDA());
                }

                // Attacher l'AudioPlayerSendHandler à l'AudioManager
                event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));

                // Vérifier si c'est un lien Spotify
                if (input.startsWith("https://open.spotify.com/")) {
                    System.out.println("Traitement du lien Spotify avec lavasrc : " + input);

                    // Charger directement l'élément Spotify
                    playerManager.loadItem(input, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            track.setUserData(track.getInfo().title);
                            event.getChannel().sendMessage("Ajouté à la file d'attente (Spotify) : " + track.getInfo().title).queue();
                            musicManager.getScheduler().queue(track);
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            event.getChannel().sendMessage("Playlist Spotify détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                            for (AudioTrack track : playlist.getTracks()) {
                                musicManager.getScheduler().queue(track);
                            }
                        }

                        @Override
                        public void noMatches() {
                            event.getChannel().sendMessage("Aucune piste trouvée sur Spotify.").queue();
                        }

                        @Override
                        public void loadFailed(FriendlyException exception) {
                            event.getChannel().sendMessage("Erreur lors du chargement de la piste Spotify : " + exception.getMessage()).queue();
                            exception.printStackTrace();
                        }
                    });
                }

                // Vérifier si c'est un lien YouTube
                else if (input.startsWith("http://") || input.startsWith("https://www.youtube.com") || input.startsWith("https://youtu.be")) {
                    // Traiter les liens YouTube avec yt-dlp
                    String[] streamData = getYoutubeStreamUrlAndTitle(input);
                    if (streamData != null) {
                        String streamUrl = streamData[0];
                        String title = streamData[1];

                        playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
                                track.setUserData(title);
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
                    // Recherche sur YouTube avec des mots-clés
                    String url = youTubeSearcher.searchYoutube(input, musicManager, event.getChannel());
                    if (url == null) {
                        event.getChannel().sendMessage("Aucun résultat trouvé sur YouTube.").queue();
                        return;
                    }

                    // Charger la musique YouTube trouvée
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

        if (message.equals("!queue")) {
            BlockingQueue<AudioTrack> queue = musicManager.getScheduler().getQueue();
            if (queue.isEmpty()) {
                event.getChannel().sendMessage("La file d'attente est vide.").queue();
            } else {
                StringBuilder queueList = new StringBuilder("Pistes en attente :\n");
                for (AudioTrack track : queue) {
                    String trackTitle = (String) track.getUserData();
                    if (trackTitle == null) {
                        trackTitle = "Titre inconnu (" + track.getInfo().uri + ")";
                    }
                    queueList.append(trackTitle).append("\n");
                }
                event.getChannel().sendMessage(queueList.toString()).queue();
            }
        }

        if (message.equals("!current")) {
            AudioTrack currentTrack = musicManager.getScheduler().getCurrentTrack();
            if (currentTrack != null) {
                String trackTitle = (String) currentTrack.getUserData();
                event.getChannel().sendMessage("En cours de lecture : " + trackTitle).queue();
            } else {
                event.getChannel().sendMessage("Aucune musique n'est en cours de lecture.").queue();
            }
        }

        if (message.equals("!pause")) {
            musicManager.getPlayer().setPaused(true);
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

    private String[] getYoutubeStreamUrlAndTitle(String videoUrl) {
        try {
            ProcessBuilder builder = new ProcessBuilder("yt-dlp", "-f", "bestaudio", "--get-url", videoUrl);
            Map<String, String> env = System.getenv();
            String path = env.get("PATH") + ":/usr/local/bin";
            builder.environment().put("PATH", path);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String streamUrl = reader.readLine();

            int exitCode = process.waitFor();
            if (exitCode == 0 && streamUrl != null && !streamUrl.isEmpty()) {
                ProcessBuilder titleBuilder = new ProcessBuilder("yt-dlp", "--get-title", videoUrl);
                Process titleProcess = titleBuilder.start();
                BufferedReader titleReader = new BufferedReader(new InputStreamReader(titleProcess.getInputStream()));
                String title = titleReader.readLine();

                int titleExitCode = titleProcess.waitFor();
                if (titleExitCode == 0 && title != null && !title.isEmpty()) {
                    return new String[]{streamUrl, title};
                }
            } else {
                System.out.println("yt-dlp a échoué avec le code : " + exitCode);
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
