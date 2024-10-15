package com.MellianBot;

import com.google.api.client.http.javanet.NetHttpTransport;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Map;

public class Main extends ListenerAdapter {
    private final AudioPlayerManager playerManager;
    private final MusicManager musicManager;
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public Main() {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        this.musicManager = new MusicManager(playerManager);
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

        builder.setActivity(Activity.playing("de la musique"));
        builder.addEventListeners(new Main());
        builder.build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();

        if (message.startsWith("!play")) {
            String[] parts = message.split(" ");
            if (parts.length < 2) {
                event.getChannel().sendMessage("Vous devez fournir une URL après la commande !play.").queue();
                return;
            }

            String url = parts[1]; // Get URL after command
            GuildVoiceState voiceState = event.getMember().getVoiceState();
            AudioChannelUnion channelUnion = voiceState.getChannel();

            if (channelUnion instanceof VoiceChannel) {
                VoiceChannel channel = (VoiceChannel) channelUnion;
                event.getGuild().getAudioManager().openAudioConnection(channel);

                // Attach the AudioPlayerSendHandler to the AudioManager
                event.getGuild().getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicManager.getPlayer()));

                // Récupérer le flux audio et le titre via yt-dlp
                String[] streamData = getYoutubeStreamUrlAndTitle(url);

                if (streamData != null) {
                    String streamUrl = streamData[0];
                    String title = streamData[1];

                    // Charger le flux récupéré avec Lavaplayer
                    playerManager.loadItem(streamUrl, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            event.getChannel().sendMessage("Lecture de : " + title).queue();
                            musicManager.getPlayer().playTrack(track);
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            event.getChannel().sendMessage("Playlist détectée.").queue();
                            for (AudioTrack track : playlist.getTracks()) {
                                musicManager.getPlayer().playTrack(track);
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
                    event.getChannel().sendMessage("Erreur lors de la récupération du flux audio avec yt-dlp.").queue();
                }
            } else {
                event.getChannel().sendMessage("Vous devez être dans un canal vocal pour jouer de la musique !").queue();
            }
        }

        if (message.equals("!pause")) {
            musicManager.getPlayer().setPaused(true);
        }

        if (message.equals("!skip")) {
            musicManager.getPlayer().stopTrack();
        }
    }

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


    // The remaining methods (getYoutubeStreamUrlAndTitle, etc.) stay the same
}
