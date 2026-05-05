package com.oscity.mechanics;

import com.oscity.mode.PlayerMode;
import com.oscity.session.JourneyTracker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JourneyMapManager {

    private final JavaPlugin plugin;
    private final JourneyTracker tracker;

    private final Map<UUID, MapView> playerMapViews = new HashMap<>();

    public JourneyMapManager(JavaPlugin plugin, JourneyTracker tracker) {
        this.plugin  = plugin;
        this.tracker = tracker;
    }

    public void giveInitialMap(Player player, String chestConfigKey) {
        MapView view = getOrCreateView(player);
        rerender(view, buildLines(tracker.getVars(player), false));
        placeInChest(buildMapItem(view), chestConfigKey);
    }

    public void updateMap(Player player) {
        MapView view = playerMapViews.get(player.getUniqueId());
        if (view == null) {
            plugin.getLogger().warning("[JourneyMap] Cannot update journey map for "
                + player.getName() + ": missing MapView");
            return;
        }
        rerender(view, buildLines(tracker.getVars(player), true));
    }

    public void updateMapAfterCalculator(Player player) {
        boolean isLearner = tracker.getMode(player) == PlayerMode.LEARNER;
        MapView view = playerMapViews.get(player.getUniqueId());
        if (view == null) {
            plugin.getLogger().warning("[JourneyMap] Cannot update journey map after calculator for "
                + player.getName() + ": missing MapView");
            return;
        }
        rerender(view, buildLinesAfterCalculator(tracker.getVars(player), !isLearner));
    }

    public void updateMapAfterTLBHit(Player player) {
        MapView view = playerMapViews.get(player.getUniqueId());
        if (view == null) {
            plugin.getLogger().warning("[JourneyMap] Cannot update journey map after TLB hit for "
                + player.getName() + ": missing MapView");
            return;
        }
        rerender(view, buildLinesWithPFN(tracker.getVars(player)));
    }

    public void updateMapAfterPTE(Player player) {
        updateMap(player);
    }

    private List<String> buildLines(Map<String, String> vars, boolean showDiscoveries) {
        List<String> lines = new ArrayList<>();

        lines.add("= OSCity Journey Map =");
        lines.add(safe(vars.getOrDefault("process", "")));
        lines.add("");

        String instruction = vars.getOrDefault("instruction", "?");
        if (!"?".equals(instruction) && instruction != null && !instruction.isEmpty()) {
            String[] parts = instruction.split(" ", 3);
            if (parts.length == 3) {
                lines.add("Instruction: " + safe(parts[0]));
                lines.add(safe(parts[1]) + " " + safe(parts[2]));
            } else if (parts.length == 2) {
                lines.add("Instruction: " + safe(parts[0]));
                lines.add(safe(parts[1]));
            } else {
                lines.add("Instruction: " + safe(instruction));
            }
        } else {
            lines.add("Instruction: ?");
        }
        lines.add("");

        String vaHex = vars.getOrDefault("va", "?");
        lines.add("VA: " + safe(vaHex));

        if (showDiscoveries) {
            String vpnBin = vars.getOrDefault("vpn", "?");
            String vpnHex = vars.getOrDefault("vpnHex", "?");
            String offsetBin = vars.getOrDefault("offset", "?");
            String offsetHex = vars.getOrDefault("offsetHex", "?");

            if (!"?".equals(vpnBin)) {
                lines.add("VPN: " + safe(vpnBin) + " = " + safe(vpnHex));
            }
            if (!"?".equals(offsetBin)) {
                lines.add("OFFSET: " + safe(offsetBin) + " = " + safe(offsetHex));
            }

            String pageSize = vars.getOrDefault("pageSize", "?");
            if (!"?".equals(pageSize) && !pageSize.isEmpty()) {
                lines.add("Page Size: " + safe(pageSize));
            }

            String pageIdx  = vars.getOrDefault("pageIndex", "?");
            if (!"?".equals(pageIdx) && !pageIdx.isEmpty()) {
                lines.add("Page_index: " + safe(pageIdx));
            }

        }

        return lines;
    }

    private List<String> buildLinesAfterCalculator(Map<String, String> vars, boolean showJourneyName) {
        List<String> lines = new ArrayList<>();

        lines.add("= OSCity Journey Map =");
        if (showJourneyName) {
            lines.add(safe(vars.getOrDefault("journey", "Journey")));
        }
        lines.add(safe(vars.getOrDefault("process", "")));
        lines.add("");

        String instruction = vars.getOrDefault("instruction", "?");
        if (!"?".equals(instruction) && instruction != null && !instruction.isEmpty()) {
            String[] parts = instruction.split(" ", 3);
            if (parts.length == 3) {
                lines.add("Instruction: " + safe(parts[0]));
                lines.add(safe(parts[1]) + " " + safe(parts[2]));
            } else if (parts.length == 2) {
                lines.add("Instruction: " + safe(parts[0]));
                lines.add(safe(parts[1]));
            } else {
                lines.add("Instruction: " + safe(instruction));
            }
        } else {
            lines.add("Instruction: ?");
        }
        lines.add("");

        String vaHex = vars.getOrDefault("va", "?");
        lines.add("VA: " + safe(vaHex));

        String vpnBin = vars.getOrDefault("vpn", "?");
        String vpnHex = vars.getOrDefault("vpnHex", "?");
        String offsetBin = vars.getOrDefault("offset", "?");
        String offsetHex = vars.getOrDefault("offsetHex", "?");

        if (!"?".equals(vpnBin)) {
            lines.add("VPN: " + safe(vpnBin) + " = " + safe(vpnHex));
        }
        if (!"?".equals(offsetBin)) {
            lines.add("OFFSET: " + safe(offsetBin) + " = " + safe(offsetHex));
        }

        String pageSize = vars.getOrDefault("pageSize", "?");
        if (!"?".equals(pageSize) && !pageSize.isEmpty()) {
            lines.add("Page Size: " + safe(pageSize));
        }

        String pageIdx  = vars.getOrDefault("pageIndex", "?");
        if (!"?".equals(pageIdx) && !pageIdx.isEmpty()) {
            lines.add("Page_index: " + safe(pageIdx));
        }

        return lines;
    }

    private List<String> buildLinesWithPFN(Map<String, String> vars) {
        List<String> lines = buildLines(vars, true);
        String pfn = vars.getOrDefault("pfn", "?");
        if (!"?".equals(pfn) && !"N/A".equals(pfn) && !pfn.isEmpty()) {
            lines.add("PFN: " + safe(pfn));
        }
        return lines;
    }

    @SuppressWarnings("deprecation")
    private void rerender(MapView view, List<String> lines) {
        view.getRenderers().clear();

        final byte bg     = MapPalette.matchColor(new Color(10, 20, 40));
        final byte accent = MapPalette.matchColor(new Color(30, 80, 150));

        view.addRenderer(new MapRenderer(false) {
            @Override
            public void render(MapView mapView, MapCanvas canvas, Player player) {
                for (int x = 0; x < 128; x++)
                    for (int y = 0; y < 128; y++)
                        canvas.setPixel(x, y, bg);

                for (int x = 0; x < 128; x++) {
                    canvas.setPixel(x, 0,   accent);
                    canvas.setPixel(x, 1,   accent);
                    canvas.setPixel(x, 126, accent);
                    canvas.setPixel(x, 127, accent);
                }

                int y = 2;
                for (String line : lines) {
                    if (y > 124) break;
                    if (!line.isEmpty()) {
                        canvas.drawText(2, y, MinecraftFont.Font, line);
                    }
                    y += 10;
                }
            }
        });
    }

    private ItemStack buildMapItem(MapView view) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.displayName(Component.text("§bOSCity Journey Map"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void placeInChest(ItemStack mapItem, String chestKey) {
        ConfigurationSection sec = plugin.getConfig()
            .getConfigurationSection("chests." + chestKey);
        if (sec == null) {
            plugin.getLogger().warning("[JourneyMap] No config at chests." + chestKey);
            return;
        }
        String worldName = sec.getString("world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        Block block = loc.getBlock();

        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            plugin.getLogger().warning("[JourneyMap] No chest block at chests." + chestKey
                + " (found: " + block.getType() + ")");
            return;
        }

        Inventory inv = chest.getInventory();
        inv.clear();
        inv.setItem(13, mapItem); // slot 13 = centre of a 27-slot chest
        plugin.getLogger().info("[JourneyMap] Journey map placed in chests." + chestKey);
    }

    @SuppressWarnings("deprecation")
    private MapView getOrCreateView(Player player) {
        MapView view = playerMapViews.get(player.getUniqueId());
        if (view == null) {
            view = Bukkit.createMap(player.getWorld());
            view.setScale(MapView.Scale.CLOSEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            playerMapViews.put(player.getUniqueId(), view);
        }
        return view;
    }

    private List<String> wrap(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) { result.add("?"); return result; }
        while (text.length() > maxLen) {
            int cut = text.lastIndexOf(' ', maxLen);
            if (cut <= 0) cut = maxLen;
            result.add(text.substring(0, cut).trim());
            text = text.substring(cut).trim();
        }
        if (!text.isEmpty()) result.add(text);
        return result;
    }

    private String cap(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : (s != null ? s : "");
    }

    /**
     * Replace characters not supported by MinecraftFont with '?'
     * to avoid IllegalArgumentException from canvas.drawText().
     */
    private String safe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(MinecraftFont.Font.getChar(c) != null ? c : '?');
        }
        return sb.toString();
    }
}
