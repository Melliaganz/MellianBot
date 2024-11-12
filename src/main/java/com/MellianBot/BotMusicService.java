package com.MellianBot;

import com.google.api.services.youtube.YouTube;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.HashMap;
import java.util.Map;

public class BotMusicService {
    // Map pour stocker un MusicManager par serveur (guilde)
    private final Map<Guild, MusicManager> musicManagers = new HashMap<>();
    private final AudioPlayerManager playerManager;
    private final YouTube youTubeService;

    /**
     * Constructeur de BotMusicService.
     * Initialise le AudioPlayerManager et enregistre les sources audio.
     *
     * @param youTubeService Le service YouTube pour accéder aux vidéos.
     */
    public BotMusicService(YouTube youTubeService) {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        this.youTubeService = youTubeService;
    }

    /**
     * Récupère le MusicManager pour une guilde donnée. En crée un nouveau si nécessaire.
     *
     * @param guild La guilde pour laquelle récupérer le MusicManager.
     * @param textChannel Le canal texte où envoyer les messages de musique.
     * @param jda L'instance de JDA.
     * @return Le MusicManager correspondant à la guilde.
     */
    public synchronized MusicManager getMusicManager(Guild guild, TextChannel textChannel, JDA jda) {
        // Récupère ou crée un MusicManager pour cette guilde
        return musicManagers.computeIfAbsent(guild, g -> new MusicManager(playerManager, guild, jda, textChannel, youTubeService));
    }

    /**
     * Retire le MusicManager pour une guilde lorsque la musique est terminée.
     *
     * @param guild La guilde pour laquelle supprimer le MusicManager.
     */
    public synchronized void removeMusicManager(Guild guild) {
        musicManagers.remove(guild);
    }
}
