package com.oscity.mechanics;

import com.oscity.OSCity;
import com.oscity.core.KernelGuardian;
import com.oscity.core.RoomChangeListener;
import com.oscity.session.JourneyTracker;
import com.oscity.world.LocationRegistry;
import com.oscity.world.RoomRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class TeleportManager implements Listener {

    private final JavaPlugin plugin;
    private final LocationRegistry locationRegistry;
    private final JourneyTracker journeyTracker;
    private final boolean debugClicks;

    private final Map<Location, TeleportButton> buttons = new HashMap<>();

    private static class TeleportButton {
        String key;
        String destination;
        String message;

        TeleportButton(String key, String destination, String message) {
            this.key = key;
            this.destination = destination;
            this.message = message;
        }
    }

    public TeleportManager(JavaPlugin plugin, LocationRegistry locationRegistry, JourneyTracker journeyTracker, boolean debugClicks) {
        this.plugin = plugin;
        this.locationRegistry = locationRegistry;
        this.journeyTracker = journeyTracker;
        this.debugClicks = debugClicks;
        loadButtons();
    }
    
    private void loadButtons() {
        buttons.clear();
        
        ConfigurationSection tpButtons = plugin.getConfig().getConfigurationSection("tpButtons");
        if (tpButtons == null) {
            plugin.getLogger().warning("No 'tpButtons' section in config.yml");
            return;
        }
        
        for (String key : tpButtons.getKeys(false)) {
            ConfigurationSection btn = tpButtons.getConfigurationSection(key);
            if (btn == null) continue;
            
            String worldName = btn.getString("world");
            if (worldName == null) {
                plugin.getLogger().warning("Button '" + key + "' missing 'world'");
                continue;
            }
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Button '" + key + "' refers to unknown world: " + worldName);
                continue;
            }
            
            int x = btn.getInt("x");
            int y = btn.getInt("y");
            int z = btn.getInt("z");
            Location buttonLoc = new Location(world, x, y, z);
            
            String destination = btn.getString("destination");
            if (destination == null) {
                plugin.getLogger().warning("Button '" + key + "' missing 'destination'");
                continue;
            }
            
            String message = btn.getString("message", "&aTeleported!");
            
            buttons.put(buttonLoc, new TeleportButton(key, destination, message));
        }
        
        plugin.getLogger().info("Loaded " + buttons.size() + " teleport buttons from config.yml");
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("TeleportManager registered.");
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPress(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Location clicked = e.getClickedBlock().getLocation();
        Material type = e.getClickedBlock().getType();

        if (debugClicks) {
            e.getPlayer().sendMessage(Component.text(
                    "Clicked: " + type + " at " +
                            clicked.getBlockX() + ", " + 
                            clicked.getBlockY() + ", " + 
                            clicked.getBlockZ(),
                    NamedTextColor.YELLOW
            ));
        }

        if (!type.name().endsWith("_BUTTON")) return;
        
        for (Map.Entry<Location, TeleportButton> entry : buttons.entrySet()) {
            if (sameBlock(clicked, entry.getKey())) {
                TeleportButton button = entry.getValue();
                Player player = e.getPlayer();

                // Phase gate: block going to Calculator if already completed
                if ("tlbToCalculator".equals(button.key)) {
                    String phase = journeyTracker.getPhase(player);
                    if (!"tlb_spawn".equals(phase)) {
                        player.sendMessage("§cYou've already visited the Calculator Room. Make a hit or miss decision.");
                        return;
                    }
                }

                Location destination = locationRegistry.get(button.destination);
                if (destination == null) {
                    plugin.getLogger().warning("[Teleport] Destination '" + button.destination
                        + "' not found for tpButtons." + button.key);
                    e.getPlayer().sendMessage(Component.text(
                        "§c[Error] Destination '" + button.destination + "' not found!", 
                        NamedTextColor.RED
                    ));
                    return;
                }
                
                e.getPlayer().teleport(destination);

                OSCity oscity = (OSCity) plugin;
                RoomRegistry.Room room = oscity.getRoomRegistry().getRoomAt(destination);
                if (room != null) {
                    KernelGuardian guardian = oscity.getKernelGuardian();
                    if (guardian != null && guardian.isSpawned()) {
                        guardian.moveTo(room.npcPosition != null
                            ? room.npcPosition
                            : destination.clone().add(3, 0, 0));
                    }
                    RoomChangeListener rcl = oscity.getRoomChangeListener();
                    if (rcl != null) {
                        rcl.markRoomEntered(player, room.title);
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                            rcl.onRoomEntered(player, room.title), 10L);
                    }
                } else {
                    plugin.getLogger().warning("[Teleport] Destination '" + button.destination
                        + "' for tpButtons." + button.key
                        + " is not inside any registered room; room-entry logic skipped");
                }

                Component message = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(button.message);
                e.getPlayer().sendMessage(message);

                return;
            }
        }
    }
}
