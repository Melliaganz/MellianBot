package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

public class MusicManager {
    private final AudioPlayer player;
    private final TrackScheduler scheduler;

    public MusicManager(AudioPlayerManager manager, Guild guild, JDA jda) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player, guild, jda);
        this.player.addListener(this.scheduler);
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public void updateScheduler(Guild guild, JDA jda) {
        this.scheduler.setGuild(guild);
        this.scheduler.setJDA(jda);
    }
}
