package com.lunar_prototype.impossbleEscapeMC.loot.event;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class LootItemRevealEventTest {
    @Test void searchAndTransferRemainDistinctWithoutSharingMutableItems() {
        MockBukkit.mock();
        try {
            var item=new ItemStack(Material.PAPER,2);
            var search=new LootItemRevealEvent(null,item);
            var transfer=new LootItemRevealEvent(null,item,false);
            assertTrue(search.isSearchCompletion());
            assertFalse(transfer.isSearchCompletion());
            item.setAmount(1);
            assertEquals(2,search.getItem().getAmount());
            search.getItem().setAmount(3);
            assertEquals(2,search.getItem().getAmount());
            assertEquals(2,transfer.getItem().getAmount());
        } finally { MockBukkit.unmock(); }
    }
}
