/*
 * Copyright (c) 2021, Rodolfo Ruiz-Velasco <https://github.com/lequietriot>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.musicmask;

import com.google.inject.Provides;
import com.musicmask.musicsystem.MidiPcmStream;
import com.musicmask.musicsystem.MidiTrack;
import com.musicmask.musicsystem.PcmPlayer;
import com.musicmask.musicsystem.SoundPlayer;
import com.musicmask.resourcehandler.ResourceLoader;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.HttpUrl;

import javax.inject.Inject;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

@PluginDescriptor(
        name = "Music Mask",
        description = "Plays music over the game",
        tags = {"sound", "music"}
)
public class MusicMaskPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private MusicMaskConfig musicMaskConfig;

    private String currentSong;

    private boolean usingNewEngine;

    private byte[] midiFile;

    private boolean initialized = false;

    SoundPlayer[] soundPlayers;

    SourceDataLine sourceDataLine;

    public static final File RESOURCES_DIR = new File(RuneLite.RUNELITE_DIR.getPath() + File.separator + "music-mask-resources");

    public static final HttpUrl RAW_GITHUB = HttpUrl.parse("https://github.com/lequietriot/music-mask-hosting/raw/master/resources");

    @Override
    protected void startUp() {

        if (!RESOURCES_DIR.exists()) {
            RESOURCES_DIR.mkdirs();
        }

        Widget musicPlayingWidget = client.getWidget(WidgetID.MUSIC_GROUP_ID, 6);

        if (musicPlayingWidget != null) {

            if (!musicPlayingWidget.getText().equals(currentSong)) {
                currentSong = musicPlayingWidget.getText();
            }
        } else {
            currentSong = "Scape Main";
        }
        startSoundPlayer();
    }

    private byte[] loadMidiFileFromURL(String currentSong) throws IOException {
        return ResourceLoader.getURLResource("/MIDI/" + currentSong + ".mid/");
    }

    private void startSoundPlayer() {

        Thread songThread = new Thread(() -> {

            try {

                midiFile = loadMidiFileFromURL(currentSong.replace(" ", "%20").trim());

                if (!initialized && midiFile != null) {

                    soundPlayers = initStereoMidiStream(midiFile);

                    while (initialized) {
                        if (sourceDataLine == null) {
                            initSDL();
                        } else {
                            play(soundPlayers);
                        }
                    }
                }
            } catch (IOException | InvalidMidiDataException | UnsupportedAudioFileException | LineUnavailableException exception) {
                exception.printStackTrace();
            }
        });
        songThread.start();
    }

    private void initSDL() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, new AudioFormat(44100, 16, 2, true, false));
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceDataLine.open();
            sourceDataLine.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void play(SoundPlayer[] soundPlayers) throws LineUnavailableException {

        for (SoundPlayer soundPlayer : soundPlayers) {
            soundPlayer.fill(soundPlayer.samples, 256);
            soundPlayer.writeToBuffer();
        }

        byte[] audioL = soundPlayers[0].byteSamples;
        byte[] audioR = soundPlayers[1].byteSamples;
        byte[] audioBytes = new byte[audioL.length];

        for (int index = 0; index < audioBytes.length; index += 4) {
            audioBytes[index] = audioL[index];
            audioBytes[index + 1] = audioL[index + 1];
            audioBytes[index + 2] = audioR[index + 2];
            audioBytes[index + 3] = audioR[index + 3];
        }

        if (sourceDataLine != null) {
            sourceDataLine.write(audioBytes, 0, audioBytes.length);
        }
    }

    private SoundPlayer[] initStereoMidiStream(byte[] midiFile) throws IOException, InvalidMidiDataException, UnsupportedAudioFileException {

        PcmPlayer.pcmPlayer_sampleRate = 44100;
        PcmPlayer.pcmPlayer_stereo = true;

        MidiTrack.midi = midiFile;

        //LEFT
        MidiPcmStream midiPcmStreamL = new MidiPcmStream();
        midiPcmStreamL.init(9, 128);
        midiPcmStreamL.setMusicTrack(musicMaskConfig.getLoopingMode());
        midiPcmStreamL.setPcmStreamVolume(musicMaskConfig.getMusicVolume());
        midiPcmStreamL.loadStereoSoundBank(musicMaskConfig.getMusicVersion().version, true, !musicMaskConfig.getMusicVersion().version.equals("Custom"));

        SoundPlayer soundPlayerL = new SoundPlayer();
        soundPlayerL.setStream(midiPcmStreamL);
        soundPlayerL.samples = new int[512];
        soundPlayerL.init();

        //RIGHT
        MidiPcmStream midiPcmStreamR = new MidiPcmStream();
        midiPcmStreamR.init(9, 128);
        midiPcmStreamR.setMusicTrack(musicMaskConfig.getLoopingMode());
        midiPcmStreamR.setPcmStreamVolume(musicMaskConfig.getMusicVolume());
        midiPcmStreamR.loadStereoSoundBank(musicMaskConfig.getMusicVersion().version, false, !musicMaskConfig.getMusicVersion().version.equals("Custom"));

        SoundPlayer soundPlayerR = new SoundPlayer();
        soundPlayerR.setStream(midiPcmStreamR);
        soundPlayerR.samples = new int[512];
        soundPlayerR.init();

        if (midiPcmStreamL.midiFile.isReady() && midiPcmStreamR.midiFile.isReady()) {
            initialized = true;
            return new SoundPlayer[]{soundPlayerL, soundPlayerR};
        }
        else {
            return initStereoMidiStream(midiFile);
        }
    }

    @Subscribe
    protected void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (client.getGameState().equals(GameState.LOGIN_SCREEN)) {
            currentSong = "Scape Main";
        }
    }

    @Override
    protected void shutDown()
    {
        initialized = false;

        if (sourceDataLine != null) {
            sourceDataLine.stop();
            sourceDataLine.close();
            sourceDataLine = null;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getKey().equals("musicVersion")) {
            try {
                fadeOutSong(currentSong);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (configChanged.getKey().equals("musicVolume")) {
            for (SoundPlayer soundPlayer : soundPlayers) {
                ((MidiPcmStream) soundPlayer.stream).setPcmStreamVolume(Integer.parseInt(configChanged.getNewValue()));
            }
        }
    }

    @Provides
    MusicMaskConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(MusicMaskConfig.class);
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {

        Widget musicPlayingWidget = client.getWidget(WidgetID.MUSIC_GROUP_ID, 6);

        if (musicPlayingWidget != null) {

            if (!musicPlayingWidget.getText().equals(currentSong)) {
                currentSong = musicPlayingWidget.getText();
                try {
                    fadeOutSong(musicPlayingWidget.getText());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void fadeOutSong(String songName) throws InterruptedException {
        Thread fadeThread = new Thread(() -> {
            int volume = ((MidiPcmStream) soundPlayers[0].stream).getPcmStreamVolume();
            for (int step = volume; step > -1; step--) {
                ((MidiPcmStream) soundPlayers[0].stream).setPcmStreamVolume(step);
                ((MidiPcmStream) soundPlayers[1].stream).setPcmStreamVolume(step);
                if (step == 0) {
                    break;
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!songName.equals(currentSong)) {
                currentSong = songName;
            }

            if (sourceDataLine != null) {
                sourceDataLine.stop();
                sourceDataLine.close();
                sourceDataLine = null;
            }

            initialized = false;
            startSoundPlayer();
        });

        fadeThread.start();
    }
}
