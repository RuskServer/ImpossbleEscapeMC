package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import java.util.List;
import static com.lunar_prototype.impossbleEscapeMC.ai.ScavVoiceOcclusion.*;

public final class ScavVoiceOcclusionTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Scene implements Geometry {
        final List<BoundingBox> boxes;int rays;int chunks;boolean available=true;
        Scene(BoundingBox...boxes){this.boxes=List.of(boxes);}
        @Override public boolean loaded(int x,int z){chunks++;return available;}
        @Override public Obstacle firstHit(Point p,Point d,double distance){
            rays++;BoundingBox nearest=null;double best=Double.POSITIVE_INFINITY;
            Vector start=new Vector(p.x(),p.y(),p.z()),direction=new Vector(d.x(),d.y(),d.z());
            for(var box:boxes){var hit=box.rayTrace(start,direction,distance);if(hit==null)continue;
                double at=hit.getHitPosition().distanceSquared(start);if(at<best){best=at;nearest=box;}}
            return nearest==null?null:new Obstacle(nearest.getMinX(),nearest.getMinY(),nearest.getMinZ(),nearest.getMaxX(),nearest.getMaxY(),nearest.getMaxZ());
        }
    }
    public static void main(String[] args){
        Point source=new Point(0,1.4,0),same=new Point(4,1.6,0);
        check(classify(source,same,new Scene())==Level.DIRECT,"open same floor");
        check(classify(source,new Point(0,8,0),new Scene())==Level.DIRECT,"height alone never muffles an atrium");
        check(classify(source,new Point(0,5.6,0),new Scene(new BoundingBox(-5,3,-5,5,3.5,5)))==Level.MUFFLED,"single thin floor muffles above");
        check(classify(source,new Point(0,-3,0),new Scene(new BoundingBox(-5,-1,-5,5,0,5)))==Level.MUFFLED,"single floor muffles below");
        check(classify(source,same,new Scene(new BoundingBox(2,0,-2,3,3,2)))==Level.MUFFLED,"wall is also an obstruction");
        Scene thick=new Scene(new BoundingBox(1,0,-2,2,3,2),new BoundingBox(2,0,-2,3,3,2));
        check(classify(source,same,thick)==Level.DEEP,"adjacent blocks count separately");
        check(thick.rays<=6,"bounded six collision queries per listener");
        check(classify(source,new Point(0,5.6,0),new Scene(new BoundingBox(-5,3,-5,-.3,4,5),new BoundingBox(.3,3,-5,5,4,5)))==Level.DIRECT,"opening in floor carries sound");
        check(classify(source,same,new Scene(new BoundingBox(1.9,0,-.02,2.1,3,.02)))==Level.DIRECT,"one clipped probe does not muffle all sound");
        check(classify(source,same,new Scene(new BoundingBox(1.9,0,.03,2.1,3,2),new BoundingBox(1.9,0,-2,2.1,3,-.03)))==Level.MUFFLED,"mostly obstructed probes remain muffled");
        Scene unloaded=new Scene();unloaded.available=false;
        check(classify(source,same,unloaded)==Level.DEEP&&unloaded.rays==0,"unloaded chunks never raycast or load");
        Scene negative=new Scene();
        check(classify(new Point(-32,1.4,-32),new Point(-.1,1.6,-.1),negative)==Level.DIRECT,"negative chunk boundaries and diagonal path");
        check(negative.chunks<60,"bounded chunk traversal");
        check(classify(source,source,new Scene())==Level.DIRECT,"zero distance is valid");
        for(ScavVoice voice:ScavVoice.values())for(int take=0;take<ScavVoice.TAKES.size();take++){
            String direct=voice.sound(take,Level.DIRECT);
            check(voice.sound(take,Level.MUFFLED).equals(direct+".muffled")&&voice.sound(take,Level.DEEP).equals(direct+".deep"),"same take across acoustic variants");
        }
        check(ScavVoice.IDLE.range()==20&&ScavVoice.HEARD.range()==20&&ScavVoice.SPOTTED.range()==40,"voice ranges preserved");
        System.out.println("PASS: floor/wall/slab/opening and partial occlusion, loaded chunks, query bounds and voice take mapping");
    }
}
