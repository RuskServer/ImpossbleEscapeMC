package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.google.gson.*;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.*;

/** オフラインの初回読込と既存の原子的保存・バックアップを通す。 */
public final class QuestlineLoadMigrationTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("iem-quest-migration");var log=Logger.getLogger("QuestlineLoadMigrationTest");log.setLevel(Level.OFF);
        var store=new PlayerDataStorage(dir.toFile(),log);UUID id=UUID.randomUUID();var gson=new Gson();
        var old=new PlayerData(id);old.completeQuest("bastion_night_optics_trial");old.getActiveQuests().put("supply_route",new ActiveQuest("supply_route"));
        var json=gson.toJsonTree(old).getAsJsonObject();json.remove("questlineVersion");json.remove("retainedTradeUnlocks");String original=json.toString();
        Files.writeString(store.mainFile(id).toPath(),original);
        var result=store.load(id);check(PlayerDataModule.migrateLoadedQuests(result),"offline load uses production migration hook");
        check(result.data().isDirty(),"migrated data scheduled for normal persistence");
        check(Files.readString(store.mainFile(id).toPath()).equals(original),"load alone never partially rewrites disk");
        check(store.write(id,1,store.toJson(result.data())),"single atomic snapshot saved");
        check(Files.readString(store.backupDir(id).toPath().resolve("1.json")).equals(original),"pre-reset snapshot backed up");
        var again=store.load(id);check(!PlayerDataModule.migrateLoadedQuests(again),"saved version prevents repeated reset");
        check(again.data().hasRetainedTradeUnlock(QuestlineMigration.tradeKey("bastion","PVS-14",true)),"unlock and reset persisted together");
        var failed=gson.fromJson(original,PlayerData.class);
        check(!PlayerDataModule.migrateLoadedQuests(new PlayerDataStorage.LoadResult(failed,true,false,false))&&!failed.getCompletedQuests().isEmpty(),"failed data is never migrated");
        var created=store.load(UUID.randomUUID());check(!PlayerDataModule.migrateLoadedQuests(created),"new player not migrated");
        try(var paths=Files.walk(dir)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);}
        System.out.println("QuestlineLoadMigrationTest passed: lazy offline load, atomic snapshot, backup and failure/new-player guards");
    }
}
