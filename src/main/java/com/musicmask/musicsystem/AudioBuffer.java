package com.musicmask.musicsystem;

public class AudioBuffer extends AbstractSound
{
    public int sampleRate;
    public byte[] samples;
    public int start;
    public int end;
    boolean enableLoop;

    AudioBuffer(int sRate, byte[] data, int loopStart, int loopEnd)
    {
        sampleRate = sRate;
        samples = data;
        start = loopStart;
        end = loopEnd;
    }

    AudioBuffer(int sRate, byte[] data, int loopStart, int loopEnd, boolean loop)
    {
        sampleRate = sRate;
        samples = data;
        start = loopStart;
        end = loopEnd;
        enableLoop = loop;
    }

}
