package me.modernadventurer.antilavaplace;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class MyListener implements Listener {

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if(event.getBucket() == Material.LAVA_BUCKET) {
            event.setCancelled(true);
            event.getPlayer().getInventory().getItemInMainHand().setType(Material.BUCKET);
        }
    }
}
