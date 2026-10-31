package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
import java.util.Arrays;
import java.util.Locale;

/** 旧座標形式を保った、管理者の明示的なAIレベル指定。nullは従来のLOW/MID抽選。 */
record ScavSpawnArguments(ScavBrain.BrainLevel level,String[] coordinates) {
    static ScavBrain.BrainLevel rank(String value) {
        try{return ScavBrain.BrainLevel.valueOf(value.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException ex){return null;}
    }
    static ScavSpawnArguments parse(String[] args) {
        if(args.length==0)return new ScavSpawnArguments(null,new String[0]);
        ScavBrain.BrainLevel level=rank(args[0]);String[] coordinates;
        if(level!=null) {
            if(args.length!=1&&args.length!=4)throw new IllegalArgumentException("Invalid argument count");
            coordinates=Arrays.copyOfRange(args,1,args.length);
        }else if(args.length==4&&(level=rank(args[3]))!=null)coordinates=Arrays.copyOf(args,3);
        else if(args.length==3)coordinates=args.clone();
        else throw new IllegalArgumentException("Unknown AI level or argument count");
        for(String c:coordinates)coordinate(0,c);
        return new ScavSpawnArguments(level,coordinates);
    }
    static double coordinate(double base,String input) {
        double value=input.startsWith("~")?(input.length()==1?base:base+Double.parseDouble(input.substring(1))):Double.parseDouble(input);
        if(!Double.isFinite(value))throw new IllegalArgumentException("Coordinate must be finite");
        return value;
    }
}
