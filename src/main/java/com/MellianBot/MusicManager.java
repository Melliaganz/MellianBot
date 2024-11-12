package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import com.google.api.services.youtube.YouTube;

public class MusicManager {
    private final AudioPlayer player;
    private final TrackScheduler scheduler;

    public MusicManager(AudioPlayerManager manager, Guild guild, JDA jda, TextChannel textChannel, YouTube youTubeService) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player, guild, jda, textChannel, youTubeService); // Passe le service YouTube
        this.player.addListener(this.scheduler);
    }
    public AudioPlayer getPlayer() {
        return player;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public void updateScheduler(Guild guild, JDA jda, TextChannel textChannel) {
        this.scheduler.setChannel(textChannel); // Ensure scheduler can update the text channel
    }
}
