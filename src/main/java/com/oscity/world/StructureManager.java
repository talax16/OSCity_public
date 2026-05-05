package com.oscity.world;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class StructureManager {

    private final JavaPlugin plugin;
    private final WorldManager worldManager;
    private final RoomRegistry roomRegistry;

    public StructureManager(JavaPlugin plugin, WorldManager worldManager, RoomRegistry roomRegistry) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.roomRegistry = roomRegistry;
    }

    public void initialize() {
        World world = worldManager.getGameWorld();
        if (world == null) {
            plugin.getLogger().severe("StructureManager: game world is not loaded, skipping init.");
            return;
        }
        plugin.getLogger().info("StructureManager initialized. Structures ready in world: " + world.getName());
    }

    public String getStructureAt(org.bukkit.Location loc) {
        return roomRegistry.getRoomTitleAt(loc);
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public RoomRegistry getRoomRegistry() {
        return roomRegistry;
    }
}
