package me.modernadventurer.waterbreather;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.lang.Math.min;
import static java.lang.Math.pow;

public final class WaterBreather extends JavaPlugin {

    ProtocolManager protocolManager;
    WaterBreather plugin;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        getServer().getPluginManager().registerEvents(new MyListener(this), this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        registerBlockBreakListener();

        //Objects.requireNonNull(getCommand("blockbreakanimation")).setExecutor(this);

        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            int tick = 0;
            public void run() {
                Bukkit.getServer().getPluginManager().callEvent(new GameTickEvent(tick));
                tick += 1;
            }
        }, 0L, 1L);
    }

    // Implement the command executor
   /* @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("blockbreakanimation")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }

            Player player = (Player) sender;
            Location loc = player.getTargetBlock(null, 5).getLocation();
            int breakStage = 9; // Default break stage
            if (args.length > 0) {
                try {
                    breakStage = Integer.parseInt(args[0]);
                    if (breakStage < -1 || breakStage > 9) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid break stage. Must be an integer between 0 and 9.");
                    return true;
                }
            }
            sendBlockBreakAnimation(player, loc, breakStage);

            return true;
        }

        return false;
    }
*/
    private void registerBlockBreakListener() {
        Map<UUID, BukkitRunnable> breakTasks = new HashMap<>();
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.HIGHEST, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().getValues().get(0);
                BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);
                Location loc = pos.toLocation(event.getPlayer().getWorld());
                Block block = loc.getBlock();
                Player player = event.getPlayer();
                if(digType.equals(EnumWrappers.PlayerDigType.START_DESTROY_BLOCK)) {
                    BukkitRunnable breakTask = new BukkitRunnable() {
                        float breakProgress = 0;
                        final float breakSpeed = getBreakSpeedIgnoreWater(player, block);

                        @Override
                        public void run() {
                            breakProgress += breakSpeed;
                            sendBlockBreakAnimation(player, loc, getBreakStage(breakProgress));
                            if(breakProgress > 1) {
                                // the block breaks
                                block.breakNaturally();
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
        boolean inWater = player.getLocation().getBlock().isLiquid();
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
