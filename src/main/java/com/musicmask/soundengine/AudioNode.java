package com.musicmask.soundengine;

public class AudioNode
{
    public long key;
    public AudioNode previous;
    public AudioNode next;

    public void remove()
    {
        if(this.next != null)
        {
            this.next.previous = this.previous;
            this.previous.next = this.next;
            this.previous = null;
            this.next = null;
        }

    }

    public boolean hasNext() {
        return this.next != null;
    }
}
