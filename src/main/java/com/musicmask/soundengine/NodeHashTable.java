package com.musicmask.soundengine;

public final class NodeHashTable {

   int size;
   AudioNode[] buckets;
   AudioNode currentGet;
   AudioNode current;
   int index;

   public NodeHashTable(int var1) {
      this.index = 0;
      this.size = var1;
      this.buckets = new AudioNode[var1];

      for(int var2 = 0; var2 < var1; ++var2) {
         AudioNode var3 = this.buckets[var2] = new AudioNode();
         var3.previous = var3;
         var3.next = var3;
      }

   }

   public AudioNode get(long var1) {
      AudioNode var3 = this.buckets[(int)(var1 & (long)(this.size - 1))];

      for(this.currentGet = var3.previous; var3 != this.currentGet; this.currentGet = this.currentGet.previous) {
         if(this.currentGet.key == var1) {
            AudioNode var4 = this.currentGet;
            this.currentGet = this.currentGet.previous;
            return var4;
         }
      }

      this.currentGet = null;
      return null;
   }

   public void put(AudioNode var1, long var2) {
      if(var1.next != null) {
         var1.remove();
      }

      AudioNode var4 = this.buckets[(int)(var2 & (long)(this.size - 1))];
      var1.next = var4.next;
      var1.previous = var4;
      var1.next.previous = var1;
      var1.previous.next = var1;
      var1.key = var2;
   }

   public void clear() {
      for(int var1 = 0; var1 < this.size; ++var1) {
         AudioNode var2 = this.buckets[var1];

         while(true) {
            AudioNode var3 = var2.previous;
            if(var3 == var2) {
               break;
            }

            var3.remove();
         }
      }

      this.currentGet = null;
      this.current = null;
   }

   public AudioNode first() {
      this.index = 0;
      return this.next();
   }

   public AudioNode next() {
      AudioNode var1;
      if(this.index > 0 && this.buckets[this.index - 1] != this.current) {
         var1 = this.current;
         this.current = var1.previous;
         return var1;
      } else {
         while(this.index < this.size) {
            var1 = this.buckets[this.index++].previous;
            if(var1 != this.buckets[this.index - 1]) {
               this.current = var1.previous;
               return var1;
            }
         }

         return null;
      }
   }
}
