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
import com.sun.media.sound.AudioSynthesizer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

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

    private String maskPath;

    private AudioSynthesizer audioSynthesizer;

    private boolean usingNewEngine;

    private File midiFile;

    private boolean initialized = false;

    SoundPlayer[] soundPlayers;

    SourceDataLine sourceDataLine;

    @Override
    protected void startUp() {

        File resource = new File("resources/MusicMask/RLMusicMask/");
        maskPath = resource.getAbsolutePath();

        Widget musicPlayingWidget = client.getWidget(WidgetID.MUSIC_GROUP_ID, 6);

        if (musicPlayingWidget != null) {

            if (!musicPlayingWidget.getText().equals(currentSong)) {
                currentSong = musicPlayingWidget.getText();
            }
        }

        else {
            currentSong = "Scape Main";
        }

        startSoundPlayer();
    }

    private void startSoundPlayer() {

        Thread songThread = new Thread(() -> {

            try {
                File[] midiFiles = new File(maskPath + "/MIDI/").listFiles();
                if (midiFiles != null) {
                    for (File midi : midiFiles) {
                        if (midi.getName().contains(" - ")) {
                            int index = midi.getName().lastIndexOf(" - ");
                            String name = midi.getName().substring(index).replace(".mid", "").replace(" - ", "").trim();

                            if (name.equalsIgnoreCase(currentSong)) {
                                midiFile = midi;
                            }
                        }

                        if (midi.getName().replace(".mid", "").trim().equalsIgnoreCase(currentSong)) {
                            midiFile = midi;
                        }
                    }
                }

                if (!initialized) {

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

        sourceDataLine.write(audioBytes, 0, audioBytes.length);
    }

    private SoundPlayer[] initStereoMidiStream(File midi) throws IOException, InvalidMidiDataException, UnsupportedAudioFileException {

        PcmPlayer.pcmPlayer_sampleRate = 44100;
        PcmPlayer.pcmPlayer_stereo = true;

        Path path = Paths.get(midi.toURI());
        MidiTrack.midi = Files.readAllBytes(path);

        //LEFT
        MidiPcmStream midiPcmStreamL = new MidiPcmStream();
        midiPcmStreamL.init(9, 128);
        midiPcmStreamL.setMusicTrack(musicMaskConfig.getLoopingMode());
        midiPcmStreamL.setPcmStreamVolume(musicMaskConfig.getMusicVolume());
        midiPcmStreamL.loadStereoSoundBank(new File(maskPath + "/SF2/" + musicMaskConfig.getMusicVersion().version), new File((maskPath + "/Patches/")), true, musicMaskConfig.getMusicVersion().version.equals("RS3"));

        SoundPlayer soundPlayerL = new SoundPlayer();
        soundPlayerL.setStream(midiPcmStreamL);
        soundPlayerL.samples = new int[512];
        soundPlayerL.init();

        //RIGHT
        MidiPcmStream midiPcmStreamR = new MidiPcmStream();
        midiPcmStreamR.init(9, 128);
        midiPcmStreamR.setMusicTrack(musicMaskConfig.getLoopingMode());
        midiPcmStreamR.setPcmStreamVolume(musicMaskConfig.getMusicVolume());
        midiPcmStreamR.loadStereoSoundBank(new File(maskPath + "/SF2/" + musicMaskConfig.getMusicVersion().version), new File((maskPath + "/Patches/")), false, musicMaskConfig.getMusicVersion().version.equals("RS3"));

        SoundPlayer soundPlayerR = new SoundPlayer();
        soundPlayerR.setStream(midiPcmStreamR);
        soundPlayerR.samples = new int[512];
        soundPlayerR.init();

        initialized = true;

        return new SoundPlayer[]{soundPlayerL, soundPlayerR};
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        /**
        if (widgetLoaded.getGroupId() == WidgetID.MUSIC_GROUP_ID) {
            Widget trackListWidget = client.getWidget(WidgetID.MUSIC_GROUP_ID, 3);
            if (trackListWidget != null) {
                trackListWidget.deleteAllChildren();
                File[] midiFiles = new File(maskPath + "/MIDI/").listFiles();
                addCustomMusic(midiFiles, trackListWidget);
            }
        }**/
    }

    private void addCustomMusic(File[] midiFiles, Widget trackListWidget) {
        int songIndex = Objects.requireNonNull(trackListWidget.getChildren()).length;
        int originalY = 0;
        if (midiFiles != null) {
            for (File midi : midiFiles) {
                if (midi.getName().contains(" - ")) {
                    int index = midi.getName().lastIndexOf(" - ");
                    String name = midi.getName().substring(index).replace(".mid", "").replace(" - ", "").trim();

                    Widget trackSlot = trackListWidget.createChild(songIndex++, 4);
                    trackSlot.setHidden(false);
                    trackSlot.setName(name);
                    trackSlot.setText(name);
                    trackSlot.setFontId(495);
                    trackSlot.setTextColor(Objects.requireNonNull(client.getWidget(239, 6)).getTextColor());
                    trackSlot.setTextShadowed(true);
                    trackSlot.setOriginalWidth(154);
                    trackSlot.setOriginalHeight(15);
                    trackSlot.setOpacity(0);
                    trackSlot.setHasListener(true);
                    trackSlot.setOriginalX(0);
                    trackSlot.setOriginalY(originalY);
                    trackSlot.setAction(1, "Play");
                    trackSlot.setOnOpListener((JavaScriptCallback) e -> playCustomSong(name));
                    trackSlot.revalidate();

                    originalY = originalY + 15;
                }
                else {
                    String name = midi.getName().replace(".mid", "").trim();

                    Widget trackSlot = trackListWidget.createChild(songIndex++, 4);
                    trackSlot.setHidden(false);
                    trackSlot.setName(name);
                    trackSlot.setText(name);
                    trackSlot.setFontId(495);
                    trackSlot.setTextColor(Objects.requireNonNull(client.getWidget(239, 6)).getTextColor());
                    trackSlot.setTextShadowed(true);
                    trackSlot.setOriginalWidth(154);
                    trackSlot.setOriginalHeight(15);
                    trackSlot.setOpacity(0);
                    trackSlot.setHasListener(true);
                    trackSlot.setOriginalX(0);
                    trackSlot.setOriginalY(originalY);
                    trackSlot.setAction(1, "Play");
                    trackSlot.setOnOpListener((JavaScriptCallback) e -> playCustomSong(name));
                    trackSlot.revalidate();

                    originalY = originalY + 15;
                }
            }
        }
        trackListWidget.setScrollHeight(originalY + 3);
        trackListWidget.revalidateScroll();
    }

    private void playCustomSong(String name) {

        Widget musicPlayingWidget = client.getWidget(WidgetID.MUSIC_GROUP_ID, 6);

        if (musicPlayingWidget != null) {
            musicPlayingWidget.setName(name);
            musicPlayingWidget.setText(name);
            try {
                fadeTo(name);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Subscribe
    protected void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (client.getGameState().equals(GameState.LOGGED_IN)) {
            currentSong = null;
        }
    }

    @Override
    protected void shutDown()
    {
        initialized = false;

        if (sourceDataLine != null) {
            sourceDataLine.stop();
            sourceDataLine = null;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getKey().equals("musicVersion")) {
            try {
                fadeTo(currentSong);
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
                    fadeTo(currentSong);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void fadeTo(String songName) throws InterruptedException {

        Thread fadeThread = new Thread(() -> {

            File[] midiFiles = new File(maskPath + "/MIDI/").listFiles();
            if (midiFiles != null) {
                for (File midi : midiFiles) {
                    if (midi.getName().contains(" - ")) {
                        int index = midi.getName().lastIndexOf(" - ");
                        String name = midi.getName().substring(index).replace(".mid", "").replace(" - ", "").trim();

                        if (name.equalsIgnoreCase(songName)) {
                            midiFile = midi;
                        }
                    }

                    if (midi.getName().replace(".mid", "").trim().equalsIgnoreCase(songName)) {
                        midiFile = midi;
                    }
                }
            }

            for (SoundPlayer soundPlayer : soundPlayers) {
                int volume = ((MidiPcmStream) soundPlayer.stream).getPcmStreamVolume();
                for (int step = volume; step > -1; step--) {
                    ((MidiPcmStream) soundPlayer.stream).setPcmStreamVolume(step);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            shutDown();
            startUp();
        });

        fadeThread.start();
    }
}
