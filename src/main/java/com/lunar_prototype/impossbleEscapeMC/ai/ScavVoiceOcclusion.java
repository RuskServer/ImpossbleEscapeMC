package com.lunar_prototype.impossbleEscapeMC.ai;

/** 発声時だけ行う声の遮蔽判定。高さによる分岐はせず、当たった床・壁の数で音を選ぶ。 */
public final class ScavVoiceOcclusion {
    private ScavVoiceOcclusion() {}
    public enum Level {
        DIRECT("",1f), MUFFLED(".muffled",0.60f), DEEP(".deep",0.30f);
        private final String suffix; private final float volume;
        Level(String suffix,float volume){this.suffix=suffix;this.volume=volume;}
        public String suffix(){return suffix;}
        public float volume(){return volume;}
    }
    public record Point(double x,double y,double z) {
        Point offset(Point direction,double distance){return new Point(x+direction.x*distance,y+direction.y*distance,z+direction.z*distance);}
    }
    /** 最初に実衝突したブロックのワールド座標での包絡箱。 */
    public record Obstacle(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    public interface Geometry {
        boolean loaded(int chunkX,int chunkZ);
        Obstacle firstHit(Point origin,Point direction,double distance);
    }

    /** 頭の中心と左右の小さなずれを使い、柵・窓枠の端の1本だけで全てを遮蔽しない。 */
    public static Level classify(Point mouth,Point ears,Geometry geometry) {
        double dx=ears.x-mouth.x,dz=ears.z-mouth.z,flat=Math.hypot(dx,dz);
        Point lateral=flat<1e-6?new Point(.12,0,0):new Point(-dz/flat*.12,0,dx/flat*.12);
        int[] counts={barriers(mouth,ears,geometry),
                barriers(mouth,new Point(ears.x+lateral.x,ears.y,ears.z+lateral.z),geometry),
                barriers(mouth,new Point(ears.x-lateral.x,ears.y,ears.z-lateral.z),geometry)};
        java.util.Arrays.sort(counts);
        return Level.values()[counts[1]];
    }

    static int barriers(Point from,Point to,Geometry geometry) {
        if (!loadedPath(from,to,geometry)) return 2; // 未読込を調べるためにチャンクを生成・ロードしない。
        double dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z;
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(distance<.01)return 0;
        Point direction=new Point(dx/distance,dy/distance,dz/distance);
        double at=0;
        for(int count=0;count<2;count++) {
            Obstacle hit=geometry.firstHit(from.offset(direction,at),direction,distance-at);
            if(hit==null)return count;
            double exit=Math.min(exit(from.x,direction.x,hit.minX,hit.maxX),
                    Math.min(exit(from.y,direction.y,hit.minY,hit.maxY),exit(from.z,direction.z,hit.minZ,hit.maxZ)));
            if(!Double.isFinite(exit)||exit<at)return 2;
            at=exit+.001;
            if(at>=distance)return count+1;
        }
        return 2;
    }

    private static double exit(double origin,double direction,double low,double high) {
        return Math.abs(direction)<1e-10?Double.POSITIVE_INFINITY:((direction>0?high:low)-origin)/direction;
    }

    /** 16ブロック格子のDDA。rayTraceBlocksより先に経路の全チャンクを確認する。 */
    private static boolean loadedPath(Point from,Point to,Geometry geometry) {
        int x=(int)Math.floor(from.x/16),z=(int)Math.floor(from.z/16);
        int endX=(int)Math.floor(to.x/16),endZ=(int)Math.floor(to.z/16);
        double dx=to.x-from.x,dz=to.z-from.z;
        int sx=dx>0?1:dx<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*16.0:x*16.0)-from.x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*16.0:z*16.0)-from.z)/dz;
        double stepX=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx);
        double stepZ=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
        for(int i=0;i<32;i++) {
            if(!geometry.loaded(x,z))return false;
            if(x==endX&&z==endZ)return true;
            if(Math.abs(tx-tz)<1e-10) {
                if(!geometry.loaded(x+sx,z)||!geometry.loaded(x,z+sz))return false;
                x+=sx;z+=sz;tx+=stepX;tz+=stepZ;
            } else if(tx<tz){x+=sx;tx+=stepX;}
            else{z+=sz;tz+=stepZ;}
        }
        return false;
    }
}
