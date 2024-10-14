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
import com.google.api.services.youtube.YouTube;
import com.MellianBot.YouTubeSearcher;
import com.MellianBot.MusicManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
            String url = message.split(" ")[1]; // Get URL after command
            GuildVoiceState voiceState = event.getMember().getVoiceState();
            AudioChannelUnion channelUnion = voiceState.getChannel();

            if (channelUnion instanceof VoiceChannel) {
                VoiceChannel channel = (VoiceChannel) channelUnion;
                event.getGuild().getAudioManager().openAudioConnection(channel);

                // Create YouTube service instance
                YouTube youtubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                        .setApplicationName("Your Application Name")
                        .build();

                YouTubeSearcher searcher = new YouTubeSearcher(youtubeService);
                searcher.searchYoutube(url, musicManager, event.getChannel());

                // Load item from player manager
                playerManager.loadItem(url, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        event.getChannel().sendMessage("Lecture de : " + track.getInfo().title).queue();
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
}
