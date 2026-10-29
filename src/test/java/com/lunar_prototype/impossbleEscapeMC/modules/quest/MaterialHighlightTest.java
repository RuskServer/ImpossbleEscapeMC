package com.lunar_prototype.impossbleEscapeMC.modules.quest;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutMaterialNeeds;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import java.util.Map;
public final class MaterialHighlightTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String text(ItemNeedPresentation.Decoration d){return d.lines().stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("",(a,b)->a+"\n"+b);}
    public static void main(String[] args){
        var use=List.of(new HideoutMaterialNeeds.Use("ワークベンチ",2,4,false),new HideoutMaterialNeeds.Use("医療設備",1,2,true));
        var missing=new HideoutMaterialNeeds.Need(use,6,2);
        var secured=new HideoutMaterialNeeds.Need(use,6,6);
        var quest=new ItemNeedPresentation.Need("工具集め",new HandInObjective("bolts",null,3,true),3);
        var both=new ItemNeedPresentation.Snapshot(List.of(quest),Map.of("bolts",missing));
        var d=ItemNeedPresentation.describe("bolts","LOOT",false,both);
        check(d.glow()&&text(d).contains("FIR 品のみ")&&text(d).contains("不足 4")&&text(d).contains("前提設備待ち"),"non-FIR can serve hideout but not FIR quest");
        check(d.lines().stream().anyMatch(l->NamedTextColor.AQUA.equals(l.color())),"hideout aqua marker");
        var done=new ItemNeedPresentation.Snapshot(List.of(quest),Map.of("bolts",secured));
        check(!ItemNeedPresentation.describe("bolts","LOOT",false,done).glow(),"secured material and ineligible quest do not glow");
        d=ItemNeedPresentation.describe("bolts","LOOT",true,done);
        check(d.glow()&&text(d).contains("確保済み")&&text(d).contains("★ クエスト"),"quest glow retained after hideout secured");
        check(d.lines().stream().anyMatch(l->NamedTextColor.GOLD.equals(l.color())),"quest gold retained");
        check(ItemNeedPresentation.describe("bolts",null,false,new ItemNeedPresentation.Snapshot(List.of(),Map.of("bolts",missing))).glow(),"hideout match needs no quest type or FIR");
        check(ItemNeedPresentation.describe("ram","LOOT",true,both).lines().isEmpty(),"unrelated item unchanged");
        check(!ItemNeedPresentation.describe("bolts","LOOT",true,new ItemNeedPresentation.Snapshot(List.of(),Map.of())).glow(),"completed upgrade removes old need");
        System.out.println("PASS: combined quest/hideout text, color, FIR separation, secured glint and unrelated items");
    }
}
