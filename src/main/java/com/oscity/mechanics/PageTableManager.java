package com.oscity.mechanics;

import com.oscity.journey.Journey;
import com.oscity.session.JourneyTracker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PageTableManager {

    private final JavaPlugin plugin;
    private final JourneyTracker tracker;

    public PageTableManager(JavaPlugin plugin, JourneyTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    public void populateFloor(Player player, int floorNum) {
        plugin.getLogger().info("[PageTable] populateFloor called for " + player.getName() + " floor=" + floorNum);

        Journey journey = tracker.getJourney(player);
        if (journey == null) {
            plugin.getLogger().warning("[PageTable] No journey for " + player.getName());
            return;
        }

        String vpnHex = tracker.getVar(player, "vpnHex");
        if ("?".equals(vpnHex) || vpnHex.isEmpty()) {
            plugin.getLogger().warning("[PageTable] No vpnHex for " + player.getName());
            return;
        }

        int vpnValue = parseVpnHex(vpnHex);
        int correctChestIndex = vpnValue & 0x3;

        plugin.getLogger().info("[PageTable] vpnHex=" + vpnHex + " vpnValue=" + vpnValue
            + " correctChest=" + correctChestIndex);

        for (int chestIdx = 0; chestIdx < 4; chestIdx++) {
            boolean isCorrect = (chestIdx == correctChestIndex);
            String pteData = buildPteData(player, journey, floorNum, chestIdx, isCorrect);
            placePteMap(player, floorNum, chestIdx, pteData, isCorrect);
        }
    }

    public void updatePteMapAfterCow(Player player) {
        plugin.getLogger().info("[PageTable] updatePteMapAfterCow called for " + player.getName());
        updatePteMap(player);
    }

    public void updatePteMapPFNOnly(Player player, String newPfn) {
        plugin.getLogger().info("[PageTable] updatePteMapPFNOnly called for " + player.getName() + ", newPfn=" + newPfn);

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
                String displayName = item.getItemMeta().getDisplayName();
                if (displayName != null && displayName.contains("PTE Map")) {
                    plugin.getLogger().info("[PageTable] Found PTE map in slot " + i);

                    Journey journey = tracker.getJourney(player);
                    String vpnHex = tracker.getVar(player, "vpnHex");
                    int vpnValue = parseVpnHex(vpnHex);
                    int correctChestIndex = vpnValue & 0x3;
                    int floorNum = 1;

                    String pteData = buildPteDataWithPFN(player, journey, floorNum, correctChestIndex, true, newPfn);
                    plugin.getLogger().info("[PageTable] New PTE data (PFN only update): " + pteData);

                    ItemStack newPteMap = buildPteMapItem(floorNum, correctChestIndex, pteData, true);
                    player.getInventory().setItem(i, newPteMap);
                    plugin.getLogger().info("[PageTable] PTE map PFN updated to " + newPfn);
                    break;
                }
            }
        }
    }

    public void updatePteMap(Player player) {
        plugin.getLogger().info("[PageTable] updatePteMap called for " + player.getName());

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
                String displayName = item.getItemMeta().getDisplayName();
                if (displayName != null && displayName.contains("PTE Map")) {
                    plugin.getLogger().info("[PageTable] Found PTE map in slot " + i);

                    Journey journey = tracker.getJourney(player);
                    String vpnHex = tracker.getVar(player, "vpnHex");
                    int vpnValue = parseVpnHex(vpnHex);
                    int correctChestIndex = vpnValue & 0x3;
                    int floorNum = 1;

                    String pteData = buildPteData(player, journey, floorNum, correctChestIndex, true);
                    plugin.getLogger().info("[PageTable] New PTE data: " + pteData);

                    ItemStack newPteMap = buildPteMapItem(floorNum, correctChestIndex, pteData, true);
                    player.getInventory().setItem(i, newPteMap);
                    plugin.getLogger().info("[PageTable] PTE map updated!");
                    break;
                }
            }
        }
    }

    private String buildPteData(Player player, Journey journey, int floorNum, int chestIdx, boolean isCorrect) {
        if (!isCorrect) {
            return buildFakePte(player, floorNum, chestIdx);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("= PTE Entry =\n");

        switch (journey) {
            case LUCKY:
                sb.append("PRESENT: 1\n");
                sb.append("READ: 1\n");
                sb.append("WRITE: 1\n");
                sb.append("READ_ONLY: 0\n");
                sb.append("USER: 1\n");
                sb.append("FILE_BACKED: 0\n");
                String pfn = tracker.getVar(player, "pfn");
                sb.append("PFN: ").append(pfn != null ? pfn : "?").append("\n");
                sb.append("IN_SWAP: 0\n");
                break;

            case TLB_MISS_ALLOW:
                String pfnTLB = tracker.getVar(player, "pfn");
                sb.append("PRESENT: 1\n");
                sb.append("READ: 1\n");
                sb.append("WRITE: 1\n");
                sb.append("READ_ONLY: 0\n");
                sb.append("USER: 1\n");
                sb.append("FILE_BACKED: 0\n");
                sb.append("PFN: ").append(pfnTLB != null ? pfnTLB : "0x3").append("\n");
                sb.append("IN_SWAP: 0\n");
                break;

            case PERMISSION_VIOLATION:
                sb.append("PRESENT: 1\n");
                sb.append("READ: 0\n");
                sb.append("WRITE: 0\n");
                sb.append("READ_ONLY: 0\n");
                sb.append("USER: 0\n");
                sb.append("KERNEL: 1\n");
                sb.append("FILE_BACKED: 0\n");
                sb.append("PFN: 0x4\n");
                sb.append("IN_SWAP: 0\n");
                break;

            case SWAPPED_OUT:
                String swapPresent = tracker.getVar(player, "ptePresent");
                String swapPfn     = tracker.getVar(player, "pfn");
                boolean loaded = "1".equals(swapPresent);
                sb.append("PRESENT: ").append(loaded ? "1" : "0").append("\n");
                sb.append("READ: 1\n");
                sb.append("WRITE: 1\n");
                sb.append("READ_ONLY: 0\n");
                sb.append("USER: 1\n");
                sb.append("KERNEL: 1\n");
                sb.append("PFN: ").append(loaded ? (swapPfn != null ? swapPfn : "0x2") : "N/A").append("\n");
                sb.append("IN_SWAP: ").append(loaded ? "0" : "1").append("\n");
                if (!loaded) sb.append("SWAP_SLOT: 0\n");
                break;

            case PURE_COW:
                String pfnCow = tracker.getVar(player, "pfn");
                String writeCow = tracker.getVar(player, "pteWrite");
                String readOnlyCow = tracker.getVar(player, "pteReadOnly");
                
                sb.append("PRESENT: 1\n");
                sb.append("READ: 1\n");
                sb.append("WRITE: ").append((writeCow == null || "?".equals(writeCow)) ? "0" : writeCow).append("\n");
                sb.append("READ_ONLY: ").append((readOnlyCow == null || "?".equals(readOnlyCow)) ? "1" : readOnlyCow).append("\n");
                sb.append("USER: 1\n");
                sb.append("KERNEL: 1\n");
                sb.append("ACCESSED: 1\n");
                sb.append("PFN: ").append(pfnCow != null ? pfnCow : "0x9").append("\n");
                break;

            case LAZY_LOADING:
                String lazyPresent = tracker.getVar(player, "ptePresent");
                String lazyPfn     = tracker.getVar(player, "pfn");
                boolean lazyLoaded = "1".equals(lazyPresent);
                sb.append("PRESENT: ").append(lazyLoaded ? "1" : "0").append("\n");
                sb.append("READ: ").append(lazyLoaded ? "1" : "0").append("\n");
                sb.append("WRITE: ").append(lazyLoaded ? "1" : "0").append("\n");
                sb.append("READ_ONLY: 0\n");
                sb.append("USER: 1\n");
                sb.append("KERNEL: 1\n");
                if (!lazyLoaded) {
                    sb.append("FILE_BACKED: 1\n");
                    sb.append("PFN: N/A\n");
                    sb.append("FILE_NAME:\n");
                    sb.append("mystical_grimoire.spells\n");
                } else {
                    sb.append("PFN: ").append(lazyPfn != null ? lazyPfn : "N/A").append("\n");
                }
                break;

            case LAZY_ALLOCATION:
                String ptePresent = tracker.getVar(player, "ptePresent");
                String pteRead = tracker.getVar(player, "pteRead");
                String pteWrite = tracker.getVar(player, "pteWrite");
                String pteReadOnly = tracker.getVar(player, "pteReadOnly");
                String pteUser = tracker.getVar(player, "pteUser");
                String pteKernel = tracker.getVar(player, "pteKernel");
                String pteFileBacked = tracker.getVar(player, "pteFileBacked");
                String pteAnon = tracker.getVar(player, "pteAnon");
                String pfnLazy = tracker.getVar(player, "pfn");
                String pteInSwap = tracker.getVar(player, "pteInSwap");
                
                sb.append("PRESENT: ").append(ptePresent != null ? ptePresent : "0").append("\n");
                sb.append("READ: ").append(pteRead != null ? pteRead : "0").append("\n");
                sb.append("WRITE: ").append(pteWrite != null ? pteWrite : "0").append("\n");
                sb.append("READ_ONLY: ").append(pteReadOnly != null ? pteReadOnly : "0").append("\n");
                sb.append("USER: ").append(pteUser != null ? pteUser : "1").append("\n");
                if (pteKernel != null) sb.append("KERNEL: ").append(pteKernel).append("\n");
                if (pteFileBacked != null) sb.append("FILE_BACKED: ").append(pteFileBacked).append("\n");
                if ("1".equals(pteAnon)) sb.append("ANON: 1\n");
                sb.append("PFN: ").append(pfnLazy != null ? pfnLazy : "N/A").append("\n");
                sb.append("IN_SWAP: ").append(pteInSwap != null ? pteInSwap : "0").append("\n");
                break;

            default:
                sb.append("PRESENT: ?\n");
                sb.append("PFN: ?\n");
                break;
        }

        return sb.toString();
    }

    private String buildPteDataWithPFN(Player player, Journey journey, int floorNum, int chestIdx, boolean isCorrect, String newPfn) {
        if (!isCorrect) {
            return buildFakePte(player, floorNum, chestIdx);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("= PTE Entry =\n");

        switch (journey) {
            case LAZY_ALLOCATION:
                String ptePresent = tracker.getVar(player, "ptePresent");
                String pteRead = tracker.getVar(player, "pteRead");
                String pteWrite = tracker.getVar(player, "pteWrite");
                String pteReadOnly = tracker.getVar(player, "pteReadOnly");
                String pteUser = tracker.getVar(player, "pteUser");
                String pteKernel = tracker.getVar(player, "pteKernel");
                String pteFileBacked = tracker.getVar(player, "pteFileBacked");
                String pteAnon = tracker.getVar(player, "pteAnon");
                String pteInSwap = tracker.getVar(player, "pteInSwap");

                sb.append("PRESENT: ").append(ptePresent != null ? ptePresent : "0").append("\n");
                sb.append("READ: ").append(pteRead != null ? pteRead : "0").append("\n");
                sb.append("WRITE: ").append(pteWrite != null ? pteWrite : "0").append("\n");
                sb.append("READ_ONLY: ").append(pteReadOnly != null ? pteReadOnly : "0").append("\n");
                sb.append("USER: ").append(pteUser != null ? pteUser : "1").append("\n");
                if (pteKernel != null) sb.append("KERNEL: ").append(pteKernel).append("\n");
                if (pteFileBacked != null) sb.append("FILE_BACKED: ").append(pteFileBacked).append("\n");
                if ("1".equals(pteAnon)) sb.append("ANON: 1\n");
                sb.append("PFN: ").append(newPfn).append("\n");
                sb.append("IN_SWAP: ").append(pteInSwap != null ? pteInSwap : "0").append("\n");
                break;

            default:
                return buildPteData(player, journey, floorNum, chestIdx, isCorrect);
        }

        return sb.toString();
    }

    /** Fake PTE data for incorrect chests (deterministic per player/floor/chest). */
    private String buildFakePte(Player player, int floorNum, int chestIdx) {
        long seed = player.getUniqueId().getLeastSignificantBits() ^ (floorNum << 4) ^ chestIdx;
        Random rand = new Random(seed);

        StringBuilder sb = new StringBuilder();
        sb.append("= PTE Entry =\n");

        boolean present = rand.nextBoolean();
        sb.append("PRESENT: ").append(present ? "1" : "0").append("\n");

        if (present) {
            int fakePfn = rand.nextInt(16);
            sb.append("PFN: 0x").append(Integer.toHexString(fakePfn).toUpperCase()).append("\n");
        }

        String[] perms = {"R", "RW", "RX", "RWX"};
        sb.append(perms[rand.nextInt(perms.length)]).append("\n");

        sb.append("COW: ").append(rand.nextBoolean() ? "1" : "0").append("\n");

        return sb.toString();
    }

    private boolean isPresentBit(Journey journey, int floorNum) {
        return !needsSwapInfo(journey);
    }

    private String getPermissions(Journey journey) {
        if (journey == Journey.PURE_COW) {
            return "R (read-only)";
        }
        return "RW";
    }

    private boolean needsSwapInfo(Journey journey) {
        return journey == Journey.LAZY_LOADING || journey == Journey.LAZY_ALLOCATION;
    }

    private boolean needsFileInfo(Journey journey) {
        return false;
    }

    @SuppressWarnings("deprecation")
    private void placePteMap(Player player, int floorNum, int chestIdx, String pteData, boolean isCorrect) {
        String configKey = "chests.pageTable" + floorNum + ".chest" + chestIdx;
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(configKey);
        if (sec == null) {
            plugin.getLogger().warning("[PageTable] No config for " + configKey);
            return;
        }

        String worldName = sec.getString("world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        Block block = loc.getBlock();

        plugin.getLogger().info("[PageTable] placePteMap floor=" + floorNum + " chest=" + chestIdx
            + " at (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ") block=" + block.getType());

        if (!(block.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("[PageTable] No chest at " + configKey
                + " (" + block.getType() + ")");
            return;
        }

        Inventory inv = chest.getInventory();
        inv.clear();
        inv.setItem(13, buildPteMapItem(floorNum, chestIdx, pteData, isCorrect));
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildPteMapItem(int floorNum, int chestIdx, String pteData, boolean isCorrect) {
        MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.getRenderers().clear();

        final byte bg = MapPalette.matchColor(new Color(10, 20, 40));
        final byte accent = isCorrect
            ? MapPalette.matchColor(new Color(30, 150, 80))
            : MapPalette.matchColor(new Color(150, 30, 30));

        String[] lines = pteData.split("\n");

        view.addRenderer(new MapRenderer(false) {
            @Override
            public void render(MapView mapView, MapCanvas canvas, Player player) {
                for (int x = 0; x < 128; x++)
                    for (int y = 0; y < 128; y++)
                        canvas.setPixel(x, y, bg);

                for (int x = 0; x < 128; x++) {
                    canvas.setPixel(x, 0, accent);
                    canvas.setPixel(x, 1, accent);
                }

                String title = "PT" + floorNum + " Chest" + chestIdx;
                canvas.drawText(2, 2, MinecraftFont.Font, title);

                int y = 14;
                for (String line : lines) {
                    if (y > 124) break;
                    canvas.drawText(2, y, MinecraftFont.Font, safe(line));
                    y += 10;
                }
            }
        });

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.displayName(Component.text("§bPTE Map §7(PT" + floorNum + " Chest" + chestIdx + ")"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int parseVpnHex(String vpnHex) {
        try {
            return Integer.parseInt(vpnHex.replace("0x", "").replace("0X", ""), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(MinecraftFont.Font.getChar(c) != null ? c : '?');
        }
        return sb.toString();
    }
}
