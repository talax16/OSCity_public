package com.oscity.mechanics;

import com.oscity.content.DialogueManager;
import com.oscity.content.QuestionBank;
import com.oscity.session.JourneyTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.bukkit.map.MapPalette;
import java.awt.Color;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class CalculatorListener implements Listener {

    private static final Logger log = Logger.getLogger("OSCity");

    private final JavaPlugin plugin;
    private final JourneyTracker tracker;
    private final JourneyMapManager journeyMapManager;
    private final QuestionBank questionBank;
    private final DialogueManager dialogueManager;

    private Location hopperLocation;
    private final List<Location> instrFrames = new ArrayList<>();
    private final List<Location> calcFrames  = new ArrayList<>();
    private int pageOffsetBits = 4;

    private final Map<UUID, Boolean> calculating = new HashMap<>();

    private final Map<UUID, Boolean> hasCalculated = new HashMap<>();

    private final Map<UUID, String> pendingCalcVerify = new HashMap<>();

    private final Map<UUID, Boolean> wasSkipped = new HashMap<>();


    private final Map<Location, MapView> frameMapViews = new HashMap<>();

    public CalculatorListener(JavaPlugin plugin, JourneyTracker tracker,
                              JourneyMapManager journeyMapManager, QuestionBank questionBank,
                              DialogueManager dialogueManager) {
        this.plugin          = plugin;
        this.tracker         = tracker;
        this.journeyMapManager = journeyMapManager;
        this.questionBank    = questionBank;
        this.dialogueManager = dialogueManager;
        loadConfig();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("CalculatorListener registered.");
    }

    private void loadConfig() {
        ConfigurationSection hopperSec = plugin.getConfig().getConfigurationSection("hopper");
        if (hopperSec != null) {
            World world = Bukkit.getWorld(hopperSec.getString("world", "OSCityWorld"));
            hopperLocation = new Location(world,
                hopperSec.getInt("x"), hopperSec.getInt("y"), hopperSec.getInt("z"));
        }

        ConfigurationSection frames = plugin.getConfig().getConfigurationSection("signs.calculatorRoom");
        if (frames != null) {
            for (int i = 1; i <= 6; i++) {
                ConfigurationSection instr = frames.getConfigurationSection("instructionsFrame" + i);
                if (instr != null) instrFrames.add(locFrom(instr));
                ConfigurationSection calc = frames.getConfigurationSection("calculationFrame" + i);
                if (calc != null) calcFrames.add(locFrom(calc));
            }
        }

        pageOffsetBits = plugin.getConfig().getInt("calculator.pageOffsetBits", 4);
        plugin.getLogger().info("CalculatorListener: " + instrFrames.size()
            + " instruction frames, " + calcFrames.size() + " calculation frames, "
            + "pageOffsetBits=" + pageOffsetBits);
    }

    private Location locFrom(ConfigurationSection sec) {
        World world = Bukkit.getWorld(sec.getString("world", "OSCityWorld"));
        return new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
    }

    public void onCalculatorRoomEntered(Player player, String phase) {
        log.info("[Calc] " + player.getName() + " entered Calculator Room | phase=" + phase
            + " | va=" + tracker.getVar(player, "va"));
        updateInstructionFrames(phase);
        setCalcAwaiting();
        calculating.remove(player.getUniqueId());
        hasCalculated.remove(player.getUniqueId());
        pendingCalcVerify.remove(player.getUniqueId());
        wasSkipped.remove(player.getUniqueId());
    }

    public void clearHopper() {
        if (hopperLocation == null) return;
        Block block = hopperLocation.getBlock();
        if (block.getState() instanceof org.bukkit.block.Hopper hopper) {
            hopper.getInventory().clear();
        }
    }

    public boolean hasPlayerCalculated(Player player) {
        return hasCalculated.getOrDefault(player.getUniqueId(), false);
    }

    public boolean hasPendingCalcVerify(Player player) {
        return pendingCalcVerify.containsKey(player.getUniqueId());
    }

    public void skipCalculation(Player player) {
        if (calculating.getOrDefault(player.getUniqueId(), false)) return;
        String va = tracker.getVar(player, "va");
        if ("?".equals(va) || va.isEmpty()) {
            player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("errors.calculator.no_va"));
            return;
        }
        String phase = tracker.getPhase(player);
        boolean isPageIndex = "calculator_from_lazy_loading".equals(phase);
        log.info("[Calc] " + player.getName() + " SKIP | phase=" + phase
            + " | isPageIndex=" + isPageIndex + " | va=" + va);
        try {
            long value = parseInput(va);
            log.info("[Calc] Skip result | value=" + value
                + (isPageIndex ? " (pageIndex)" : " (vpn=" + (value >> pageOffsetBits)
                    + " offset=" + (value & ((1L << pageOffsetBits) - 1)) + ")"));
            showResult(va, value, isPageIndex);
            journeyMapManager.updateMapAfterCalculator(player);
            wasSkipped.put(player.getUniqueId(), true);
            if (isPageIndex) {
                com.oscity.journey.Journey journey = tracker.getJourney(player);
                String path = journey != null ? "calculator_room." + journey.name() + ".page_index" : null;
                QuestionBank.Question q = path != null ? questionBank.getQuestion(path) : null;
                String pageIndex = q != null ? q.options.get(q.correctAnswer) : "?";
                tracker.setVar(player, "pageIndex", pageIndex);
                journeyMapManager.updateMap(player);
                String summary = buildPageIndexSummary(pageIndex);
                player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_skipped", "{summary}", summary));
                askJourneyQuestion(player, "page_index");
            } else {
                String summary = buildChatSummary(value);
                player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_skipped", "{summary}", summary));
                askJourneyQuestion(player, "hex_to_binary");
            }
        } catch (NumberFormatException e) {
            setCalcError(va);
            player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("errors.calculator.parse_error_va", "{va}", va));
        }
    }

    private void askJourneyQuestion(Player player, String questionType) {
        com.oscity.journey.Journey journey = tracker.getJourney(player);
        if (journey == null) {
            log.warning("[Calc] askJourneyQuestion: no journey for " + player.getName());
            hasCalculated.put(player.getUniqueId(), true);
            return;
        }
        String path = "calculator_room." + journey.name() + "." + questionType;
        log.info("[Calc] " + player.getName() + " asking " + path);
        askCalcQuestion(player, path);
    }

    private void askCalcQuestion(Player player, String questionPath) {
        QuestionBank.Question q = questionBank.getQuestion(questionPath);
        if (q == null) {
            log.warning("[Calc] No question found for path: " + questionPath + " — skipping quiz");
            hasCalculated.put(player.getUniqueId(), true);
            return;
        }
        Map<String, String> vars = tracker.getVars(player);
        String questionText = q.text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            questionText = questionText.replace("{" + e.getKey() + "}", e.getValue());
        }
        player.sendMessage("§6[Quiz] §e" + questionText);
        player.sendMessage(q.formatOptions(vars));
        pendingCalcVerify.put(player.getUniqueId(), questionPath);
    }

    @EventHandler
    public void onCalcVerifyChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        String questionPath = pendingCalcVerify.get(player.getUniqueId());
        if (questionPath == null) return;
        event.setCancelled(true);
        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            QuestionBank.Question q = questionBank.getQuestion(questionPath);
            if (q == null) {
                pendingCalcVerify.remove(player.getUniqueId());
                hasCalculated.put(player.getUniqueId(), true);
                return;
            }
            log.info("[Calc] " + player.getName() + " answered '" + msg
                + "' for '" + questionPath + "' | correct=" + q.checkAnswer(msg)
                + " | phase=" + tracker.getPhase(player));
            if (q.checkAnswer(msg)) {
                pendingCalcVerify.remove(player.getUniqueId());
                hasCalculated.put(player.getUniqueId(), true);
                player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_quiz_correct"));
                boolean skipped = wasSkipped.getOrDefault(player.getUniqueId(), false);
                wasSkipped.remove(player.getUniqueId());
                String phase = tracker.getPhase(player);
                if ("calculator_from_tlb".equals(phase)) {
                    String dialoguePath = skipped
                        ? "rooms.calculator_room.from_tlb_skip"
                        : "rooms.calculator_room.from_tlb_after_quiz";
                    log.info("[Calc] " + player.getName() + " post-quiz dialogue: " + dialoguePath);
                    dialogueManager.speakInstant(player, dialoguePath, tracker.getVars(player));
                    tracker.setPhase(player, "calculator_from_tlb_done");
                } else if ("calculator_from_lazy_loading".equals(phase)) {
                    tracker.setPhase(player, "calculator_from_lazy_loading_done");
                }
            } else {
                player.sendMessage("§c" + q.wrongFeedback);
                Map<String, String> vars = tracker.getVars(player);
                String questionText = q.text;
                for (Map.Entry<String, String> e : vars.entrySet()) {
                    questionText = questionText.replace("{" + e.getKey() + "}", e.getValue());
                }
                player.sendMessage("§6[Quiz] §e" + questionText);
                player.sendMessage(q.formatOptions(vars));
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (hopperLocation == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInv = event.getView().getTopInventory();
        log.info("[Calc] onInventoryClick: player=" + player.getName()
            + " topInvType=" + topInv.getType()
            + " holder=" + (topInv.getHolder() != null ? topInv.getHolder().getClass().getSimpleName() : "null")
            + " clickType=" + event.getClick()
            + " slotType=" + event.getSlotType());
        if (!isHopperInventory(topInv)) return;

        // Delay 1 tick so the item has actually moved into the hopper
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkHopper(player), 1L);
    }

    private boolean isHopperInventory(Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof org.bukkit.block.Hopper)) {
            log.info("[Calc] isHopperInventory: holder is not Hopper (was "
                + (holder != null ? holder.getClass().getSimpleName() : "null") + ")");
            return false;
        }
        Location loc = ((org.bukkit.block.Hopper) holder).getLocation();
        boolean match = hopperLocation != null
            && loc.getBlockX() == hopperLocation.getBlockX()
            && loc.getBlockY() == hopperLocation.getBlockY()
            && loc.getBlockZ() == hopperLocation.getBlockZ();
        log.info("[Calc] isHopperInventory: hopperAt=(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
            + ") configAt=(" + (hopperLocation != null ? hopperLocation.getBlockX() + "," + hopperLocation.getBlockY() + "," + hopperLocation.getBlockZ() : "null")
            + ") match=" + match);
        return match;
    }

    private void checkHopper(Player player) {
        if (calculating.getOrDefault(player.getUniqueId(), false)) return;
        String hopperPhase = tracker.getPhase(player);
        log.info("[Calc] checkHopper: player=" + player.getName() + " phase=" + hopperPhase);
        if ("calculator_from_tlb_done".equals(hopperPhase) || "calculator_from_lazy_loading_done".equals(hopperPhase)) return;

        Block block = hopperLocation.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Hopper)) {
            log.warning("[Calc] checkHopper: block at hopper location is not a Hopper (was " + block.getType() + ")");
            return;
        }
        org.bukkit.block.Hopper hopper = (org.bukkit.block.Hopper) block.getState();

        ItemStack book = null;
        int bookSlot = -1;
        for (int i = 0; i < hopper.getInventory().getSize(); i++) {
            ItemStack item = hopper.getInventory().getItem(i);
            log.info("[Calc] checkHopper slot " + i + ": " + (item != null ? item.getType() : "empty"));
            if (item != null && item.getType() == Material.WRITABLE_BOOK) {
                book = item; bookSlot = i; break;
            }
        }
        if (book == null) {
            log.info("[Calc] checkHopper: no WRITABLE_BOOK found in hopper");
            return;
        }

        BookMeta meta = (BookMeta) book.getItemMeta();
        List<Component> pages = meta != null ? meta.pages() : java.util.Collections.emptyList();
        if (pages.isEmpty()) {
            player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("errors.calculator.empty_page"));
            return;
        }
        String pageText = PlainTextComponentSerializer.plainText().serialize(pages.get(0)).trim();
        String input    = pageText.split("\n")[0].trim();
        if (input.isEmpty()) {
            player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("errors.calculator.empty_page"));
            return;
        }

        hopper.getInventory().setItem(bookSlot, null);
        player.getInventory().addItem(book);

        String phase = tracker.getPhase(player);
        log.info("[Calc] " + player.getName() + " HOPPER | phase=" + phase + " | input=" + input);
        startCalculation(player, input, phase);
    }

    private void startCalculation(Player player, String input, String phase) {
        calculating.put(player.getUniqueId(), true);
        setCalcCalculating();
        player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_processing", "{input}", input));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            calculating.remove(player.getUniqueId());
            try {
                long value   = parseInput(input);
                boolean pageIdx = "calculator_from_lazy_loading".equals(phase);
                log.info("[Calc] " + player.getName() + " result | phase=" + phase
                    + " | input=" + input + " | value=" + value
                    + (pageIdx ? " (pageIndex)" : " (vpn=" + (value >> pageOffsetBits)
                        + " offset=" + (value & ((1L << pageOffsetBits) - 1)) + ")"));
                showResult(input, value, pageIdx);
                // Only update VPN and offset, NOT PFN (PFN comes from TLB or page table)
                journeyMapManager.updateMapAfterCalculator(player);

                if (pageIdx) {
                    com.oscity.journey.Journey journey = tracker.getJourney(player);
                    String path = journey != null ? "calculator_room." + journey.name() + ".page_index" : null;
                    QuestionBank.Question q = path != null ? questionBank.getQuestion(path) : null;
                    String pageIndex = q != null ? q.options.get(q.correctAnswer) : "?";
                    tracker.setVar(player, "pageIndex", pageIndex);
                    journeyMapManager.updateMap(player);
                    String summary = buildPageIndexSummary(pageIndex);
                    player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_result", "{summary}", summary));
                    askJourneyQuestion(player, "page_index");
                } else {
                    String summary = buildChatSummary(value);
                    player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("feedback.calculator_result", "{summary}", summary));
                    askJourneyQuestion(player, "hex_to_binary");
                }
            } catch (NumberFormatException e) {
                log.warning("[Calc] " + player.getName() + " parse error | phase=" + phase + " | input=" + input);
                setCalcError(input);
                player.sendMessage(((com.oscity.OSCity) plugin).getConfigManager().getMessage("errors.calculator.parse_error", "{input}", input));
            }
        }, 100L); // 5 seconds = 100 ticks
    }

    private long parseInput(String raw) throws NumberFormatException {
        String s = raw.trim();
        
        // Handle division expressions (e.g., "0xE/0x10" for page index)
        if (s.contains("/")) {
            String[] parts = s.split("/");
            if (parts.length == 2) {
                long numerator = parseSingleValue(parts[0].trim());
                long denominator = parseSingleValue(parts[1].trim());
                if (denominator == 0) {
                    throw new NumberFormatException("Division by zero");
                }
                return numerator / denominator;
            }
        }
        
        return parseSingleValue(s);
    }

    private long parseSingleValue(String s) throws NumberFormatException {
        if (s.startsWith("0x") || s.startsWith("0X")) return Long.parseLong(s.substring(2), 16);
        if (s.startsWith("0b") || s.startsWith("0B")) return Long.parseLong(s.substring(2), 2);
        if (s.matches("[01]+") && s.length() >= 4)    return Long.parseLong(s, 2);
        if (s.matches("[0-9A-Fa-f]+"))                return Long.parseLong(s, 16);
        return Long.parseLong(s);
    }

    private String buildChatSummary(long value) {
        String binary = formatNibbles(value, pageOffsetBits * 2);
        long vpn = value >> pageOffsetBits;
        long off = value & ((1L << pageOffsetBits) - 1);
        String vpnHex = "0x" + Long.toHexString(vpn).toUpperCase();
        String offHex = "0x" + Long.toHexString(off).toUpperCase();
        String vpnBin = formatNibbles(vpn, pageOffsetBits);
        String offBin = formatNibbles(off, pageOffsetBits);
        return "Binary=" + binary
            + ", VPN=" + vpnHex + " (" + vpnBin + ")"
            + ", Offset=" + offHex + " (" + offBin + ")";
    }

    private String buildPageIndexSummary(String value) {
        try {
            long v = Long.parseLong(value);
            return "Page Index=" + v + " (0x" + Long.toHexString(v).toUpperCase() + ")";
        } catch (NumberFormatException e) {
            return "Page Index=" + value;
        }
    }

    private void updateInstructionFrames(String phase) {
        if (instrFrames.size() < 6) return;
        if ("calculator_from_tlb".equals(phase) || "calculator_from_tlb_done".equals(phase)) {
            setFrame(instrFrames.get(0), "= HOW TO USE =", " CALCULATOR ", "", "HEX->VPN+OFFSET");
            setFrame(instrFrames.get(1), "   STEP 1:   ", "Write your hex", "VA in the book", "from chest");
            setFrame(instrFrames.get(2), "   STEP 2:   ", "Place the book", "in the hopper", "");
            setFrame(instrFrames.get(3), "The result", "will be shown", "over the hopper", "");
            setFrame(instrFrames.get(4), "First 4 bits:", "= your VPN", "", "");
            setFrame(instrFrames.get(5), "Last 4 bits:", "= your offset", "", "");
        } else {
            setFrame(instrFrames.get(0), "= HOW TO USE =", " PAGE INDEX ", "", "CALCULATOR");
            setFrame(instrFrames.get(1), "   STEP 1:   ", "Write hex OR", "binary", "in a book");
            setFrame(instrFrames.get(2), "   STEP 2:   ", "Place book in", "the hopper", "");
            setFrame(instrFrames.get(3), " IMPORTANT!  ", "Use ONE form:", " all HEX  OR", " all BINARY");
            setFrame(instrFrames.get(4), "  FORMULA:   ", "PAGE INDEX" , "= VA / PAGE SIZE","");
            setFrame(instrFrames.get(5), "   TIP:      ", "Use hex", "values only,", "to calculate");
        }
    }

    private void setCalcAwaiting() {
        if (calcFrames.size() < 6) return;
        setFrameCentered(calcFrames.get(0), "[ STEP 1 ]", "Get the book", "from the chest", "on your left");
        setFrameLarge(   calcFrames.get(1), "AWAITING", "", "", "");
        setFrameCentered(calcFrames.get(2), "[ STEP 2 ]", "Write your", "hex VA in", "the book");
        setFrameCentered(calcFrames.get(3), "[ STEP 3 ]", "Place book", "in hopper", "above this");
        setFrameLarge(   calcFrames.get(4), "INPUT...", "", "", "");
        setFrameCentered(calcFrames.get(5), "[ RESULT ]", "Will appear", "on this wall", "");
    }

    private void setCalcCalculating() {
        if (calcFrames.size() < 6) return;
        clearFrame(calcFrames.get(0));
        setFrameLarge(   calcFrames.get(1), "WORKING", "", "", "");
        clearFrame(calcFrames.get(2));
        setFrameCentered(calcFrames.get(3), "", "Please wait", "5 seconds...", "");
        setFrameLarge(   calcFrames.get(4), "WAIT...", "", "", "");
        clearFrame(calcFrames.get(5));
    }

    private void setCalcError(String input) {
        if (calcFrames.size() < 6) return;
        setFrame(calcFrames.get(0), "=== ERROR ===", "Bad input:",
            shorten(input, 13), "Try again");
        for (int i = 1; i < calcFrames.size(); i++) clearFrame(calcFrames.get(i));
    }

    private void showResult(String input, long value, boolean isPageIndex) {
        if (calcFrames.size() < 6) return;

        if (isPageIndex) {
            String hexVal = "0x" + Long.toHexString(value).toUpperCase();
            setFrame(calcFrames.get(0), "== RESULT ==", "Input: " + shorten(input, 11), "", "");
            setFrame(calcFrames.get(1), "PAGE INDEX:", String.valueOf(value), hexVal, "");
            setFrame(calcFrames.get(2), "Added to log!", "", "", "");
            clearFrame(calcFrames.get(3));
            clearFrame(calcFrames.get(4));
            clearFrame(calcFrames.get(5));
        } else {
            int totalBits = pageOffsetBits * 2;
            String binary = formatNibbles(value, totalBits);
            long vpn = value >> pageOffsetBits;
            long off = value & ((1L << pageOffsetBits) - 1);
            String vpnBin = formatNibbles(vpn, pageOffsetBits);
            String offBin = formatNibbles(off, pageOffsetBits);
            String vpnHex = "0x" + Long.toHexString(vpn).toUpperCase();
            String offHex = "0x" + Long.toHexString(off).toUpperCase();

            setFrame(calcFrames.get(0), "== RESULT ==", "VA: " + shorten(input, 11), "", "");
            setFrame(calcFrames.get(1), "Binary:", binary, "", "");
            setFrame(calcFrames.get(2), "VPN: " + vpnBin, "= " + vpn + "  " + vpnHex, "", "");
            setFrame(calcFrames.get(3), "Offset: " + offBin, "= " + off + "  " + offHex, "", "");
            setFrame(calcFrames.get(4), "Added to log!", "", "", "");
            clearFrame(calcFrames.get(5));
        }
    }

    private void setFrameCentered(Location loc, String l1, String l2, String l3, String l4) {
        setFrame(loc, l1, l2, l3, l4, true, 1);
    }

    private void setFrameLarge(Location loc, String l1, String l2, String l3, String l4) {
        setFrame(loc, l1, l2, l3, l4, true, 2);
    }

    private void setFrame(Location loc, String l1, String l2, String l3, String l4) {
        setFrame(loc, l1, l2, l3, l4, false, 1);
    }

    private void setFrame(Location loc, String l1, String l2, String l3, String l4, boolean centered) {
        setFrame(loc, l1, l2, l3, l4, centered, 1);
    }

    /**
     * Write 4 lines to a frame. Tries wall sign first; falls back to item frame map.
     * centered=true centres the text block; scale=2 renders at 2× pixel size.
     */
    private void setFrame(Location loc, String l1, String l2, String l3, String l4,
                          boolean centered, int scale) {
        Block block = loc.getBlock();
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            sign.getSide(Side.FRONT).line(0, Component.text(l1, NamedTextColor.BLACK));
            sign.getSide(Side.FRONT).line(1, Component.text(l2, NamedTextColor.BLACK));
            sign.getSide(Side.FRONT).line(2, Component.text(l3, NamedTextColor.BLACK));
            sign.getSide(Side.FRONT).line(3, Component.text(l4, NamedTextColor.BLACK));
            sign.update(true);
            return;
        }

        ItemFrame frame = findItemFrameAt(loc);
        if (frame != null) {
            renderTextToFrame(frame, loc, l1, l2, l3, l4, centered, scale);
            return;
        }

        plugin.getLogger().warning("CalculatorListener: no sign or item frame at "
            + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
            + " (block=" + block.getType() + ")");
    }

    private ItemFrame findItemFrameAt(Location loc) {
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        java.util.Collection<Entity> nearby = loc.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0);

        ItemFrame closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity entity : nearby) {
            if (entity instanceof ItemFrame) {
                double dist = entity.getLocation().distanceSquared(center);
                if (dist < minDist) {
                    minDist = dist;
                    closest = (ItemFrame) entity;
                }
            }
        }
        if (closest == null) {
            plugin.getLogger().warning("CalculatorListener: no item frame found near ("
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")");
        }
        return closest;
    }

    /**
     * Render up to 4 lines of text onto a map item placed in the given item frame.
     * Reuses the same MapView across updates to keep the same map ID.
     * scale=1 → normal text; scale=2 → 2× enlarged pixels for big status displays.
     */
    @SuppressWarnings("deprecation")
    private void renderTextToFrame(ItemFrame frame, Location key,
                                   String l1, String l2, String l3, String l4,
                                   boolean centered, int scale) {
        MapView view = frameMapViews.get(key);
        if (view == null) {
            view = Bukkit.createMap(frame.getWorld());
            view.setScale(MapView.Scale.CLOSEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            frameMapViews.put(key, view);
        }

        view.getRenderers().clear();
        final String tl1 = l1, tl2 = l2, tl3 = l3, tl4 = l4;
        final boolean tc = centered;
        final int ts = scale;
        final byte bg = MapPalette.matchColor(new Color(30, 30, 50));
        final byte fg = MapPalette.matchColor(new Color(255, 255, 255));
        view.addRenderer(new MapRenderer(false) {
            @Override
            public void render(MapView mapView, MapCanvas canvas, Player player) {
                for (int px = 0; px < 128; px++)
                    for (int py = 0; py < 128; py++)
                        canvas.setPixel(px, py, bg);

                String[] lines = {tl1, tl2, tl3, tl4};
                int nonEmpty = 0;
                for (String l : lines) if (!l.trim().isEmpty()) nonEmpty++;

                if (ts > 1) {
                    int lineSpacing = 8 * ts + 2;
                    int y = tc ? Math.max(4, (128 - nonEmpty * lineSpacing) / 2) : 4;
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            int x = tc ? Math.max(2, (128 - textWidth(line) * ts) / 2) : 4;
                            drawScaledText(canvas, x, y, line, ts, fg);
                            y += lineSpacing;
                        }
                    }
                } else {
                    final int lineSpacing = tc ? 18 : 14;
                    int y = tc ? Math.max(8, (128 - nonEmpty * lineSpacing) / 2) : 8;
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            int x = tc ? Math.max(2, (128 - textWidth(line)) / 2) : 4;
                            canvas.drawText(x, y, MinecraftFont.Font, line);
                            y += lineSpacing;
                        }
                    }
                }
            }
        });

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(view);
        mapItem.setItemMeta(meta);
        frame.setItem(mapItem, false);
    }

    private void clearFrame(Location loc) { setFrame(loc, "", "", "", ""); }

    /**
     * Format a number as binary, grouped in nibbles (e.g. "1010 1011").
     * Padded to at least minBits bits (rounded up to multiple of 4).
     */
    private String formatNibbles(long value, int minBits) {
        int rawLen = Long.toBinaryString(value).length();
        int padded = ((Math.max(rawLen, minBits) + 3) / 4) * 4;
        String bin = String.format("%" + padded + "s", Long.toBinaryString(value)).replace(' ', '0');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bin.length(); i += 4) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(bin, i, Math.min(i + 4, bin.length()));
        }
        return sb.toString();
    }

    private String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Returns the pixel width of a string rendered with MinecraftFont. */
    private int textWidth(String s) {
        if (s == null || s.isEmpty()) return 0;
        int w = 0;
        for (char c : s.toCharArray()) {
            MapFont.CharacterSprite cs = MinecraftFont.Font.getChar(c);
            if (cs != null) w += cs.getWidth() + 1;
        }
        return Math.max(0, w - 1);
    }

    /**
     * Draws text onto a MapCanvas at (startX, startY) with each font pixel
     * blown up to a scale×scale block — giving visually larger text.
     */
    @SuppressWarnings("deprecation")
    private void drawScaledText(MapCanvas canvas, int startX, int startY,
                                String text, int scale, byte color) {
        int x = startX;
        for (char c : text.toCharArray()) {
            MapFont.CharacterSprite cs = MinecraftFont.Font.getChar(c);
            if (cs == null) { x += 4 * scale; continue; }
            for (int row = 0; row < cs.getHeight(); row++) {
                for (int col = 0; col < cs.getWidth(); col++) {
                    if (cs.get(row, col)) {
                        for (int sy = 0; sy < scale; sy++) {
                            for (int sx = 0; sx < scale; sx++) {
                                int px = x + col * scale + sx;
                                int py = startY + row * scale + sy;
                                if (px >= 0 && px < 128 && py >= 0 && py < 128)
                                    canvas.setPixel(px, py, color);
                            }
                        }
                    }
                }
            }
            x += (cs.getWidth() + 1) * scale;
        }
    }
}
