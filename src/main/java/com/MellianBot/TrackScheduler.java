package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private Guild guild;
    private final BlockingQueue<AudioTrack> queue;

    public TrackScheduler(AudioPlayer player, Guild guild) {
        this.player = player;
        this.guild = guild;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    // Méthode pour ajouter une piste à la file d'attente
    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    // Méthode pour passer à la piste suivante
    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.startTrack(nextTrack, false);  // Joue la prochaine piste
        } else {
            // Ne déconnecte que si la file d'attente est vraiment vide et qu'aucune autre piste ne joue
            if (player.getPlayingTrack() == null) {
                guild.getAudioManager().closeAudioConnection();
            }
        }
    }

    @Override
    public void onEvent(com.sedmelluq.discord.lavaplayer.player.event.AudioEvent event) {
        if (event instanceof TrackEndEvent) {
            nextTrack();  // Appelle nextTrack() lorsque la piste actuelle se termine
        }
    }

    // Méthode pour récupérer la file d'attente
    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }
}
