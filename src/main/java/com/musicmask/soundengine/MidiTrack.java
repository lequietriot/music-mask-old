package com.musicmask.soundengine;

/**
 * MIDI File class from Jagex's custom MIDI Player Engine, refactored.
 * @author Rodolfo Ruiz-Velasco (https://github.com/lequietriot)
 * @author Vincent (MIDI Encoder, from Rune-Server)
 */
public class MidiTrack extends AudioNode {

    public static NodeHashTable table;
    public static byte[] midi;

    public MidiTrack() {

    }

    public static void loadMidiTrackInfo() {
        if (table == null) {
            table = new NodeHashTable(16);
            int[] var1 = new int[16];
            int[] var2 = new int[16];
            var2[9] = 128;
            var1[9] = 128;
            MidiFileReader var4 = new MidiFileReader(midi);
            int var5 = var4.trackCount();

            int var6;
            for (var6 = 0; var6 < var5; ++var6) {
                var4.gotoTrack(var6);
                var4.readTrackLength(var6);
                var4.markTrackPosition(var6);
            }

            label53:
            do {
                while (true) {
                    var6 = var4.getPrioritizedTrack();
                    int var7 = var4.trackLengths[var6];

                    while (var7 == var4.trackLengths[var6]) {
                        var4.gotoTrack(var6);
                        int var8 = var4.getMessage(var6);
                        if (var8 == 1) {
                            var4.setTrackDone();
                            var4.markTrackPosition(var6);
                            continue label53;
                        }

                        int var9 = var8 & 240;
                        int var10;
                        int var11;
                        int var12;
                        if (var9 == 176) {
                            var10 = var8 & 15;
                            var11 = var8 >> 8 & 127;
                            var12 = var8 >> 16 & 127;
                            if (var11 == 0) {
                                var1[var10] = (var12 << 14) + (var1[var10] & -2080769);
                            }

                            if (var11 == 32) {
                                var1[var10] = (var1[var10] & -16257) + (var12 << 7);
                            }
                        }

                        if (var9 == 192) {
                            var10 = var8 & 15;
                            var11 = var8 >> 8 & 127;
                            var2[var10] = var11 + var1[var10];
                        }

                        if (var9 == 144) {
                            var10 = var8 & 15;
                            var11 = var8 >> 8 & 127;
                            var12 = var8 >> 16 & 127;
                            if (var12 > 0) {
                                int var13 = var2[var10];
                                ByteArrayNode var14 = (ByteArrayNode) table.get(var13);
                                if (var14 == null) {
                                    var14 = new ByteArrayNode(new byte[128]);
                                    table.put(var14, var13);
                                }

                                var14.byteArray[var11] = 1;
                            }
                        }

                        var4.readTrackLength(var6);
                        var4.markTrackPosition(var6);
                    }
                }
            } while(!var4.isDone());

        }
    }

    public static void clear() {
        table = null;
    }
}
