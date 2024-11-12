package com.MellianBot;

import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Classe qui sert d'intermédiaire pour envoyer l'audio à Discord.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    private final AudioPlayer audioPlayer;
    private AudioFrame lastFrame;

    /**
     * Constructeur qui initialise l'AudioPlayer.
     * 
     * @param audioPlayer l'instance de AudioPlayer pour la gestion de l'audio.
     */
    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Vérifie si l'audio peut être fourni.
     * 
     * @return true si un frame audio est disponible, sinon false.
     */
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    /**
     * Fournit 20 ms d'audio à Discord sous forme de ByteBuffer.
     * 
     * @return ByteBuffer contenant les données audio.
     */
    @Override
    public ByteBuffer provide20MsAudio() {
        return lastFrame != null ? ByteBuffer.wrap(lastFrame.getData()) : ByteBuffer.allocate(0);
    }

    /**
     * Indique si l'audio est au format Opus, utilisé par Discord.
     * 
     * @return true car le format est toujours Opus.
     */
    @Override
    public boolean isOpus() {
        return true;
    }
}
