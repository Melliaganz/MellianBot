// TrackScheduler.java
package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.JDA;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private Guild guild;
    private final BlockingQueue<AudioTrack> queue;
    private JDA jda;
    private AudioTrack currentTrack;

    public TrackScheduler(AudioPlayer player, Guild guild, JDA jda) {
        this.player = player;
        this.guild = guild;
        this.jda = jda;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {  // Essaie de démarrer la piste si aucune autre n'est en lecture
            queue.offer(track);  // Si une piste est en cours, ajoute la nouvelle à la file d'attente
        } else {
            currentTrack = track;
            updateBotActivity(track);  // Met à jour l'activité du bot avec la piste en cours
        }
    }

    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();  // Récupère la prochaine piste de la file d'attente
        if (nextTrack != null) {
            currentTrack = nextTrack;
            player.startTrack(nextTrack, false);  // Charge et joue la piste suivante
            updateBotActivity(nextTrack);  // Met à jour l'activité du bot avec la nouvelle piste
        } else {
            if (player.getPlayingTrack() == null && guild != null) {  // Vérifie que guild n'est pas null avant de tenter la déconnexion
                guild.getAudioManager().closeAudioConnection();  // Si la file est vide et aucune piste ne joue, déconnecte le bot
                jda.getPresence().setActivity(Activity.watching("un bon gros boulard"));  // Revenir à l'activité par défaut
            }
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Si la piste se termine naturellement, on passe à la suivante
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    // Méthode pour skipper une piste
    public void skipTrack() {
        nextTrack();  // Passe à la piste suivante dans la file d'attente
    }

    // Méthode pour récupérer la file d'attente
    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    // Méthode pour obtenir la piste en cours de lecture
    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    // Méthode pour mettre à jour l'activité du bot
    private void updateBotActivity(AudioTrack track) {
        String trackTitle = (String) track.getUserData();
        if (trackTitle == null) {
            trackTitle = "Titre inconnu";
        }
        jda.getPresence().setActivity(Activity.listening(trackTitle));
    }
}