package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;

public final class ScavSpawnArgumentsTest {
    public static void main(String[] args) {
        check(ScavSpawnArguments.parse(new String[0]).level()==null,"default rank remains random");
        for(var level:ScavBrain.BrainLevel.values()) {
            var plain=ScavSpawnArguments.parse(new String[]{level.name().toLowerCase()});
            check(plain.level()==level&&plain.coordinates().length==0,"rank at executor position");
            var first=ScavSpawnArguments.parse(new String[]{level.name(),"~","~-2","100"});
            var last=ScavSpawnArguments.parse(new String[]{"~","~-2","100",level.name().toLowerCase()});
            check(first.level()==level&&last.level()==level&&java.util.Arrays.equals(first.coordinates(),last.coordinates()),"both coordinate syntaxes");
        }
        check(ScavSpawnArguments.parse(new String[]{"1","-34","2"}).level()==null,"old coordinate-only syntax");
        check(ScavSpawnArguments.coordinate(10,"~")==10&&ScavSpawnArguments.coordinate(10,"~-2.5")==7.5,"relative coordinates");
        check(ScavSpawnArguments.coordinate(10,"-34.5")==-34.5,"absolute coordinates");
        for(String[] invalid:new String[][]{{"hgh"},{"low","1"},{"1","2"},{"high","1","2","3","4"},{"1","2","3","nope"},{"low","NaN","1","2"},{"1","Infinity","2"},{"~NaN","1","2"},{"heatmap"}}) {
            try{ScavSpawnArguments.parse(invalid);throw new AssertionError("invalid arguments accepted");}catch(IllegalArgumentException expected){}
        }
        System.out.println("ScavSpawnArgumentsTest passed: all ranks, old/new coordinate syntax, relative coordinates and rejection before spawning");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
