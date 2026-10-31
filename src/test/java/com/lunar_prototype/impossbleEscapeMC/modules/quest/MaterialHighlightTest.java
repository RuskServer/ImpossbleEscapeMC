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
        check(d.glow()&&text(d).contains("FIR必須・この品は対象外")&&text(d).contains("不足 4")&&text(d).contains("前提設備待ち"),"non-FIR can serve hideout but not FIR quest");
        check(d.lines().stream().anyMatch(l->NamedTextColor.AQUA.equals(l.color())),"hideout aqua marker");
        var done=new ItemNeedPresentation.Snapshot(List.of(quest),Map.of("bolts",secured));
        check(!ItemNeedPresentation.describe("bolts","LOOT",false,done).glow(),"secured material and ineligible quest do not glow");
        d=ItemNeedPresentation.describe("bolts","LOOT",true,done);
        check(d.glow()&&text(d).contains("確保済み")&&text(d).contains("クエスト納品"),"quest glow retained after hideout secured");
        check(d.lines().stream().anyMatch(l->NamedTextColor.GOLD.equals(l.color())),"quest gold retained");
        check(ItemNeedPresentation.describe("bolts",null,false,new ItemNeedPresentation.Snapshot(List.of(),Map.of("bolts",missing))).glow(),"hideout match needs no quest type or FIR");
        check(ItemNeedPresentation.describe("ram","LOOT",true,both).lines().isEmpty(),"unrelated item unchanged");
        check(!ItemNeedPresentation.describe("bolts","LOOT",true,new ItemNeedPresentation.Snapshot(List.of(),Map.of())).glow(),"completed upgrade removes old need");
        var orderA=new ItemNeedPresentation.Snapshot(List.of(
                new ItemNeedPresentation.Need("Bの依頼",new HandInObjective("bolts",null,2,false),2),quest),Map.of("bolts",missing));
        var orderB=new ItemNeedPresentation.Snapshot(List.of(quest,
                orderA.quests().stream().filter(n->n.questName().equals("Bの依頼")).findFirst().orElseThrow()),Map.of("bolts",missing));
        check(text(ItemNeedPresentation.describe("bolts","LOOT",true,orderA))
                .equals(text(ItemNeedPresentation.describe("bolts","LOOT",true,orderB))),"quest map order cannot shuffle tooltip lines");
        d=ItemNeedPresentation.describe("bolts","LOOT",true,both);
        var lines=d.lines().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
        check(lines.getFirst().equals("クエスト納品")&&lines.contains("隠れ家強化"),"one matching heading for each use");
        check(lines.stream().filter(String::isEmpty).count()==1,"one separator between the two uses");
        check(lines.contains("  工具集め  残り 3個")&&lines.contains("  ワークベンチ Lv.2  必要 4個"),"shared indented name/count rows");
        check(lines.contains("  確保 2 / 6個  不足 4個"),"stock summary does not confuse delivered quest progress with stored materials");
        check(use.size()==2&&missing.have()==2&&quest.remaining()==3,"presentation never mutates demand or progress");
        System.out.println("PASS: unified headings/rows, stable order, FIR separation, secured glint, stock and unchanged demands");
    }
}
