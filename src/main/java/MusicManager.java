package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class MusicManager {
    private final AudioPlayer player;

    public MusicManager(AudioPlayerManager playerManager) {
        this.player = playerManager.createPlayer();
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public void playTrack(AudioTrack track) {
        player.playTrack(track);
    }

    public void stopTrack() {
        player.stopTrack();
    }
}
