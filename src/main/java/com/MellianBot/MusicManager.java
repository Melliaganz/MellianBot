package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import com.google.api.services.youtube.YouTube;

public class MusicManager {

    // Le lecteur audio qui gère la lecture de la musique
    private final AudioPlayer player;
    // Le planificateur de pistes pour gérer la file d'attente et le contrôle de lecture
    private final TrackScheduler scheduler;

    /**
     * Constructeur de MusicManager.
     * Initialise le lecteur audio et le planificateur de pistes, et ajoute le planificateur comme écouteur au lecteur.
     *
     * @param manager Le gestionnaire de lecteurs audio.
     * @param guild La guilde (serveur Discord) associée.
     * @param jda L'instance de JDA pour interagir avec Discord.
     * @param textChannel Le canal texte où les messages de musique seront envoyés.
     * @param youTubeService Le service YouTube pour accéder aux vidéos.
     */
    public MusicManager(AudioPlayerManager manager, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player, guild, jda, textChannel, youTubeService);
        this.player.addListener(scheduler);
    }

    /**
     * Retourne le lecteur audio de ce gestionnaire.
     *
     * @return Le lecteur audio.
     */
    public AudioPlayer getPlayer() {
        return player;
    }

    /**
     * Retourne le planificateur de pistes de ce gestionnaire.
     *
     * @return Le planificateur de pistes.
     */
    public TrackScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Met à jour le canal texte du planificateur de pistes.
     * Utilisé pour rediriger les messages vers un autre canal si nécessaire.
     *
     * @param textChannel Le nouveau canal texte où les messages de musique seront envoyés.
     */
    public void updateScheduler(TextChannel textChannel) {
        scheduler.setChannel(textChannel);
    }
}
