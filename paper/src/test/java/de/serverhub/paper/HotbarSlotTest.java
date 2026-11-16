package de.serverhub.paper;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class HotbarSlotTest {
    @Test void fullInventoryNeverOverwritesOrDropsAnything(){
        PlayerInventory inv=mock(PlayerInventory.class);ItemStack item=mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);when(inv.getItem(anyInt())).thenReturn(item);
        assertFalse(HotbarSlot.makeSpace(inv,8));verify(inv,never()).setItem(anyInt(),any());
    }
    @Test void occupiedSlotIsPreservedInAnEmptyStorageSlot(){
        PlayerInventory inv=mock(PlayerInventory.class);ItemStack item=mock(ItemStack.class),copy=mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);when(item.clone()).thenReturn(copy);
        when(inv.getItem(anyInt())).thenReturn(item);when(inv.getItem(12)).thenReturn(null);
        assertTrue(HotbarSlot.makeSpace(inv,8));verify(inv).setItem(12,copy);verify(inv,never()).setItem(eq(8),any());
    }
}
