package me.modernadventurer.aquaticplayers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

public class WaterBreathingListener implements Listener {

    private final AquaticPlayers plugin;

    public WaterBreathingListener(AquaticPlayers plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onGameTickEvent(GameTickEvent event) {
        int tick = event.getGameTick();
        for(World world : Bukkit.getWorlds()) {
            for(Player player : world.getPlayers()) {
                if(!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {

                    if(!player.getPersistentDataContainer().has(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER)) {
                        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, 300);
                    }
                    // When the player is no longer submerged in water or is in a bubble column
                    if(!isUnderwater(player) || inWaterColumn(player)) {
                        // the air supply value decreases each tick. (USED TO BE -1 PER TICK, 10X FASTER)
                        if(!player.hasPotionEffect(PotionEffectType.WATER_BREATHING)) {
                            player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER) - 10);
                        }
                        // Respiration gives a chance for air supply to not decrease itself per tick.
                        // Chance is x/(x + 1), where x is the level of enchantment.
                        if(player.getInventory().getHelmet()!=null) {
                            ItemStack helmet = player.getInventory().getHelmet();
                            if(helmet.getEnchantments().containsKey(Enchantment.OXYGEN)) {
                                int respirationLevel = helmet.getEnchantments().get(Enchantment.OXYGEN);
                                if(Math.random()>(respirationLevel/(respirationLevel+10))) {
                                    player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER) + 10);
                                }
                            }
                        }
                        // when the air supply value reaches -100 (-10 per tick, 10 ticks). (USED TO BE -20, -1 per tick, 20 ticks)
                        if(player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER) <= -100) {
                            // Air resets to 0
                            player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, 0);
                            // 2 Damage is taken
                            if(player.getHealth() > 0) {
                                world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 1, 1);
                                player.damage(2);
                            }
                        }
                    } else {
                        // every 0.2 seconds (4 game ticks)
                        if(tick % 4 == 0) {
                            // their oxygen regenerates by 1 bubble (30 air)
                            player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER)+30);
                            if(player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER) > 300) {
                                player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, 300);
                            }
                        }
                    };
                    int airDisplay = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER);
                    if(airDisplay >= 300) {
                        airDisplay = -10;
                    }
                    if(airDisplay <= 0) {
                        airDisplay = -10;
                    }
                    player.setRemainingAir(airDisplay);
                }
            }
        }
    }

    @EventHandler
    public void onRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "Oxygen"), PersistentDataType.INTEGER, 300);
    }

    public Boolean isUnderwater(Player player) {
        BlockData headBlock = player.getEyeLocation().getBlock().getBlockData();
        return headBlock.getMaterial().equals(Material.WATER) || (headBlock instanceof Waterlogged && ((Waterlogged) headBlock).isWaterlogged());
    }


    public Boolean inWaterColumn(Player player) {
        Location location = player.getEyeLocation();
        if(isWaterSource(location.getBlock())) {
            while(location.getBlockY()>0) {
                location.add(0, -1, 0);
                if(location.getBlock().getType().equals(Material.SOUL_SAND) || location.getBlock().getType().equals(Material.MAGMA_BLOCK)) {
                    return true;
                }
                if(!isWaterSource(location.getBlock())) {
                    return false;
                }
            }
        }
        return false;
    }

    public Boolean isWaterSource(Block block) {
        if(block.getType() == Material.WATER) {
            BlockData blockData = block.getBlockData();
            if(blockData instanceof Levelled){
                Levelled lv = (Levelled)blockData;
                if(lv.getLevel() == lv.getMaximumLevel()) {
                    return true;
                }
            }
        }
        return false;
    }
}
