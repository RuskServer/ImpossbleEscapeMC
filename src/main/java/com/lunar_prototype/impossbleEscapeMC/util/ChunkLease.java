package com.lunar_prototype.impossbleEscapeMC.util;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** メインスレッド専用。同じプラグインの保持要求を数え、最後の利用者だけが解除する。 */
public final class ChunkLease implements AutoCloseable {
    private record Key(Plugin plugin, UUID world, int x, int z) {}
    private static final class Entry {
        final World world; final boolean owned; int count;
        Entry(World world, boolean owned) { this.world=world; this.owned=owned; }
    }
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private final Key key;
    private boolean closed;
    private ChunkLease(Key key) { this.key=key; }
    public static ChunkLease acquire(Chunk chunk, Plugin plugin) {
        World world=chunk.getWorld();
        if (!world.isChunkLoaded(chunk.getX(),chunk.getZ())) throw new IllegalStateException("Chunk is not loaded");
        Key key=new Key(plugin,world.getUID(),chunk.getX(),chunk.getZ());
        Entry entry=ENTRIES.get(key);
        if (entry==null) {
            boolean existing=world.getPluginChunkTickets(key.x,key.z).contains(plugin);
            if (!existing) world.addPluginChunkTicket(key.x,key.z,plugin);
            entry=new Entry(world,!existing); ENTRIES.put(key,entry);
        }
        entry.count++;
        return new ChunkLease(key);
    }
    @Override public void close() {
        if (closed) return;
        closed=true;
        Entry entry=ENTRIES.get(key);
        if (entry!=null && --entry.count==0) {
            ENTRIES.remove(key);
            if (entry.owned) entry.world.removePluginChunkTicket(key.x,key.z,key.plugin);
        }
    }
}
