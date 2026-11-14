package com.lunar_prototype.impossbleEscapeMC.api.message;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;

/** コンパイル済みクラスのメソッド参照を調べ、ラムダ・メソッド参照も含めて直接送信を禁止する。 */
public final class MessageRoutingArchitectureTest {
    record Pair(int first,int second){}
    record ClassRef(int name){}
    record MethodRef(int owner,int type){}
    public static void main(String[] args)throws Exception {
        List<String> violations=new ArrayList<>();int scanned=0;
        try(var paths=Files.walk(Path.of("target/classes"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".class")).toList()) {
                String own=Path.of("target/classes").relativize(path).toString().replace(File.separatorChar,'/');
                if(own.equals("com/lunar_prototype/impossbleEscapeMC/api/message/BukkitMessagePlatform.class"))continue;
                scanned++;
                try(DataInputStream in=new DataInputStream(Files.newInputStream(path))) {
                    if(in.readInt()!=0xCAFEBABE)throw new AssertionError(path);in.readUnsignedShort();in.readUnsignedShort();
                    Object[] cp=new Object[in.readUnsignedShort()];
                    for(int i=1;i<cp.length;i++) {
                        int tag=in.readUnsignedByte();
                        switch(tag){
                            case 1 -> cp[i]=in.readUTF();
                            case 7 -> cp[i]=new ClassRef(in.readUnsignedShort());
                            case 10,11 -> cp[i]=new MethodRef(in.readUnsignedShort(),in.readUnsignedShort());
                            case 12 -> cp[i]=new Pair(in.readUnsignedShort(),in.readUnsignedShort());
                            case 3,4 -> in.readInt();
                            case 5,6 -> {in.readLong();i++;}
                            case 8,16,19,20 -> in.readUnsignedShort();
                            case 9,17,18 -> {in.readUnsignedShort();in.readUnsignedShort();}
                            case 15 -> {in.readUnsignedByte();in.readUnsignedShort();}
                            default -> throw new AssertionError("Unknown constant pool tag "+tag);
                        }
                    }
                    for(Object item:cp)if(item instanceof MethodRef ref) {
                        String owner=(String)cp[((ClassRef)cp[ref.owner]).name];Pair type=(Pair)cp[ref.type];String method=(String)cp[type.first];
                        if(Set.of("broadcast","broadcastMessage").contains(method)&&(owner.equals("org/bukkit/Bukkit")||owner.equals("org/bukkit/Server")))violations.add(own+" -> "+owner+"#"+method);
                        if(Set.of("sendMessage","sendActionBar","showTitle","sendTitle","sendPlainMessage","sendRichMessage").contains(method)) {
                            Class<?> target=Class.forName(owner.replace('/','.'),false,MessageRoutingArchitectureTest.class.getClassLoader());
                            if(Audience.class.isAssignableFrom(target)||CommandSender.class.isAssignableFrom(target))violations.add(own+" -> "+owner+"#"+method);
                        }
                    }
                }
            }
        }
        if(!violations.isEmpty())throw new AssertionError("Direct sends outside message transport: "+violations);
        System.out.println("MessageRoutingArchitectureTest OK: "+scanned+" classes including method references");
    }
}
