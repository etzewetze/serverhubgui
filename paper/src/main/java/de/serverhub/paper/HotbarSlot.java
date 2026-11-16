package de.serverhub.paper;
import org.bukkit.Material;
import org.bukkit.inventory.*;
final class HotbarSlot {
    private HotbarSlot(){}
    /** Caller replaces the target only if this returns true. Never drops an item. */
    static boolean makeSpace(PlayerInventory inv,int target){
        ItemStack occupied=inv.getItem(target);
        if(empty(occupied))return true;
        for(int i=0;i<36;i++)if(i!=target&&empty(inv.getItem(i))){
            inv.setItem(i,occupied.clone());return true;
        }
        return false;
    }
    private static boolean empty(ItemStack item){
        return item==null||item.getType()==Material.AIR||item.getType()==Material.CAVE_AIR||item.getType()==Material.VOID_AIR;
    }
}
