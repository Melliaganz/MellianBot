// Main.java
package com.MellianBot;

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
    private final YouTube youTubeService;
    private final YouTubeSearcher youTubeSearcher;

    public Main() {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        // SpotifyAudioSourceManager removed due to missing dependency
        this.youTubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                .setApplicationName("MellianBot")
                .build();

        this.youTubeSearcher = new YouTubeSearcher(youTubeService);
    }

    public static void main(String[] args) throws Exception {
        // Load API keys from config.properties
        Properties properties = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return;
            }
            // Load properties file
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Get the Discord bot token from properties
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

                // Attach the AudioPlayerSendHandler to the AudioManager
                event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));

                String url = input;
                // Vérifier si l'entrée n'est pas une URL (utiliser un simple regex pour détecter les liens YouTube)
                if (!input.startsWith("http://") && !input.startsWith("https://")) {
                    // Recherche sur YouTube avec les mots-clés fournis
                    url = youTubeSearcher.searchYoutube(input, musicManager, event.getChannel());
                    if (url == null) {
                        // Si aucun résultat n'a été trouvé ou une erreur s'est produite, arrêter ici
                        return;
                    }
                }

                // Charger la musique à partir de l'URL
                playerManager.loadItem(url, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        track.setUserData(track.getInfo().title);  // Stocke le titre dans les métadonnées de la piste
                        event.getChannel().sendMessage("Ajouté à la file d'attente : " + track.getInfo().title).queue();
                        musicManager.getScheduler().queue(track);  // Ajoute la piste à la file d'attente
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        event.getChannel().sendMessage("Playlist détectée. Ajout de " + playlist.getTracks().size() + " pistes à la file d'attente.").queue();
                        for (AudioTrack track : playlist.getTracks()) {
                            musicManager.getScheduler().queue(track);  // Ajoute chaque piste à la file d'attente
                        }
                    }

                    @Override
                    public void noMatches() {
                        event.getChannel().sendMessage("Impossible de trouver cette piste.").queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        event.getChannel().sendMessage("Erreur lors du chargement de la piste.").queue();
                    }
                });
            } else {
                event.getChannel().sendMessage("Vous devez être dans un canal vocal pour jouer de la musique !").queue();
            }
        }

        // Gestion de la commande !queue
        if (message.equals("!queue")) {
            BlockingQueue<AudioTrack> queue = musicManager.getScheduler().getQueue();
            if (queue.isEmpty()) {
                event.getChannel().sendMessage("La file d'attente est vide.").queue();
            } else {
                StringBuilder queueList = new StringBuilder("Pistes en attente :\n");
                for (AudioTrack track : queue) {
                    String trackTitle = (String) track.getUserData();  // Récupère le titre depuis les métadonnées
                    if (trackTitle == null) {
                        trackTitle = "Titre inconnu (" + track.getInfo().uri + ")";
                    }
                    queueList.append(trackTitle).append("\n");
                }
                event.getChannel().sendMessage(queueList.toString()).queue();
            }
        }

        // Gestion de la commande !playing
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
                scheduler.skipTrack();  // Sauter la piste actuelle
                event.getChannel().sendMessage("Piste sautée ! Lecture de la piste suivante.").queue();
            } else {
                event.getChannel().sendMessage("Aucune piste en cours de lecture !").queue();
            }
        }

    }

    /**
     * Méthode pour exécuter yt-dlp et récupérer l'URL du flux audio ainsi que le titre.
     */
    private String[] getYoutubeStreamUrlAndTitle(String videoUrl) {
        try {
            // Utilisation de yt-dlp pour récupérer l'URL du flux audio
            ProcessBuilder builder = new ProcessBuilder("yt-dlp", "-f", "bestaudio", "--get-url", videoUrl);
            Map<String, String> env = System.getenv();
            String path = env.get("PATH") + ":/usr/local/bin"; // Assurez-vous que yt-dlp est accessible
            builder.environment().put("PATH", path);
            Process process = builder.start();

            // Lire l'URL du flux audio depuis la sortie standard
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String streamUrl = reader.readLine();  // Lire la première ligne qui contient l'URL

            // Attendre la fin du processus yt-dlp
            int exitCode = process.waitFor();
            if (exitCode == 0 && streamUrl != null && !streamUrl.isEmpty()) {
                // Récupérer le titre de la vidéo en utilisant yt-dlp
                ProcessBuilder titleBuilder = new ProcessBuilder("yt-dlp", "--get-title", videoUrl);
                Process titleProcess = titleBuilder.start();
                BufferedReader titleReader = new BufferedReader(new InputStreamReader(titleProcess.getInputStream()));
                String title = titleReader.readLine();  // Lire le titre

                // Attendre la fin du processus de titre
                int titleExitCode = titleProcess.waitFor();
                if (titleExitCode == 0 && title != null && !title.isEmpty()) {
                    return new String[]{streamUrl, title}; // Retourner l'URL et le titre
                }
            } else {
                System.out.println("yt-dlp a échoué avec le code : " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Méthode utilitaire pour extraire l'ID vidéo de l'URL YouTube
    public String extractYoutubeVideoId(String url) {
        String pattern = "(?<=watch\\?v=|youtu.be\\/)[^&]+";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();  // Retourne l'ID de la vidéo
        }
        return null;
    }
}
