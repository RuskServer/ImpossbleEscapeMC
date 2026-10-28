package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.util.ChunkLease;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 診断範囲の既存チャンクを少数ずつ非同期で読む。未来の完了コールバックではワールドを触らない。 */
final class SpawnChunkLoader implements AutoCloseable {
    private record Pending(int x,int z,CompletableFuture<Chunk> future) {}
    private final World world;
    private final Plugin plugin;
    private final int parallel;
    private final List<Pending> pending=new ArrayList<>();
    private final List<ChunkLease> leases=new ArrayList<>();
    private final Set<Long> loaded=new HashSet<>();
    private final int minX,minZ,maxX,maxZ;
    private int x,z,finished,failed;
    private final int total;
    private boolean closed;
    static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    SpawnChunkLoader(World world,Plugin plugin,List<RaidMap.SpawnEntry> entries,int margin,int maxChunks,int parallel) {
        this.world=world;this.plugin=plugin;this.parallel=parallel;
        if(entries.isEmpty()){minX=minZ=0;maxX=maxZ=-1;total=0;x=0;z=0;return;}
        minX=((int)Math.floor(entries.stream().mapToDouble(RaidMap.SpawnEntry::x).min().orElseThrow()-margin))>>4;
        maxX=((int)Math.floor(entries.stream().mapToDouble(RaidMap.SpawnEntry::x).max().orElseThrow()+margin))>>4;
        minZ=((int)Math.floor(entries.stream().mapToDouble(RaidMap.SpawnEntry::z).min().orElseThrow()-margin))>>4;
        maxZ=((int)Math.floor(entries.stream().mapToDouble(RaidMap.SpawnEntry::z).max().orElseThrow()+margin))>>4;
        long count=(long)(maxX-minX+1)*(maxZ-minZ+1);
        if(count>maxChunks)throw new IllegalArgumentException("診断範囲は"+count+"チャンクで、上限"+maxChunks+"を超えます。chunk-margin-blocks / max-chunks を調整してください。");
        total=(int)count;x=minX;z=minZ;
    }
    void step() {
        if(closed)return;
        for(Iterator<Pending> it=pending.iterator();it.hasNext();) {
            Pending p=it.next();
            if(!p.future.isDone())continue;
            it.remove();finished++;
            try {
                Chunk chunk=p.future.join();
                if(chunk!=null && world.isChunkLoaded(p.x,p.z)) {
                    leases.add(ChunkLease.acquire(chunk,plugin));loaded.add(key(p.x,p.z));
                } else failed++;
            } catch(RuntimeException ex) {failed++;}
            break;
        }
        if(x<=maxX && pending.size()<parallel) {
            int cx=x,cz=z;
            // generate=false: マップ外を勝手に生成しない。未生成・失敗は未確定として残す。
            pending.add(new Pending(cx,cz,world.getChunkAtAsync(cx,cz,false)));
            if(++z>maxZ){z=minZ;x++;}
        }
    }
    boolean done(){return finished==total;}
    int total(){return total;}
    int finished(){return finished;}
    int failed(){return failed;}
    boolean available(int x,int z){return loaded.contains(key(x,z));}
    @Override public void close(){
        if(closed)return;closed=true;
        for(ChunkLease lease:leases)lease.close();
        leases.clear();pending.clear();loaded.clear();
    }
}
