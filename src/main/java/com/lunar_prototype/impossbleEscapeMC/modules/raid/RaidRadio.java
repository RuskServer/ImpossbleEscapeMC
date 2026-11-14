package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** クライアントの短い私的な無線。音・字幕・コンパス下の区域演出を同じ状態から進める。 */
final class RaidRadio {
    private static final String ITEM="iem:radio.quest_item_found",ZONE="iem:radio.high_threat";
    private final ImpossbleEscapeMC plugin;
    private final RaidMap map;
    private final boolean enabled;
    private final Map<UUID,RaidRadioState> states=new HashMap<>();
    private final Map<UUID,String> zoneNames=new HashMap<>();
    RaidRadio(ImpossbleEscapeMC plugin,RaidMap map) {
        this.plugin=plugin;this.map=map;enabled=plugin.getConfig().getBoolean("raid-radio.enabled",true);
    }
    private RaidRadioState state(Player p){return states.computeIfAbsent(p.getUniqueId(),id->new RaidRadioState());}
    void tick(Player p) {
        if(!enabled)return;
        if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!map.isWorld(p.getWorld())){remove(p);return;}
        if(com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService.isPlaying(p,com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService.PDA_INTRO))return;
        int now=Bukkit.getCurrentTick();var state=state(p);var at=p.getLocation();
        List<RaidRadioZone> zones=map.highThreatZones();
        RaidRadioZone zone=null;
        for(var candidate:zones)if(candidate.id().equals(state.zone())&&candidate.contains(at.getX(),at.getY(),at.getZ(),2)){zone=candidate;break;}
        if(zone==null)for(var candidate:zones)if(candidate.contains(at.getX(),at.getY(),at.getZ(),0)){zone=candidate;break;}
        if(state.observeZone(zone==null?null:zone.id(),now)) {
            zoneNames.put(p.getUniqueId(),zone.name());
            Messages.chat(p, "iem.raid.raidradio.tick.notice_001", MessageText.of("iem.raid.raidradio.tick.notice_001", MessageText.payload(MessageText.of("iem.raid.raidradio.tick.notice_001.text_01", zone.name()).color(NamedTextColor.GOLD))));
        }
        if(state.takeZone(now))play(p,ZONE,"そこは交戦報告の多い区域だ。周囲をよく確認しろ。");
        else if(state.takeItem(now))play(p,ITEM,"それが探してたものだ。なくさずに持って帰ってくれ。");
    }
    void discovered(Player p,ItemStack item) {
        if(!enabled||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!map.isWorld(p.getWorld()))return;
        PlayerDataModule players=plugin.getServiceContainer().get(PlayerDataModule.class);
        QuestModule quests=plugin.getServiceContainer().get(QuestModule.class);
        if(players==null||quests==null)return;
        var data=players.getPlayerData(p.getUniqueId());
        // 検索中のコンテナ品は、まだ取得時のFIR処理を受けていない。実物は変更せず取得後の資格で確認する。
        ItemStack eligible=item.clone();
        com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory.applyFIR(eligible);
        if(data!=null&&QuestAutoHandIn.usable(data,quests,eligible))state(p).discover(QuestAutoHandIn.itemIdOf(eligible),Bukkit.getCurrentTick());
    }
    private void play(Player p,String sound,String subtitle) {
        p.playSound(p,sound,SoundCategory.MASTER,0.9f,1.0f);
        Messages.chat(p, "iem.raid.raidradio.play.notice_001", MessageText.of("iem.raid.raidradio.play.notice_001", MessageText.payload(MessageText.of("iem.raid.raidradio.play.notice_001.text_01").color(NamedTextColor.GRAY).append(Component.text(subtitle,NamedTextColor.WHITE)))));
    }
    void urgent(Player p,int duration) {
        if(!enabled)return;
        p.stopSound(ITEM,SoundCategory.MASTER);p.stopSound(ZONE,SoundCategory.MASTER);
        state(p).urgent(Bukkit.getCurrentTick(),duration);
    }
    int frame(Player p){if(!enabled)return -1;var state=states.get(p.getUniqueId());return state==null?-1:state.frame(Bukkit.getCurrentTick());}
    String zoneName(Player p){return zoneNames.get(p.getUniqueId());}
    void preview(Player p,String kind) {
        if(!enabled){Messages.chat(p, "iem.raid.raidradio.preview.notice_001", MessageText.of("iem.raid.raidradio.preview.notice_001", MessageText.payload(MessageText.of("iem.raid.raidradio.preview.notice_001.text_01").color(NamedTextColor.YELLOW))));return;}
        if(kind.equals("zone")){zoneNames.put(p.getUniqueId(),"区域表示テスト");state(p).previewZone(Bukkit.getCurrentTick());}
        else state(p).discover("preview-"+Bukkit.getCurrentTick(),Bukkit.getCurrentTick());
    }
    void clear(){states.clear();zoneNames.clear();}
    void remove(Player p) {
        zoneNames.remove(p.getUniqueId());
        var state=states.get(p.getUniqueId());if(state!=null)state.suspend();
        p.stopSound(ITEM,SoundCategory.MASTER);p.stopSound(ZONE,SoundCategory.MASTER);
    }
}
