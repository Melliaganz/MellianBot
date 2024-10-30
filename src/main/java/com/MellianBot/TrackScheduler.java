// TrackScheduler.java
package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private Guild guild;
    private final BlockingQueue<AudioTrack> queue;
    private JDA jda;
    private AudioTrack currentTrack;
    private static final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);

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
        if (!player.startTrack(track, true)) {
            queue.offer(track);
            logger.info("Piste ajoutée à la file d'attente : {} | Source : {}", track.getInfo().title, track.getSourceManager().getSourceName());
        } else {
            currentTrack = track;
            updateBotActivity(track);
            logger.info("Lecture de la piste : {}", track.getInfo().title);
        }
    }



    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            currentTrack = nextTrack;
            player.startTrack(nextTrack, false);
            updateBotActivity(nextTrack);
            logger.info("Lecture de la piste suivante : {}", nextTrack.getInfo().title);
        } else {
            if (player.getPlayingTrack() == null && guild != null) {
                guild.getAudioManager().closeAudioConnection();
                jda.getPresence().setActivity(Activity.watching("un bon gros boulard"));
                logger.info("File d'attente vide, déconnexion du bot du canal vocal");
            }
        }
    }



    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        System.out.println("Début de lecture de la piste : " + track.getInfo().title);
        logger.info("Track loaded: " + track.getInfo().title);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        logger.info("Fin de la piste : {}, raison : {}", track.getInfo().title, endReason);
        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            logger.error("Le chargement de la piste a échoué. Détails de la piste : titre = {}, URI = {}, source = {}",
                    track.getInfo().title, track.getInfo().uri, track.getSourceManager().getSourceName());

            // Capturer l'exception si elle est présente
            if (track.getUserData() instanceof FriendlyException) {
                FriendlyException exception = (FriendlyException) track.getUserData();
                logger.error("Exception lors du chargement de la piste : {}", exception.getMessage(), exception);
            }
        }

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
