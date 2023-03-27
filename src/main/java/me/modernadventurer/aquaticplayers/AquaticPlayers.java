package me.modernadventurer.aquaticplayers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.Math.min;

public final class AquaticPlayers extends JavaPlugin {

    ProtocolManager protocolManager;
    AquaticPlayers plugin;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        getServer().getPluginManager().registerEvents(new WaterBreathingListener(this), this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        registerBlockBreakListener();

        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            int tick = 0;
            public void run() {
                Bukkit.getServer().getPluginManager().callEvent(new GameTickEvent(tick));
                tick += 1;
            }
        }, 0L, 1L);
    }

    private void registerBlockBreakListener() {
        Map<UUID, BukkitRunnable> breakTasks = new HashMap<>();
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.HIGHEST, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                World world = event.getPlayer().getWorld();
                BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);
                Location loc = pos.toLocation(world);

                if (!WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(WorldGuardPlugin.inst().wrapPlayer(player), BukkitAdapter.adapt(world))) {
                    // If the player doesn't have WorldGuard bypass permission in this world, check if they can break the block
                    if (!WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().testBuild(BukkitAdapter.adapt(loc), WorldGuardPlugin.inst().wrapPlayer(player), Flags.BUILD)) {
                        // If the block is not allowed to be broken by the player, cancel the event
                        return;
                    }
                }

                PacketContainer packet = event.getPacket();
                EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().getValues().get(0);
                Block block = loc.getBlock();
                if(digType.equals(EnumWrappers.PlayerDigType.START_DESTROY_BLOCK)) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    BukkitRunnable breakTask = new BukkitRunnable() {
                        float breakProgress = 0;

                        final Sound breakSound = block.getBlockData().getSoundGroup().getBreakSound();

                        @Override
                        public void run() {
                            breakProgress += getBreakSpeedIgnoreWater(player, block);
                            sendBlockBreakAnimation(player, loc, getBreakStage(breakProgress));
                            if(breakProgress > 1) {
                                // the block breaks
                                world.playSound(loc, breakSound, 1, 1);
                                block.breakNaturally(tool);

                                // Subtract the tool's durability
                                if (tool.getType() != Material.AIR) {
                                    if(tool instanceof Damageable) {
                                        Damageable toolDamageable = (Damageable) tool.getItemMeta();
                                        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.DURABILITY);
                                        double unbreakingProbability = 1.0 / (unbreakingLevel + 1.0);
                                        // Only apply damage with the calculated probability based on the Unbreaking level
                                        if (Math.random() < unbreakingProbability) {
                                            toolDamageable.setDamage(toolDamageable.getDamage() + 1);
                                            if (toolDamageable.getDamage() >= tool.getType().getMaxDurability()) {
                                                player.getInventory().setItemInMainHand(null);
                                            } else {
                                                tool.setItemMeta((ItemMeta) toolDamageable);
                                                player.getInventory().setItemInMainHand(tool);
                                            }
                                        }
                                    }
                                }

                                this.cancel();
                            }
                            if(!player.isOnline() || player.isDead() || player.getWorld() != loc.getWorld() || player.getLocation().distanceSquared(loc) > 64) {
                                // the player stops breaking the block
                                sendBlockBreakAnimation(player, loc, -1);
                                this.cancel();
                            }
                        }
                    };
                    breakTasks.put(player.getUniqueId(), breakTask);
                    breakTask.runTaskTimer(plugin, 0L, 1L);

                }
                if(digType.equals(EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) || digType.equals(EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK)) {
                    // the player stops breaking the block
                    sendBlockBreakAnimation(event.getPlayer(), loc, -1);
                    UUID playerId = player.getUniqueId();
                    if(breakTasks.containsKey(playerId)) {
                        breakTasks.get(playerId).cancel();
                        breakTasks.remove(playerId);
                    }
                }
            }

        });
    }

    public int getBreakStage(float breakProgress) {
        return (int) (Math.floor(breakProgress * 10) - 1);
    }

    public float getBreakSpeedIgnoreWater(Player player, Block block) {
        float breakSpeed = block.getBreakSpeed(player);
        boolean onGround = player.isOnGround();
        if (!onGround) {
            breakSpeed *= 5; // removes the floating penalty
        }
        boolean inWater = player.getEyeLocation().getBlock().isLiquid();
        boolean hasAquaAffinity = player.getInventory().getHelmet() != null && player.getInventory().getHelmet().containsEnchantment(Enchantment.WATER_WORKER);
        if (inWater && !hasAquaAffinity) {
            breakSpeed *= 5; // removes the underwater penalty
        }
        return breakSpeed;
    }

    public void sendBlockBreakAnimation(Player player, Location blockLocation, int breakStage) {
        try {
            // Create a new packet of type PacketPlayOutBlockBreakAnimation
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);

            // Set the block position
            packet.getBlockPositionModifier().write(0, new BlockPosition(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ()));

            // Set the entity ID (any unique ID can be used)
            packet.getIntegers().write(0, player.getEntityId() * 10);

            // Set the break stage (0-9)
            packet.getIntegers().write(1, breakStage);

            // Send the packet to the player
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
