package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import net.dv8tion.jda.api.entities.Guild;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final Guild guild;

    public TrackScheduler(AudioPlayer player, Guild guild) {
        this.player = player;
        this.guild = guild;
    }

    @Override
    public void onEvent(com.sedmelluq.discord.lavaplayer.player.event.AudioEvent event) {
        if (event instanceof TrackEndEvent) {
            // Déconnecte le bot du canal vocal lorsqu'il n'y a plus de musique à jouer
            guild.getAudioManager().closeAudioConnection();
        }
    }
}
