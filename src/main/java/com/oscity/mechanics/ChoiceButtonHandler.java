package com.oscity.mechanics;

import com.oscity.OSCity;
import com.oscity.content.DialogueManager;
import com.oscity.content.QuestionBank;
import com.oscity.core.GuardianInteractionHandler;
import com.oscity.gamification.ProgressTracker;
import com.oscity.mode.PlayerMode;
import com.oscity.persistence.SQLiteStudyDatabase;
import com.oscity.journey.Journey;
import com.oscity.journey.JourneyManager;
import com.oscity.session.JourneyTracker;
import com.oscity.core.KernelGuardian;
import com.oscity.world.LocationRegistry;
import com.oscity.world.RoomRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChoiceButtonHandler implements Listener {

    private final OSCity plugin;
    private final JourneyTracker tracker;
    private final DialogueManager dialogue;
    private final QuestionBank questionBank;
    private final ProgressTracker progress;
    private final LocationRegistry locationRegistry;
    private final CalculatorListener calculatorListener;
    private final SwapClockManager swapClockManager;
    private final JourneyMapManager journeyMapManager;
    private final PageTableManager pageTableManager;
    private GuardianInteractionHandler guardianHandler;

    public void setGuardianHandler(GuardianInteractionHandler handler) {
        this.guardianHandler = handler;
    }

    private final Map<Location, String> buttons = new HashMap<>();

    private final Map<UUID, PendingQuiz> pendingQuiz = new HashMap<>();
    private final Map<UUID, Integer> pendingTerminalPath = new HashMap<>();
    private final Map<UUID, Integer> prevTerminalStep = new HashMap<>();
    private final Set<UUID> randomJourneyChoice = new HashSet<>();
    private final Set<UUID> pteChamberDialogueSent = new HashSet<>();
    private final Set<UUID> diskBookDialogueSent = new HashSet<>();

    private static class PendingQuiz {
        final String questionPath;
        final String onCorrectPhase;
        final String onCorrectDialoguePath;
        PendingQuiz(String questionPath, String onCorrectPhase, String onCorrectDialoguePath) {
            this.questionPath = questionPath;
            this.onCorrectPhase = onCorrectPhase;
            this.onCorrectDialoguePath = onCorrectDialoguePath;
        }
    }

    public ChoiceButtonHandler(OSCity plugin, JourneyTracker tracker,
                               DialogueManager dialogue, QuestionBank questionBank,
                               ProgressTracker progress, LocationRegistry locationRegistry,
                               CalculatorListener calculatorListener,
                               SwapClockManager swapClockManager,
                               JourneyMapManager journeyMapManager,
                               PageTableManager pageTableManager) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.dialogue = dialogue;
        this.questionBank = questionBank;
        this.progress = progress;
        this.locationRegistry = locationRegistry;
        this.calculatorListener = calculatorListener;
        this.swapClockManager = swapClockManager;
        this.journeyMapManager = journeyMapManager;
        this.pageTableManager = pageTableManager;
        loadButtons();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ChoiceButtonHandler registered.");
    }

    private void loadButtons() {
        buttons.clear();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("choiceButtons");
        if (sec == null) {
            plugin.getLogger().warning("ChoiceButtonHandler: no 'choiceButtons' in config.yml");
        } else {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection btn = sec.getConfigurationSection(key);
                if (btn == null) continue;
                String worldName = btn.getString("world");
                if (worldName == null) continue;
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                Location loc = new Location(world, btn.getInt("x"), btn.getInt("y"), btn.getInt("z"));
                buttons.put(loc, key);
            }
        }

        registerFromTpButtons("cowToRam", "cowToRam");
        registerFromTpButtons("loadingToCalc", "loadingTp");  // loadingToDisk shares same location
        registerFromTpButtons("diskToRam", "diskToRam");

        registerFromTpButtons("gameStart", "gameStart");

        registerFromTpButtons("calcToTlb", "calcToTlb");
        registerFromTpButtons("calcToLazyLoading", "calcToLazyLoading");

        registerFromTpButtons("swapToRam", "swapToRam");

        registerFromTpButtons("pt1ToChamber", "pt1ToChamber");
        registerFromTpButtons("pt2ToChamber", "pt2ToChamber");
        registerFromTpButtons("pt3ToChamber", "pt3ToChamber");

        registerFromTpButtons("endToStart", "endToStart");

        registerFromDoorOpen("toPageTable");
        registerFromDoorOpen("toPageFaultCorridor");
        registerFromDoorOpen("toLazyLoading");
        registerFromDoorOpen("toLazyAllocation");
        registerFromDoorOpen("toAssessmentRoom1");
        registerFromDoorOpen("toAssessmentRoom2");
        registerFromDoorOpen("toInitialFromAssessment1");
        registerFromDoorOpen("toInitialFromAssessment2");
        registerFromDoorOpen("toDepartureGate1");
        registerFromDoorOpen("toDepartureGate2");
        registerFromDoorOpen("toInitialFromGate1");
        registerFromDoorOpen("toInitialFromGate2");

        plugin.getLogger().info("ChoiceButtonHandler: loaded " + buttons.size() + " choice/smart buttons.");
    }

    private void registerFromTpButtons(String tpKey, String smartKey) {
        ConfigurationSection btn = plugin.getConfig().getConfigurationSection("tpButtons." + tpKey);
        if (btn == null) {
            plugin.getLogger().warning("[CalcContinue] No config for tpButtons." + tpKey);
            return;
        }
        String worldName = btn.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[Teleport] tpButtons." + tpKey + " missing world for smart button " + smartKey);
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[Teleport] tpButtons." + tpKey + " refers to missing world: " + worldName);
            return;
        }
        Location loc = new Location(world, btn.getInt("x"), btn.getInt("y"), btn.getInt("z"));
        buttons.put(loc, smartKey);
    }

    private void registerFromDoorOpen(String key) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("doorOpen." + key);
        if (sec == null) {
            plugin.getLogger().warning("[DoorOpen] No config for doorOpen." + key);
            return;
        }
        String worldName = sec.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[DoorOpen] doorOpen." + key + " missing world");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DoorOpen] doorOpen." + key + " refers to missing world: " + worldName);
            return;
        }
        Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        buttons.put(loc, key);
    }

    /**
     * LOW priority so this fires before TeleportManager (NORMAL).
     * Cancels the event for any button in our map, preventing double-handling.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onButtonPress(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        String blockTypeName = event.getClickedBlock().getType().name();
        if (!blockTypeName.endsWith("_BUTTON") && !blockTypeName.equals("LEVER")) return;

        Location clicked = event.getClickedBlock().getLocation();
        if (plugin.getConfigManager().isDebugMode()) {
            event.getPlayer().sendMessage("§7[Click] " + event.getClickedBlock().getType()
                + " x=" + clicked.getBlockX() + " y=" + clicked.getBlockY() + " z=" + clicked.getBlockZ());
        }
        String buttonKey = findButton(clicked);
        if (buttonKey == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (guardianHandler != null) guardianHandler.clearPendingState(player.getUniqueId());
        String phase = tracker.getPhase(player);
        Journey journey = tracker.getJourney(player);
        Map<String, String> vars = tracker.getVars(player);
        plugin.getLogger().info("[Button] " + player.getName() + " pressed '" + buttonKey
            + "' | phase=" + phase
            + " | journey=" + (journey != null ? journey.name() : "none")
            + " | mode=" + tracker.getMode(player));

        if ("ramMix".equals(buttonKey)) {
            handleRamMixButton(player, phase, journey);
            return;
        }

        if ("perTerminate".equals(buttonKey)) {
            handlePermExitButton(player, phase, journey);
            return;
        }

        if ("cowToRam".equals(buttonKey)) {
            handleCowToRamButton(player, phase);
            return;
        }

        if ("loadingTp".equals(buttonKey)) {
            handleLoadingTpButton(player, phase);
            return;
        }

        if ("diskToRam".equals(buttonKey)) {
            handleDiskToRamButton(player, phase, journey);
            return;
        }

        if ("skipCalc".equals(buttonKey)) {
            if (tracker.getMode(player) == PlayerMode.ADVENTURER) {
                dialogue.speakInstant(player, "guardian.meta.chose_alone", null);
                return;
            }
            if (ChoiceButtonRules.isCalculatorContinueDonePhase(phase)) return;
            calculatorListener.skipCalculation(player);
            return;
        }

        if ("gameStart".equals(buttonKey)) {
            handleModeStartButton(player, "gameStart", "tlbSpawn");
            return;
        }

        if ("endToStart".equals(buttonKey)) {
            handleEndToStart(player);
            return;
        }

        if ("toAssessmentRoom1".equals(buttonKey) || "toAssessmentRoom2".equals(buttonKey)) {
            openDoor("toAssessmentRoom1");
            openDoor("toAssessmentRoom2");
            moveGuardianAndFireRoomEntry(player, "Assessment Room", 200L);
            return;
        }
        if ("toDepartureGate1".equals(buttonKey) || "toDepartureGate2".equals(buttonKey)) {
            openDoor("toDepartureGate1");
            openDoor("toDepartureGate2");
            moveGuardianAndFireRoomEntry(player, "Departure Gate", 200L);
            return;
        }
        if ("toInitialFromAssessment1".equals(buttonKey) || "toInitialFromAssessment2".equals(buttonKey)) {
            openDoor("toAssessmentRoom1");
            openDoor("toAssessmentRoom2");
            moveGuardianAndFireRoomEntry(player, "Initial Terminal", 200L);
            return;
        }
        if ("toInitialFromGate1".equals(buttonKey) || "toInitialFromGate2".equals(buttonKey)) {
            if ("terminal_journey_chosen".equals(tracker.getPhase(player))) {
                player.sendMessage(plugin.getConfigManager().getMessage("errors.departure_gate.journey_already_selected"));
                return;
            }
            openDoor("toDepartureGate1");
            openDoor("toDepartureGate2");
            moveGuardianAndFireRoomEntry(player, "Initial Terminal", 200L);
            return;
        }

        if ("summariseJourney".equals(buttonKey)) {
            handleSummariseJourney(player);
            return;
        }

        if (buttonKey.startsWith("frame") && buttonKey.endsWith("btn")) {
            try {
                int frameNum = Integer.parseInt(buttonKey.substring(5, buttonKey.length() - 3));
                if (frameNum >= 1 && frameNum <= 6) {
                    swapClockManager.handleFrameButton(player, frameNum);
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        if ("hit".equals(buttonKey)) {
            if ("tlb_hit_quiz_done".equals(tracker.getPhase(player))) {
                if (isCarryingTLBMap(player)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.put_map_back"));
                    return;
                }
                tracker.setPhase(player, "ram_tlb_hit_access");
                teleportPlayer(player, "ramRoom");
            } else {
                handleTLBDecision(player, true);
            }
            return;
        }
        if ("miss".equals(buttonKey)) {
            handleTLBDecision(player, false);
            return;
        }

        if ("calcToTlb".equals(buttonKey) || "calcToLazyLoading".equals(buttonKey)) {
            plugin.getLogger().info("[CalcContinue] Button press detected: " + buttonKey);
            handleCalculatorContinue(player, buttonKey);
            return;
        }

        if ("swapToRam".equals(buttonKey)) {
            handleSwapToRam(player);
            return;
        }

        if ("pt1ToChamber".equals(buttonKey) || "pt2ToChamber".equals(buttonKey)
                || "pt3ToChamber".equals(buttonKey)) {
            handlePageTableToChamber(player, buttonKey);
            return;
        }

        if ("toPageFaultCorridor".equals(buttonKey)
                || "toLazyLoading".equals(buttonKey)
                || "toLazyAllocation".equals(buttonKey)) {
            handleDoorOpenButton(player, buttonKey, journey, phase);
            return;
        }

        switch (phase) {
            case "permission_decision":
                handlePermissionDecision(player, buttonKey, journey, vars);
                break;
            case "page_fault_type":
                handlePageFaultType(player, buttonKey, journey, vars);
                break;
            case "lazy_alloc_decision":
                handleLazyAllocDecision(player, buttonKey, journey, vars);
                break;
            case "lazy_alloc_cow":
                handleLazyAllocCow(player, buttonKey, journey, vars);
                break;
            case "lazy_alloc_before_tp":
                if ("btnLazyAlloc".equals(buttonKey)) {
                    teleportPlayer(player, "cowRoom");
                }
                break;
            case "cow_decision":
                handleCowDecision(player, buttonKey, journey, vars);
                break;
            case "tlb_miss_correct":
                if ("toPageTable".equals(buttonKey)) {
                    if (journey == Journey.LUCKY) {
                        if (!"ram_allow_access".equals(phase) && !"tlb_miss_quiz".equals(phase)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.answer_quiz_first"));
                            return;
                        }
                    } else {
                        if (!calculatorListener.hasPlayerCalculated(player)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.visit_calculator_first"));
                            return;
                        }

                        if ("tlb_spawn".equals(phase) || "tlb_after_calculator".equals(phase)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.decide_hit_or_miss"));
                            return;
                        }

                        // Silently block — quiz is already visible in chat, no error needed.
                        if ("tlb_miss_quiz".equals(phase) && pendingQuiz.containsKey(player.getUniqueId())) {
                            return;
                        }
                    }
                    openDoor("tlbToPt");
                    moveGuardianAndFireRoomEntry(player, "Page Table Library - Page Directory", 200L);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Detects when a player places the correct book/map into a RAM room chest or hopper and closes it.
     * Using InventoryCloseEvent means every placement method (click, shift-click, hotbar swap, drag)
     * is handled — we simply check what is in the container when the player leaves it.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onRAMChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Location loc;
        org.bukkit.inventory.InventoryHolder holder = event.getInventory().getHolder();
        plugin.getLogger().info("[RAMChest] Close event | holder=" + (holder != null ? holder.getClass().getSimpleName() : "null"));
        if (holder instanceof org.bukkit.block.Chest chest) {
            loc = chest.getLocation();
        } else if (holder instanceof org.bukkit.block.Hopper hopper) {
            loc = hopper.getLocation();
        } else {
            return;
        }
        String phase = tracker.getPhase(player);
        String pfn = tracker.getVar(player, "pfn");
        plugin.getLogger().info("[RAMChest] phase=" + phase + " pfn=" + pfn
            + " loc=(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")");
        if (!isCorrectRamChest(player, loc, phase)) {
            plugin.getLogger().info("[RAMChest] isCorrectRamChest=false");
            return;
        }

        plugin.getLogger().info("[RAMChest] Container closed, phase=" + phase);

        for (ItemStack item : event.getInventory().getContents()) {
            if (isValidRAMBook(player, phase, item)) {
                applyRAMBookTransition(player, phase);
                return;
            }
        }
        plugin.getLogger().info("[RAMChest] No valid book found in container on close, phase=" + phase);
    }

    /**
     * Detects when a player throws an item (Q key) above a RAM room hopper and the hopper picks it up.
     * This fires without the player opening any inventory, so InventoryCloseEvent won't catch it.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onRAMHopperPickup(org.bukkit.event.inventory.InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Hopper hopper)) return;
        // Server runs single-player; get the only online player
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        String phase = tracker.getPhase(player);
        if (!isCorrectRamChest(player, hopper.getLocation(), phase)) return;
        ItemStack item = event.getItem().getItemStack();
        plugin.getLogger().info("[RAMHopper] Hopper pickup, phase=" + phase + ", item=" + item.getType());

        if (isValidRAMBook(player, phase, item)) {
            // Delay by 1 tick so the hopper finishes pulling the item before we process
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyRAMBookTransition(player, phase), 1L);
        }
    }

    private boolean isValidRAMBook(Player player, String phase, ItemStack item) {
        if (item == null) return false;

        if (item.getType() == org.bukkit.Material.FILLED_MAP) {
            return "swap_after_eviction".equals(phase);
        }

        if (item.getType() == org.bukkit.Material.WRITTEN_BOOK && item.hasItemMeta()
                && ("ram_disk_swap".equals(phase) || "swap_after_eviction".equals(phase) || "ram_after_swap_lazy_loading".equals(phase))) {
            org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
            String displayName = bookMeta.getDisplayName();
            String title = bookMeta.getTitle();
            boolean isSwapSlot0 = (displayName != null && (displayName.contains("Swap Slot 0") || displayName.contains("SwapSlot0")))
                    || (title != null && (title.contains("Swap Slot 0") || title.contains("SwapSlot0")));
            boolean isGrimoirePage4 = (displayName != null && displayName.contains("mystical_grimoire.spells page 4"))
                    || (title != null && title.contains("mystical_grimoire.spells page 4"));
            plugin.getLogger().info("[RAMChest] WRITTEN_BOOK isSwapSlot0=" + isSwapSlot0 + " isGrimoirePage4=" + isGrimoirePage4);
            return isSwapSlot0 || isGrimoirePage4;
        }

        if ((item.getType() == org.bukkit.Material.WRITTEN_BOOK || item.getType() == org.bukkit.Material.WRITABLE_BOOK)
                && item.hasItemMeta()
                && ("ram_after_cow".equals(phase) || "swap_after_eviction".equals(phase) || "ram_after_swap_lazy_alloc".equals(phase))) {
            org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
            List<Component> pages = bookMeta.pages();
            if (pages != null && !pages.isEmpty()) {
                String content = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(pages.get(0)).trim();
                plugin.getLogger().info("[RAMChest] COW/ALLOC book content: '" + content + "'");
                return content.toLowerCase().replace(" ", "").contains("hey");
            }
        }

        return false;
    }

    private void applyRAMBookTransition(Player player, String phase) {
        plugin.getLogger().info("[RAMChest] Applying transition for phase=" + phase);

        if ("ram_disk_swap".equals(phase) || "swap_after_eviction".equals(phase) || "ram_after_swap_lazy_loading".equals(phase)) {
            tracker.setVar(player, "swapBookPlaced", "true");
            if ("ram_disk_swap".equals(phase)) {
                tracker.setVar(player, "ptePresent", "1");
                tracker.setVar(player, "pteRead", "1");
                tracker.setVar(player, "pteWrite", "1");
                tracker.setVar(player, "pteReadOnly", "0");
                tracker.setVar(player, "pteInSwap", "0");
                pageTableManager.updatePteMap(player);
                tracker.setPhase(player, "ram_book_placed_swapped");
                updateSign("ramRoom.mixSign", "RETRY", "INSTRUCTION", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    String pfn = tracker.getVar(player, "pfn");
                    String vpn = tracker.getVar(player, "vpn");
                    player.sendMessage(plugin.getConfigManager().getMessage("system.pte_updated",
                        "{values}", "PRESENT=1, PFN=" + pfn + ", IN_SWAP=0"));
                    player.sendMessage(plugin.getConfigManager().getMessage("system.tlb_updated",
                        "{vpn}", vpn, "{pfn}", pfn));
                    dialogue.speak(player, "rooms.ram_room.retry_instruction_page_fault", tracker.getVars(player));
                }, 40L);
            } else if ("ram_after_swap_lazy_loading".equals(phase)) {
                tracker.setVar(player, "ptePresent", "1");
                tracker.setVar(player, "pteRead", "1");
                tracker.setVar(player, "pteWrite", "1");
                tracker.setVar(player, "pteReadOnly", "0");
                tracker.setVar(player, "pteInSwap", "0");
                pageTableManager.updatePteMap(player);
                tracker.setPhase(player, "ram_book_placed_lazy_loading");
                updateSign("ramRoom.mixSign", "RETRY", "INSTRUCTION", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    String pfn = tracker.getVar(player, "pfn");
                    String vpn = tracker.getVar(player, "vpn");
                    player.sendMessage(plugin.getConfigManager().getMessage("system.pte_updated",
                        "{values}", "PRESENT=1, PFN=" + pfn));
                    player.sendMessage(plugin.getConfigManager().getMessage("system.tlb_updated",
                        "{vpn}", vpn, "{pfn}", pfn));
                    dialogue.speak(player, "rooms.ram_room.retry_instruction_page_fault", tracker.getVars(player));
                }, 40L);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("feedback.ram_page_placed"));
            }
        } else if ("ram_after_cow".equals(phase) || "ram_after_swap_lazy_alloc".equals(phase)) {
            Journey playerJourney = tracker.getJourney(player);
            if (playerJourney == Journey.PURE_COW) {
                tracker.setPhase(player, "ram_book_placed_pure_cow");
                String pfnCowMsg = tracker.getVar(player, "pfn");
                String vpnCowMsg = tracker.getVar(player, "vpn");
                player.sendMessage(plugin.getConfigManager().getMessage("system.tlb_updated",
                    "{vpn}", vpnCowMsg, "{pfn}", pfnCowMsg));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    dialogue.speak(player, "rooms.ram_room.book_placed_teach_cow", tracker.getVars(player));
                }, 40L);
            } else if (playerJourney == Journey.LAZY_ALLOCATION) {
                String pfnCow = tracker.getVar(player, "pfnCow");
                if (!"?".equals(pfnCow)) {
                    tracker.setVar(player, "pfn", pfnCow);
                }
                tracker.setVar(player, "ptePresent", "1");
                tracker.setVar(player, "pteRead", "1");
                tracker.setVar(player, "pteWrite", "1");
                tracker.setVar(player, "pteReadOnly", "0");
                tracker.setVar(player, "pteUser", "1");
                tracker.setVar(player, "pteKernel", "0");
                tracker.setVar(player, "pteFileBacked", "0");
                tracker.setVar(player, "pteAnon", "0");
                tracker.setVar(player, "pteInSwap", "0");
                pageTableManager.updatePteMap(player);
                tracker.setPhase(player, "ram_book_placed_lazy_allocation");
                String pfnAllocMsg = tracker.getVar(player, "pfn");
                String vpnAllocMsg = tracker.getVar(player, "vpn");
                player.sendMessage(plugin.getConfigManager().getMessage("system.pte_updated",
                    "{values}", "PRESENT=1, PFN=" + pfnAllocMsg + ", WRITE=1"));
                player.sendMessage(plugin.getConfigManager().getMessage("system.tlb_updated",
                    "{vpn}", vpnAllocMsg, "{pfn}", pfnAllocMsg));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    dialogue.speak(player, "rooms.ram_room.book_placed_teach_lazy_alloc", tracker.getVars(player));
                }, 40L);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("feedback.ram_book_placed"));
            }
        } else {
            plugin.getLogger().info("[RAMChest] No phase matched for valid book, phase=" + phase);
        }
    }

    /**
     * Blocks the learnerChest from being opened until the player has chosen a journey
     * (phase == "terminal_journey_chosen").
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest)) return;

        Location chestLoc = chest.getLocation();
        Location learnerChestLoc = getLearnerChestLocation();
        if (learnerChestLoc == null) return;

        if (chestLoc.getBlockX() == learnerChestLoc.getBlockX()
                && chestLoc.getBlockY() == learnerChestLoc.getBlockY()
                && chestLoc.getBlockZ() == learnerChestLoc.getBlockZ()
                && chestLoc.getWorld() != null
                && chestLoc.getWorld().equals(learnerChestLoc.getWorld())) {

            String phase = tracker.getPhase(player);
            if (!RoomAccessRules.canStartJourneyFromTerminal(phase)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("guardian.no_journey_selected"));
            }
        }
    }

    /**
     * Fires the "go to Permission Chamber" dialogue the first time a player closes
     * a page table chest while holding the correct PTE map.
     */
    @EventHandler
    public void onPageTableChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest)) return;

        Location chestLoc = ((org.bukkit.block.Chest) event.getInventory().getHolder()).getLocation();
        if (!isPageTableChest(chestLoc)) return;

        UUID uuid = player.getUniqueId();
        if (pteChamberDialogueSent.contains(uuid)) return;

        if (!"correct_floor".equals(tracker.getPhase(player))) return;

        String vpnHex = tracker.getVar(player, "vpnHex");
        if ("?".equals(vpnHex) || vpnHex == null || vpnHex.isEmpty()) return;
        int correctChestIndex = parseVpnHex(vpnHex) & 0x3;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.FILLED_MAP && item.hasItemMeta()) {
                String displayName = item.getItemMeta().getDisplayName();
                if (displayName != null && displayName.contains("PTE Map")
                        && displayName.contains("Chest" + correctChestIndex)) {
                    pteChamberDialogueSent.add(uuid);
                    tracker.setPhase(player, "acquired_pte");
                    speakIfLearner(player, "rooms.page_table_library.tp_permission_chamber", tracker.getVars(player));
                    break;
                }
            }
        }
    }

    /**
     * Fires the "after book retrieved" dialogue the first time a player closes a
     * disk room chest while holding the correct book for their journey.
     * SWAPPED_OUT: "Swap Slot 0" book. LAZY_LOADING: "mystical_grimoire.spells page 4" book.
     */
    @EventHandler
    public void onDiskChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest)) return;

        Location chestLoc = ((org.bukkit.block.Chest) event.getInventory().getHolder()).getLocation();
        if (!isDiskRoomChest(chestLoc)) return;

        UUID uuid = player.getUniqueId();
        if (diskBookDialogueSent.contains(uuid)) return;

        String phase = tracker.getPhase(player);
        if (!"disk_swap_retrieval".equals(phase) && !"disk_lazy_loading".equals(phase)) return;

        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != org.bukkit.Material.WRITTEN_BOOK || !item.hasItemMeta()) continue;
            org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
            String title = meta.getTitle();
            String displayName = meta.getDisplayName();
            boolean correct = false;
            if ("disk_swap_retrieval".equals(phase)) {
                correct = (title != null && title.contains("Swap Slot 0"))
                       || (displayName != null && displayName.contains("Swap Slot 0"));
            } else {
                correct = (title != null && title.contains("mystical_grimoire.spells page 4"))
                       || (displayName != null && displayName.contains("mystical_grimoire.spells page 4"));
            }
            if (correct) {
                diskBookDialogueSent.add(uuid);
                if ("disk_lazy_loading".equals(phase)) {
                    tracker.setPhase(player, "disk_lazy_loading_after_book");
                } else {
                    tracker.setPhase(player, "disk_swap_retrieval_after_book");
                    speakIfLearner(player, "rooms.disk_room.after_book_retrieved", tracker.getVars(player));
                }
                break;
            }
        }
    }

    private boolean isDiskRoomChest(Location chestLoc) {
        ConfigurationSection diskSec = plugin.getConfig().getConfigurationSection("chests.diskRoom");
        if (diskSec == null) return false;
        for (String key : diskSec.getKeys(false)) {
            ConfigurationSection cs = diskSec.getConfigurationSection(key);
            if (cs == null) continue;
            String worldName = cs.getString("world");
            if (worldName == null) continue;
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;
            if (chestLoc.getWorld() != null && chestLoc.getWorld().equals(w)
                    && chestLoc.getBlockX() == cs.getInt("x")
                    && chestLoc.getBlockY() == cs.getInt("y")
                    && chestLoc.getBlockZ() == cs.getInt("z")) {
                return true;
            }
        }
        return false;
    }

    private boolean isPageTableChest(Location chestLoc) {
        for (String floorKey : new String[]{"pageTable1", "pageTable2", "pageTable3"}) {
            ConfigurationSection floor = plugin.getConfig().getConfigurationSection("chests." + floorKey);
            if (floor == null) continue;
            for (String chestKey : floor.getKeys(false)) {
                ConfigurationSection cs = floor.getConfigurationSection(chestKey);
                if (cs == null) continue;
                String worldName = cs.getString("world");
                if (worldName == null) continue;
                World w = Bukkit.getWorld(worldName);
                if (w == null) continue;
                if (chestLoc.getWorld() != null && chestLoc.getWorld().equals(w)
                        && chestLoc.getBlockX() == cs.getInt("x")
                        && chestLoc.getBlockY() == cs.getInt("y")
                        && chestLoc.getBlockZ() == cs.getInt("z")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Location getLearnerChestLocation() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("chests.learnerChest");
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
    }

    private boolean isCarryingTLBMap(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != org.bukkit.Material.FILLED_MAP || !item.hasItemMeta()) continue;
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(item.getItemMeta().displayName());
            if (name.startsWith("TLB Entry")) return true;
        }
        return false;
    }

    private boolean isCarryingZeroFrameBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != org.bukkit.Material.WRITTEN_BOOK || !item.hasItemMeta()) continue;
            String title = ((org.bukkit.inventory.meta.BookMeta) item.getItemMeta()).getTitle();
            if ("Zero Frame".equals(title)) return true;
        }
        return false;
    }

    /**
     * Returns true only if the container at {@code loc} is the specific RAM frame chest
     * the player should place their book into for the current phase/journey.
     * COW/lazy-alloc writes go into the pfnCow frame; all others go into the pfn frame.
     */
    private boolean isCorrectRamChest(Player player, Location loc, String phase) {
        boolean isCowWrite = "ram_after_cow".equals(phase) || "ram_after_swap_lazy_alloc".equals(phase);
        String pfnHex = isCowWrite
                ? tracker.getVar(player, "pfnCow")
                : tracker.getVar(player, "pfn");

        if (pfnHex == null || pfnHex.equals("?") || pfnHex.equals("N/A")) return false;

        int frameNum;
        try {
            frameNum = Integer.parseInt(pfnHex.replace("0x", "").replace("0X", ""), 16);
        } catch (NumberFormatException e) {
            return false;
        }

        // PFN is 0-based; chest keys are 1-based (chest1=PFN0x0, chest2=PFN0x1, ...)
        ConfigurationSection chestSec = plugin.getConfig()
                .getConfigurationSection("chests.ramRoom.chest" + (frameNum + 1));
        if (chestSec == null) return false;

        World world = Bukkit.getWorld(chestSec.getString("world", ""));
        return world != null
                && loc.getWorld() != null
                && loc.getWorld().equals(world)
                && loc.getBlockX() == chestSec.getInt("x")
                && loc.getBlockY() == chestSec.getInt("y")
                && loc.getBlockZ() == chestSec.getInt("z");
    }

    private boolean isRAMRoomChest(Location chestLoc) {
        ConfigurationSection ramSec = plugin.getConfig().getConfigurationSection("chests.ramRoom");
        if (ramSec == null) return false;

        for (String key : ramSec.getKeys(false)) {
            ConfigurationSection chestSec = ramSec.getConfigurationSection(key);
            if (chestSec != null) {
                String worldName = chestSec.getString("world");
                if (worldName == null) continue;
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                int chestX = chestSec.getInt("x");
                int chestY = chestSec.getInt("y");
                int chestZ = chestSec.getInt("z");

                if (chestLoc.getWorld() != null && chestLoc.getWorld().equals(world)
                        && chestLoc.getBlockX() == chestX
                        && chestLoc.getBlockY() == chestY
                        && chestLoc.getBlockZ() == chestZ) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isZeroFrameChest(Location chestLoc) {
        ConfigurationSection chestSec = plugin.getConfig().getConfigurationSection("chests.ramRoom.zeroChest");
        if (chestSec == null) {
            plugin.getLogger().warning("[RAMChest] isZeroFrameChest: No config for chests.ramRoom.zeroChest");
            return false;
        }

        String worldName = chestSec.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[RAMChest] isZeroFrameChest: No world in config");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[RAMChest] isZeroFrameChest: World not found: " + worldName);
            return false;
        }

        int chestX = chestSec.getInt("x");
        int chestY = chestSec.getInt("y");
        int chestZ = chestSec.getInt("z");
        
        plugin.getLogger().info("[RAMChest] isZeroFrameChest: Config at " + chestX + "," + chestY + "," + chestZ 
            + ", Checking " + chestLoc.getBlockX() + "," + chestLoc.getBlockY() + "," + chestLoc.getBlockZ());

        boolean matches = chestLoc.getWorld() != null && chestLoc.getWorld().equals(world)
                && chestLoc.getBlockX() == chestX
                && chestLoc.getBlockY() == chestY
                && chestLoc.getBlockZ() == chestZ;
        
        plugin.getLogger().info("[RAMChest] isZeroFrameChest: matches=" + matches);
        return matches;
    }

    private boolean isBookInChest7(Player player) {
        ConfigurationSection chestSec = plugin.getConfig().getConfigurationSection("chests.ramRoom.chest7");
        if (chestSec == null) {
            plugin.getLogger().warning("[RAMChest] isBookInChest7: No config for chests.ramRoom.chest7");
            return false;
        }

        String worldName = chestSec.getString("world");
        if (worldName == null) return false;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        int chestX = chestSec.getInt("x");
        int chestY = chestSec.getInt("y");
        int chestZ = chestSec.getInt("z");
        
        Location chestLoc = new Location(world, chestX, chestY, chestZ);
        Block block = chestLoc.getBlock();
        
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
                if (item != null && 
                    (item.getType() == org.bukkit.Material.WRITABLE_BOOK || 
                     item.getType() == org.bukkit.Material.WRITTEN_BOOK)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getBookContentFromChest7(Player player) {
        ConfigurationSection chestSec = plugin.getConfig().getConfigurationSection("chests.ramRoom.chest7");
        if (chestSec == null) {
            plugin.getLogger().warning("[RAMChest] getBookContentFromChest7: No config for chests.ramRoom.chest7");
            return "";
        }

        String worldName = chestSec.getString("world");
        if (worldName == null) return "";
        World world = Bukkit.getWorld(worldName);
        if (world == null) return "";

        int chestX = chestSec.getInt("x");
        int chestY = chestSec.getInt("y");
        int chestZ = chestSec.getInt("z");
        
        Location chestLoc = new Location(world, chestX, chestY, chestZ);
        Block block = chestLoc.getBlock();
        
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
                if (item != null && 
                    (item.getType() == org.bukkit.Material.WRITABLE_BOOK || 
                     item.getType() == org.bukkit.Material.WRITTEN_BOOK)
                    && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
                    List<Component> pages = bookMeta.pages();
                    if (pages != null && !pages.isEmpty()) {
                        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(pages.get(0)).trim();
                    }
                }
            }
        }
        return "";
    }

    private boolean isBookInZeroFrameChest() {
        ConfigurationSection chestSec = plugin.getConfig().getConfigurationSection("chests.ramRoom.zeroChest");
        if (chestSec == null) {
            plugin.getLogger().warning("[RAMChest] isBookInZeroFrameChest: No config for chests.ramRoom.zeroChest");
            return false;
        }

        String worldName = chestSec.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[RAMChest] isBookInZeroFrameChest: No world in config");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[RAMChest] isBookInZeroFrameChest: World not found: " + worldName);
            return false;
        }

        int chestX = chestSec.getInt("x");
        int chestY = chestSec.getInt("y");
        int chestZ = chestSec.getInt("z");
        
        Location chestLoc = new Location(world, chestX, chestY, chestZ);
        Block block = chestLoc.getBlock();
        
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
                if (item != null && 
                    (item.getType() == org.bukkit.Material.WRITABLE_BOOK || 
                     item.getType() == org.bukkit.Material.WRITTEN_BOOK)) {
                    plugin.getLogger().info("[RAMChest] isBookInZeroFrameChest: Book found in chest");
                    return true;
                }
            }
            plugin.getLogger().info("[RAMChest] isBookInZeroFrameChest: No book in chest");
            return false;
        }
        
        plugin.getLogger().warning("[RAMChest] isBookInZeroFrameChest: No chest at location");
        return false;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (pendingQuiz.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            PendingQuiz quiz = pendingQuiz.get(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                QuestionBank.Question q = questionBank.getQuestion(quiz.questionPath);
                if (q == null) {
                    plugin.getLogger().warning("[Quiz] Missing pending phase-gating question '"
                        + quiz.questionPath + "' for " + player.getName()
                        + " in phase " + tracker.getPhase(player));
                    pendingQuiz.remove(player.getUniqueId());
                    return;
                }
                boolean correct = q.checkAnswer(msg);
                plugin.getLogger().info("[Quiz] " + player.getName() + " answered '" + msg
                    + "' for '" + quiz.questionPath + "' | correct=" + correct
                    + " | phase=" + tracker.getPhase(player));
                if (correct) {
                    pendingQuiz.remove(player.getUniqueId());

                    if ("tlb_room.hit_or_miss".equals(quiz.questionPath)) {
                        handleHitOrMissQuizCorrect(player);
                        return;
                    }

                    plugin.getAchievementManager().onCorrectAnswer(player);
                    tracker.setPhase(player, quiz.onCorrectPhase);

                    dialogue.speak(player, quiz.onCorrectDialoguePath, tracker.getVars(player));
                } else {
                    if ("tlb_room.hit_or_miss".equals(quiz.questionPath)) {
                        pendingQuiz.remove(player.getUniqueId());
                        player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.reconsider"));
                        return;
                    }
                    SQLiteStudyDatabase.logWrongAnswer(
                        tracker.getVar(player, "sessionId"),
                        tracker.getPhase(player)
                    );
                    plugin.getAchievementManager().onWrongAnswer(player, quiz.questionPath);
                    player.sendMessage("§c" + q.wrongFeedback);
                    sendQuestion(player, q);
                }
            });
            return;
        }

        UUID termUuid = player.getUniqueId();
        Integer termStep = pendingTerminalPath.get(termUuid);
        if (termStep != null) {
            event.setCancelled(true);
            if (termStep == 0) {
                int choice;
                try {
                    choice = Integer.parseInt(msg);
                    if (choice < 1 || choice > 3) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.quiz.type_1_to_3"));
                    return;
                }
                if (choice == 1) {
                    prevTerminalStep.put(termUuid, 0);
                    pendingTerminalPath.put(termUuid, -1);
                    Bukkit.getScheduler().runTask(plugin, () -> showJourneyList(player));
                } else if (choice == 2) {
                    int idx = tracker.getAllInOrderIndex(player);
                    idx = (idx % 7) + 1;
                    tracker.setAllInOrderIndex(player, idx);
                    Journey next = Journey.fromNumber(idx);
                    if (next == null) next = Journey.LUCKY;
                    final Journey picked = next;
                    prevTerminalStep.put(termUuid, 0);
                    pendingTerminalPath.put(termUuid, picked.number);
                    final Map<String, String> orderedVars = new java.util.HashMap<>();
                    orderedVars.put("journey", picked.displayName);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        askGuidance(player, picked);
                        dialogue.speakInstant(player, "rooms.terminal.journey_ordered", orderedVars);
                    });
                } else {
                    Journey random = Journey.random();
                    prevTerminalStep.put(termUuid, 0);
                    pendingTerminalPath.put(termUuid, random.number);
                    randomJourneyChoice.add(termUuid);
                    Bukkit.getScheduler().runTask(plugin, () -> askGuidance(player, random));
                }
            } else if (termStep == -1) {
                if (msg.equalsIgnoreCase("B")) {
                    pendingTerminalPath.put(termUuid, 0);
                    prevTerminalStep.remove(termUuid);
                    Bukkit.getScheduler().runTask(plugin, () -> startTerminalPathSelection(player));
                    return;
                }
                int choice;
                try {
                    choice = Integer.parseInt(msg);
                    if (choice < 1 || choice > 7) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.quiz.type_1_to_7_or_back"));
                    return;
                }
                Journey picked = Journey.fromNumber(choice);
                if (picked == null) picked = Journey.LUCKY;
                final Journey journey = picked;
                prevTerminalStep.put(termUuid, -1);
                pendingTerminalPath.put(termUuid, journey.number);
                Bukkit.getScheduler().runTask(plugin, () -> askGuidance(player, journey));
            } else {
                String input = msg.toUpperCase();
                if (input.equals("B")) {
                    int prev = prevTerminalStep.getOrDefault(termUuid, 0);
                    if (prev == -1) {
                        prevTerminalStep.put(termUuid, 0);
                        pendingTerminalPath.put(termUuid, -1);
                        Bukkit.getScheduler().runTask(plugin, () -> showJourneyList(player));
                    } else {
                        pendingTerminalPath.put(termUuid, 0);
                        prevTerminalStep.remove(termUuid);
                        Bukkit.getScheduler().runTask(plugin, () -> startTerminalPathSelection(player));
                    }
                    return;
                }
                if (!input.equals("L") && !input.equals("A")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.quiz.type_g_a_or_back"));
                    return;
                }
                boolean isLearner = input.equals("L");
                int journeyNumber = termStep;
                pendingTerminalPath.remove(termUuid);
                prevTerminalStep.remove(termUuid);
                Bukkit.getScheduler().runTask(plugin, () ->
                    startJourneyFromTerminal(player, journeyNumber, isLearner));
            }
            return;
        }

    }
    private void handleRamMixButton(Player player, String phase, Journey journey) {
        switch (phase) {
            case "ram_tlb_hit_access":
            case "ram_tlb_miss_access":
                String nextPhase = JourneyManager.nextPhaseAfterRamConfirm(journey);
                String[] sign = JourneyManager.ramSignAfterConfirm(journey);
                tracker.setPhase(player, nextPhase);
                updateSign("ramRoom.mixSign", sign[0], sign[1], sign[2], sign[3]);
                player.sendMessage(plugin.getConfigManager().getMessage("system.tlb_updated",
                    "{vpn}", tracker.getVar(player, "vpn"), "{pfn}", tracker.getVar(player, "pfn")));
                speakIfLearner(player, "rooms.ram_room.after_confirm", tracker.getVars(player));
                break;

            case "ram_after_cow":
                boolean hasProcess5Book = false;
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                    if (item != null && (item.getType() == org.bukkit.Material.WRITTEN_BOOK || item.getType() == org.bukkit.Material.WRITABLE_BOOK)
                            && item.hasItemMeta()) {
                        hasProcess5Book = true;
                        break;
                    }
                }
                if (hasProcess5Book) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.write_hello_and_place"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.take_book_write_place"));
                }
                break;

            case "ram_book_placed_pure_cow":
                tracker.setPhase(player, "ram_before_finish");
                updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    dialogue.speak(player, "rooms.ram_room.instruction_succeeded", tracker.getVars(player)), 5L);
                break;

            case "ram_disk_swap":
                boolean hasSwapBook = false;
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == org.bukkit.Material.WRITTEN_BOOK
                            && item.hasItemMeta()) {
                        String displayName = item.getItemMeta().getDisplayName();
                        if (displayName != null && displayName.contains("Swap Slot 0")) {
                            hasSwapBook = true;
                            break;
                        }
                    }
                }
                if (hasSwapBook) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.place_swap_book"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.retrieve_swap_page"));
                }
                break;

            case "ram_book_placed_swapped":
                tracker.setPhase(player, "ram_before_finish");
                updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    dialogue.speak(player, "rooms.ram_room.instruction_succeeded", tracker.getVars(player)), 5L);
                break;

            case "ram_disk_lazy_loading":
                if (isCarryingZeroFrameBook(player)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.put_zero_book_back"));
                    return;
                }
                teleportPlayer(player, "swapDistrict");
                break;

            case "ram_after_cow_alloc":
                if (isCarryingZeroFrameBook(player)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.put_zero_book_back"));
                    return;
                }
                teleportPlayer(player, "swapDistrict");
                break;

            case "swap_after_eviction":
                Journey mixJourney = tracker.getJourney(player);
                String bookPlaced = tracker.getVar(player, "swapBookPlaced");
                String cowBookPlaced = tracker.getVar(player, "cowBookPlaced");
                plugin.getLogger().info("[RamMix] swap_after_eviction: bookPlaced=" + bookPlaced + ", cowBookPlaced=" + cowBookPlaced + ", journey=" + mixJourney);

                if (mixJourney == Journey.LAZY_ALLOCATION && "true".equals(cowBookPlaced)) {
                    plugin.getLogger().info("[RamMix] swap_after_eviction: LAZY_ALLOCATION cowBookPlaced=true, finishing journey");

                    tracker.setVar(player, "operation", "write \"hey\"");

                    pageTableManager.updatePteMapAfterCow(player);

                    journeyMapManager.updateMap(player);

                    tracker.setPhase(player, "ram_before_finish");
                    updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                } else if (mixJourney == Journey.LAZY_ALLOCATION) {
                    plugin.getLogger().info("[RamMix] swap_after_eviction: LAZY_ALLOCATION book not ready");
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.put_book_back"));
                } else if ("true".equals(bookPlaced)) {
                    plugin.getLogger().info("[RamMix] swap_after_eviction: bookPlaced=true, updating PFN, PTE and finishing");
                    
                    tracker.setVar(player, "pfn", "0x2");
                    plugin.getLogger().info("[RamMix] swap_after_eviction: PFN updated to 0x2");
                    
                    tracker.setVar(player, "ptePresent", "1");
                    tracker.setVar(player, "pteRead", "1");
                    tracker.setVar(player, "pteWrite", "1");
                    tracker.setVar(player, "pteReadOnly", "0");
                    tracker.setVar(player, "pteInSwap", "0");
                    
                    pageTableManager.updatePteMap(player);
                    
                    tracker.setPhase(player, "ram_before_finish");
                    updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                } else {
                    plugin.getLogger().info("[RamMix] swap_after_eviction: bookPlaced=false, showing warning");
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.place_disk_file"));
                }
                break;

            case "ram_after_swap_lazy_alloc":
                plugin.getLogger().info("[RamMix] ram_after_swap_lazy_alloc: book not yet placed");
                player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.put_book_back"));
                break;

            case "ram_book_placed_lazy_allocation":
                tracker.setPhase(player, "ram_before_finish");
                updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    dialogue.speak(player, "rooms.ram_room.instruction_succeeded", tracker.getVars(player)), 5L);
                break;

            case "ram_after_swap_lazy_loading":
                plugin.getLogger().info("[RamMix] ram_after_swap_lazy_loading: book not yet placed");
                player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.place_disk_file"));
                break;

            case "ram_book_placed_lazy_loading":
                tracker.setPhase(player, "ram_before_finish");
                updateSign("ramRoom.mixSign", "FINISH", "", "", "");
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    dialogue.speak(player, "rooms.ram_room.instruction_succeeded", tracker.getVars(player)), 5L);
                break;

            case "ram_before_finish":
                if (isCarryingZeroFrameBook(player)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.ram.put_zero_book_back"));
                    return;
                }
                tracker.setPhase(player, "ram_finish");
                plugin.getLogger().info("[RamMix] ram_before_finish: journey=" + journey + " progress=" + (progress != null));
                if (journey != null && !progress.isComplete(player, journey)) {
                    progress.markComplete(player, journey);
                    plugin.getLogger().info("[RamMix] Marked journey complete: " + journey);
                }
                if (journey != null) {
                    plugin.getAchievementManager().onJourneyComplete(player, journey.name());
                }
                plugin.getLogger().info("[RamMix] Teleporting to endTerminal");
                teleportPlayer(player, "endTerminal");
                break;

            default:
                break;
        }
    }
    private void handlePermExitButton(Player player, String phase, Journey journey) {
        switch (phase) {
            case "permission_decision": {
                String decision = tracker.getVar(player, "permDecision");
                if ("allow_access".equals(decision)) {
                    tracker.setPhase(player, "ram_tlb_miss_access");
                    teleportPlayer(player, "ramRoom");
                } else if ("segfault".equals(decision)) {
                    progress.markComplete(player, Journey.PERMISSION_VIOLATION);
                    tracker.setPhase(player, "segfault_end");
                    updateSign("perChamber.sign6", "Finish", "", "", "");
                } else if ("protection_fault".equals(decision)) {
                    teleportPlayer(player, "cowRoom");
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.make_choice_first"));
                }
                break;
            }
            case "page_fault_type":
                if ("swapped_out".equals(tracker.getVar(player, "pageFaultSubtype"))) {
                    tracker.setPhase(player, "disk_swap_retrieval");
                    teleportPlayer(player, "diskRoom");
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.make_choice_first"));
                }
                break;

            case "segfault_end":
                plugin.getAchievementManager().onJourneyComplete(player, Journey.PERMISSION_VIOLATION.name());
                teleportPlayer(player, "endTerminal");
                break;

            default:
                player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.make_choice_first"));
                break;
        }
    }
    private void handleCowToRamButton(Player player, String phase) {
        if (ChoiceButtonRules.canUseCowToRam(phase)) {
            Journey j = tracker.getJourney(player);
            tracker.setPhase(player, j == Journey.LAZY_ALLOCATION ? "ram_after_cow_alloc" : "ram_after_cow");
            teleportPlayer(player, "ramRoom");
        }
    }
    private void handleLoadingTpButton(Player player, String phase) {
        if (!ChoiceButtonRules.canUseLoadingTeleport(phase)) return;

        if ("lazy_loading_entered".equals(phase)) {
            teleportPlayer(player, "calculatorRoom");
        } else if ("lazy_loading_returned".equals(phase)) {
            teleportPlayer(player, "diskRoom");
        }
    }
    private void handleDiskToRamButton(Player player, String phase, Journey journey) {
        if ("disk_swap_retrieval".equals(phase)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.disk.retrieve_swap_slot"));
        } else if ("disk_swap_retrieval_after_book".equals(phase)) {
            boolean hasSwapBook = false;
            for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == org.bukkit.Material.WRITTEN_BOOK
                        && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
                    String displayName = bookMeta.getDisplayName();
                    String title = bookMeta.getTitle();
                    if ((displayName != null && (displayName.contains("Swap Slot 0") || displayName.contains("SwapSlot0")))
                            || (title != null && (title.contains("Swap Slot 0") || title.contains("SwapSlot0")))) {
                        hasSwapBook = true;
                        break;
                    }
                }
            }
            if (hasSwapBook) {
                tracker.setPhase(player, "ram_disk_swap");
                teleportPlayer(player, "ramRoom");
            } else {
                plugin.getLogger().warning("[Disk] Required swap book missing for " + player.getName()
                    + " in phase " + phase + "; blocking diskToRam");
                player.sendMessage(plugin.getConfigManager().getMessage("errors.disk.retrieve_swap_slot"));
            }
        } else if ("disk_lazy_loading_after_book".equals(phase)) {
            boolean hasCorrectBook = false;
            for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == org.bukkit.Material.WRITTEN_BOOK
                        && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
                    String displayName = bookMeta.getDisplayName();
                    String title = bookMeta.getTitle();
                    if ((displayName != null && displayName.contains("mystical_grimoire.spells page 4"))
                            || (title != null && title.contains("mystical_grimoire.spells page 4"))) {
                        hasCorrectBook = true;
                        break;
                    }
                }
            }
            if (hasCorrectBook) {
                tracker.setPhase(player, "ram_disk_lazy_loading");
                teleportPlayer(player, "ramRoom");
            } else {
                plugin.getLogger().warning("[Disk] Required lazy-loading book missing for " + player.getName()
                    + " in phase " + phase + "; blocking diskToRam");
                player.sendMessage(plugin.getConfigManager().getMessage("errors.disk.retrieve_treasure_map"));
            }
        } else if ("disk_lazy_loading".equals(phase)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.disk.retrieve_treasure_map"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.disk.need_correct_book"));
        }
    }

    private void handleDoorOpenButton(Player player, String buttonKey, Journey journey, String phase) {
        switch (buttonKey) {
            case "toPageFaultCorridor":
                if (RoomAccessRules.canOpenPageFaultCorridor(phase, journey)) {
                    openDoor("toPageFaultCorridor");
                    moveGuardianAndFireRoomEntry(player, "Page Fault Corridor", 200L);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.wrong_door"));
                }
                break;

            case "toLazyLoading":
                if (RoomAccessRules.canOpenLazyLoading(journey)) {
                    openDoor("toLazyLoading");
                    moveGuardianAndFireRoomEntry(player, "Lazy Loading Room", 200L);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.wrong_door"));
                }
                break;

            case "toLazyAllocation":
                if (RoomAccessRules.canOpenLazyAllocation(journey)) {
                    openDoor("toLazyAllocation");
                    moveGuardianAndFireRoomEntry(player, "Lazy Allocation Room", 200L);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("errors.permission.wrong_door"));
                }
                break;
        }
    }

    private void handleSwapToRam(Player player) {
        String phase = tracker.getPhase(player);
        if (ChoiceButtonRules.canUseSwapToRam(phase)) {
            teleportPlayer(player, "ramRoom");
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.swap.complete_algorithm"));
        }
    }

    private void handlePageTableToChamber(Player player, String buttonKey) {
        String vpnHex = tracker.getVar(player, "vpnHex");
        if ("?".equals(vpnHex) || vpnHex.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.page_table.no_vpn"));
            return;
        }

        int vpnValue = parseVpnHex(vpnHex);
        int correctChestIndex = vpnValue & 0x3;

        boolean hasCorrectPTE = false;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.FILLED_MAP
                    && item.hasItemMeta()) {
                String displayName = item.getItemMeta().getDisplayName();
                if (displayName != null && displayName.contains("PTE Map")) {
                    if (displayName.contains("Chest" + correctChestIndex)) {
                        hasCorrectPTE = true;
                        break;
                    }
                }
            }
        }

        // Allow TP if the player is on the correct floor (correct_floor) or has already
        // taken the PTE from it (acquired_pte). Both phases are only reachable via the
        // correct DIR floor, so a wrong-floor PTE can never pass this gate.
        String phase = tracker.getPhase(player);
        boolean onCorrectFloor = RoomAccessRules.canUsePageTableAccessPhase(phase);
        if (RoomAccessRules.canUsePageTableToChamber(phase, hasCorrectPTE)) {
            teleportPlayer(player, "permissionChamber");
        } else if (!onCorrectFloor) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.page_table.wrong_floor"));
        } else {
            if ("acquired_pte".equals(phase)) {
                plugin.getLogger().warning("[PageTable] Required PTE map missing for " + player.getName()
                    + " after acquired_pte; expected Chest" + correctChestIndex);
            }
            player.sendMessage(plugin.getConfigManager().getMessage("errors.page_table.need_pte_map", "{chest}", String.valueOf(correctChestIndex)));
        }
    }

    private void handleModeStartButton(Player player, String buttonKey, String destination) {
        String phase = tracker.getPhase(player);
        if (!RoomAccessRules.canStartJourneyFromTerminal(phase)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.terminal.select_journey_first"));
            return;
        }
        
        boolean hasMap = false;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.FILLED_MAP
                    && item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta) {
                hasMap = true;
                break;
            }
        }
        if (!hasMap) {
            plugin.getLogger().warning("[Terminal] Required journey map missing for " + player.getName()
                + " in phase " + phase + "; blocking " + buttonKey);
            player.sendMessage(plugin.getConfigManager().getMessage("errors.terminal.take_map_first"));
            return;
        }

        plugin.getAchievementManager().onJourneyStart(player);
        teleportPlayer(player, destination);
    }

    public void cancelTerminalPathSelection(Player player) {
        pendingTerminalPath.remove(player.getUniqueId());
        prevTerminalStep.remove(player.getUniqueId());
    }

    public boolean isTerminalPathPending(Player player) {
        return pendingTerminalPath.containsKey(player.getUniqueId());
    }

    public void startTerminalPathSelection(Player player) {
        pendingTerminalPath.put(player.getUniqueId(), 0);
        prevTerminalStep.remove(player.getUniqueId());

        boolean quizDone = tracker.hasCompletedQuiz(player);
        List<Journey> recommended = tracker.getRecommendedJourneys(player);

        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.title"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        if (quizDone && !recommended.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.option1_quiz"));
        } else if (quizDone) {
            player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.option1_all_right"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.option1_basic"));
        }
        player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.option2"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.option3"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.choose_path.prompt"));
    }

    private void showJourneyList(Player player) {
        boolean quizDone = tracker.hasCompletedQuiz(player);
        Map<Journey, Integer> wrongCounts = tracker.getQuizWrongCounts(player);
        int maxWrong = quizDone
            ? wrongCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0)
            : 0;

        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.journey_list.title"));
        if (quizDone && maxWrong > 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("ui.journey_list.starred_note"));
        }
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));

        Journey[] sorted = Journey.values().clone();
        java.util.Arrays.sort(sorted, java.util.Comparator.comparingInt(j -> j.number));
        for (Journey j : sorted) {
            boolean done = progress.isComplete(player, j);
            StringBuilder line = new StringBuilder("§e" + j.number + "§7) §f" + j.displayName);
            if (done) {
                line.append(" §a✔");
            }
            if (quizDone) {
                int wrong = wrongCounts.getOrDefault(j, 0);
                String wrongStr = wrong == 0 ? "§a0 wrong" : "§c" + wrong + " wrong";
                line.append(" §8(").append(wrongStr);
                if (maxWrong > 0 && wrong == maxWrong) {
                    line.append(" §6★ Recommended");
                }
                line.append("§8)");
            }
            player.sendMessage(line.toString());
        }

        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.journey_list.prompt"));
    }

    private void askGuidance(Player player, Journey journey) {
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.title"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.journey_label", "{journey}", journey.displayName));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.option_g"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.option_g_note"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.option_a"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.separator"));
        player.sendMessage(plugin.getConfigManager().getMessage("ui.guidance.prompt"));
    }

    private void startJourneyFromTerminal(Player player, int journeyNumber, boolean isLearner) {
        Journey journey = Journey.fromNumber(journeyNumber);
        if (journey == null) journey = Journey.LUCKY;

        boolean isRandom = randomJourneyChoice.remove(player.getUniqueId());
        pteChamberDialogueSent.remove(player.getUniqueId());
        diskBookDialogueSent.remove(player.getUniqueId());

        tracker.setMode(player, isLearner ? PlayerMode.LEARNER : PlayerMode.ADVENTURER);
        tracker.setJourney(player, journey);
        tracker.setPhase(player, "terminal_journey_chosen");

        Map<String, String> vars = tracker.getVars(player);
        boolean returning = "true".equals(tracker.getVar(player, "hasCompletedJourney"));
        String confirmDialogue = isRandom
            ? (returning ? "rooms.terminal.returning_journey_random" : "rooms.terminal.journey_random")
            : (returning ? "rooms.terminal.returning_journey_selected" : "rooms.terminal.journey_selected");
        dialogue.speak(player, confirmDialogue, vars);

        journeyMapManager.giveInitialMap(player, "learnerChest");
        refillCalculatorChest();
    }

    private void refillCalculatorChest() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("chests.calculatorChest");
        if (sec == null) { plugin.getLogger().warning("[Calculator] No config at chests.calculatorChest"); return; }
        String worldName = sec.getString("world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { plugin.getLogger().warning("[Calculator] World '" + worldName + "' not found"); return; }
        int x = sec.getInt("x"), y = sec.getInt("y"), z = sec.getInt("z");
        Block block = new Location(world, x, y, z).getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("[Calculator] No chest at chests.calculatorChest " + x + "," + y + "," + z + " (found: " + block.getType() + ")");
            return;
        }
        Inventory inv = chest.getInventory();
        inv.clear();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemStack(Material.WRITABLE_BOOK));
        }
        plugin.getLogger().info("[Calculator] Filled calculator chest with " + inv.getSize() + " writable books");
    }

    private void handleSummariseJourney(Player player) {
        Journey journey = tracker.getJourney(player);
        if (journey == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_active_journey"));
            return;
        }
        String key = "rooms.end_terminal.summary_" + journey.name().toLowerCase();
        if (dialogue.hasPath(key)) {
            dialogue.speak(player, key, tracker.getVars(player));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("feedback.nothing_to_add"));
        }
    }

    private void handleEndToStart(Player player) {
        plugin.getLogger().info("[EndToStart] Resetting journey state for " + player.getName());
        player.getInventory().clear();

        boolean quizDone = tracker.hasCompletedQuiz(player);
        tracker.setPhase(player, quizDone ? "terminal_path_select" : "terminal_spawn");

        String sessionId = tracker.getVar(player, "sessionId");
        String learnerJourneyNum = tracker.getVar(player, "learnerJourneyNum");
        tracker.clearVars(player);
        if (sessionId != null && !sessionId.isEmpty()) {
            tracker.setVar(player, "sessionId", sessionId);
        }
        if (learnerJourneyNum != null && !"?".equals(learnerJourneyNum)) {
            tracker.setVar(player, "learnerJourneyNum", learnerJourneyNum);
        }
        tracker.setVar(player, "hasCompletedJourney", "true");

        tracker.setJourney(player, null);

        teleportPlayer(player, "initialSpawn");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String returnPath = quizDone
                ? "rooms.terminal.returning_quiz_done"
                : "rooms.terminal.returning_no_quiz";
            dialogue.speakDelayed(player, returnPath, tracker.getVars(player));
        }, 20L);
    }

    private void handleTLBDecision(Player player, boolean isHit) {
        String phase = tracker.getPhase(player);

        if (!"tlb_after_calculator".equals(phase)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.tlb_room.visit_calculator"));
            return;
        }

        tracker.setVar(player, "tlbDecision", isHit ? "hit" : "miss");
        tracker.setVar(player, "result", isHit ? "HIT" : "MISS");
        tracker.setVar(player, "did_not", isHit ? "DID" : "DID NOT");
        dialogue.speakInstant(player, "rooms.tlb_room.after_hit_miss", tracker.getVars(player));
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            askQuestion(player, "tlb_room.hit_or_miss", null, null), 45L);
    }

    private void handleHitOrMissQuizCorrect(Player player) {
        Journey journey = tracker.getJourney(player);
        boolean isHit = "hit".equals(tracker.getVar(player, "tlbDecision"));
        boolean correctHit = JourneyManager.tlbRoute(journey) == JourneyManager.TlbRoute.RAM;
        boolean correctDecision = (isHit == correctHit);

        if (correctDecision) {
            plugin.getAchievementManager().onCorrectAnswer(player);
            if (correctHit) {
                tracker.setPhase(player, "tlb_hit_quiz_done");
                journeyMapManager.updateMapAfterTLBHit(player);
                updateSign("tlb.hitDecision", "Go to RAM", "", "", "");
                dialogue.speak(player, "rooms.tlb_room.after_hit_quiz_lucky", tracker.getVars(player));
            } else {
                tracker.setPhase(player, "tlb_miss_quiz");
                dialogue.speak(player, "rooms.tlb_room.after_miss_quiz_non_lucky", tracker.getVars(player));
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    askQuestion(player, "tlb_room.miss_door", "tlb_miss_correct", "rooms.tlb_room.after_miss_correct"),
                    60L);
            }
        } else {
            plugin.getAchievementManager().onWrongAnswer(player, "tlb_decision");
            if (correctHit) {
                dialogue.speak(player, "rooms.tlb_room.after_miss_quiz_lucky", tracker.getVars(player));
            } else {
                dialogue.speak(player, "rooms.tlb_room.after_hit_quiz_non_lucky", tracker.getVars(player));
            }
        }
    }

    private void handleCalculatorContinue(Player player, String buttonKey) {
        String phase = tracker.getPhase(player);
        plugin.getLogger().info("[CalcContinue] Button pressed, phase=" + phase);

        if (ChoiceButtonRules.isCalculatorContinueDonePhase(phase)) {
            if ("calculator_from_tlb_done".equals(phase)) {
                plugin.getLogger().info("[CalcContinue] Teleporting to TLB room");
                tracker.setPhase(player, "tlb_after_calculator");
                teleportPlayer(player, "tlbSpawn");
            } else {
                plugin.getLogger().info("[CalcContinue] Teleporting to Lazy Loading room");
                teleportPlayer(player, "lazyLoadingRoom");
            }
        } else if (ChoiceButtonRules.isCalculatorContinueBlockedPhase(phase)) {
            if ("calculator_from_tlb".equals(phase)) {
                if (calculatorListener.hasPendingCalcVerify(player)) {
                    // Quiz is already visible in chat — silently block.
                    return;
                }
                player.sendMessage(plugin.getConfigManager().getMessage("errors.calculator.calculate_va"));
                plugin.getLogger().info("[CalcContinue] Player hasn't completed visit 1 yet");
            } else {
                if (calculatorListener.hasPendingCalcVerify(player)) return;
                player.sendMessage(plugin.getConfigManager().getMessage("errors.calculator.calculate_page_index"));
                plugin.getLogger().info("[CalcContinue] Player hasn't completed visit 2 yet");
            }
        } else {
            player.sendMessage("§cComplete the calculator room first. Current phase: " + phase);
            plugin.getLogger().warning("[CalcContinue] Wrong phase: " + phase);
        }
    }

    private void handlePermissionDecision(Player player, String buttonKey, Journey journey, Map<String, String> vars) {
        String label = ChoiceButtonRules.permissionDecisionLabel(buttonKey);
        if (label == null) return;

        vars.put("button", label);
        player.sendMessage(plugin.getConfigManager().getMessage("feedback.selected", "{label}", label));
        resolvePermissionDecision(player, buttonKey, journey, vars);
    }

    private void handlePageFaultType(Player player, String buttonKey, Journey journey, Map<String, String> vars) {
        String label = ChoiceButtonRules.pageFaultTypeLabel(buttonKey);
        if (label == null) return;

        vars.put("button", label);
        player.sendMessage(plugin.getConfigManager().getMessage("feedback.selected", "{label}", label));
        resolvePageFaultType(player, buttonKey, journey, vars);
    }

    private void handleLazyAllocDecision(Player player, String buttonKey, Journey journey, Map<String, String> vars) {
        String label = ChoiceButtonRules.lazyAllocDecisionLabel(buttonKey);
        if (label == null) return;

        vars.put("button", label);
        player.sendMessage(plugin.getConfigManager().getMessage("feedback.selected", "{label}", label));
        resolveLazyAllocDecision(player, buttonKey, journey, vars);
    }

    private void handleLazyAllocCow(Player player, String buttonKey, Journey journey, Map<String, String> vars) {
        String label = ChoiceButtonRules.lazyAllocCowLabel(buttonKey);
        if (label == null) return;

        vars.put("button", label);
        player.sendMessage(plugin.getConfigManager().getMessage("feedback.selected", "{label}", label));
        resolveLazyAllocCow(player, buttonKey, vars);
    }

    private void handleCowDecision(Player player, String buttonKey, Journey journey, Map<String, String> vars) {
        String label = ChoiceButtonRules.cowDecisionLabel(buttonKey);
        if (label == null) return;

        vars.put("button", label);
        player.sendMessage(plugin.getConfigManager().getMessage("feedback.selected", "{label}", label));
        resolveCowDecision(player, buttonKey, vars);
    }


    private void resolvePermissionDecision(Player player, String action, Journey journey, Map<String, String> vars) {
        String answer = ChoiceButtonRules.buttonToPermissionAnswer(action);
        boolean correct = answer.equals(journey.permissionAnswer);

        if (!correct) {
            plugin.getAchievementManager().onWrongAnswer(player, "permission_chamber");
            SQLiteStudyDatabase.logWrongAnswer(vars.getOrDefault("sessionId", "?"), "permission_chamber");
            player.sendMessage(permissionIncorrectFeedback(action));
            return;
        }

        plugin.getAchievementManager().onCorrectAnswer(player);

        switch (JourneyManager.permissionRoute(journey)) {
            case RAM:
                tracker.setVar(player, "button", "Allow Access");
                dialogue.speak(player, "rooms.permission_chamber.after_button_press", vars);
                dialogue.speak(player, "feedback.allow_access_correct", vars);
                dialogue.speak(player, "rooms.permission_chamber.allow_access_proceed", vars);
                tracker.setVar(player, "permDecision", "allow_access");
                clearPermChoiceSigns();
                updateSign("perChamber.sign6", "Go to RAM", "", "", "");
                break;
            case PAGE_FAULT:
                tracker.setPhase(player, "page_fault_type");
                updateSign("perChamber.sign1", "Lazy Allocation", "", "", "");
                updateSign("perChamber.sign2", "Lazy Loading", "", "", "");
                updateSign("perChamber.sign3", "Swapped out", "", "", "");
                updateSign("perChamber.sign4", "", "", "", "");
                updateSign("perChamber.sign5", "Which type of", "page fault?", "", "");
                dialogue.speak(player, "rooms.permission_chamber.page_fault_subtype_prompt", vars);
                break;
            case TERMINATE:
                dialogue.speak(player, "feedback.segfault_correct", vars);
                tracker.setVar(player, "permDecision", "segfault");
                clearPermChoiceSigns();
                updateSign("perChamber.sign6", "Terminate", "process and", "finish", "");
                break;
            case COW:
                dialogue.speak(player, "feedback.protection_fault_correct", vars);
                tracker.setVar(player, "permDecision", "protection_fault");
                clearPermChoiceSigns();
                updateSign("perChamber.sign6", "Go to COW", "room", "", "");
                break;
            case NONE:
                break;
        }
    }

    private String permissionIncorrectFeedback(String action) {
        switch (action) {
            case "btn1": return plugin.getConfigManager().getMessage("permission_feedback.allow_access");
            case "btn2": return plugin.getConfigManager().getMessage("permission_feedback.page_fault");
            case "btn3": return plugin.getConfigManager().getMessage("permission_feedback.segfault");
            case "btn4": return plugin.getConfigManager().getMessage("permission_feedback.protection");
            default:     return plugin.getConfigManager().getMessage("permission_feedback.default");
        }
    }

    private void resolvePageFaultType(Player player, String action, Journey journey, Map<String, String> vars) {
        String selectedType = ChoiceButtonRules.buttonToPageFaultType(action);
        String expected = journey.pageFaultType;
        boolean correct = selectedType.equals(expected);

        if (!correct) {
            plugin.getAchievementManager().onWrongAnswer(player, "page_fault_type");
            SQLiteStudyDatabase.logWrongAnswer(vars.getOrDefault("sessionId", "?"), "permission_chamber_pft");
            player.sendMessage(pageFaultTypeIncorrectFeedback(action));
            return;
        }

        plugin.getAchievementManager().onCorrectAnswer(player);

        switch (JourneyManager.pageFaultRoute(journey)) {
            case LAZY_ALLOCATION_ROOM:
                dialogue.speak(player, "feedback.lazy_allocation_correct", vars);
                clearPermSubtypeSigns();
                break;
            case LAZY_LOADING_ROOM:
                dialogue.speak(player, "feedback.lazy_loading_correct", vars);
                clearPermSubtypeSigns();
                break;
            case DISK_ROOM:
                dialogue.speak(player, "rooms.permission_chamber.proceed_to_disk", vars);
                tracker.setVar(player, "pageFaultSubtype", "swapped_out");
                clearPermSubtypeSigns();
                updateSign("perChamber.sign6", "Go to Disk", "", "", "");
                break;
            case NONE:
                break;
        }
    }

    private String pageFaultTypeIncorrectFeedback(String action) {
        switch (action) {
            case "btn1": return plugin.getConfigManager().getMessage("page_fault_type_feedback.lazy_allocation");
            case "btn2": return plugin.getConfigManager().getMessage("page_fault_type_feedback.lazy_loading");
            case "btn3": return plugin.getConfigManager().getMessage("page_fault_type_feedback.swapping");
            default:     return plugin.getConfigManager().getMessage("page_fault_type_feedback.default");
        }
    }

    private void resolveLazyAllocDecision(Player player, String action, Journey journey, Map<String, String> vars) {
        if ("allocateLazy".equals(action)) {
            plugin.getAchievementManager().onCorrectAnswer(player);
            tracker.setVar(player, "pfn", "0x9");
            tracker.setVar(player, "ptePresent", "1");
            tracker.setVar(player, "pteRead", "1");
            tracker.setVar(player, "pteWrite", "0");
            tracker.setVar(player, "pteReadOnly", "1");
            tracker.setVar(player, "pteAnon", "0");
            tracker.setVar(player, "pteInSwap", "0");
            tracker.setVar(player, "instruction", "write 0x45 hey");
            tracker.setVar(player, "operation",   "write");
            pageTableManager.updatePteMap(player);
            journeyMapManager.updateMap(player);
            player.sendMessage(plugin.getConfigManager().getMessage("system.pte_updated",
                "{values}", "PRESENT=1, PFN=" + tracker.getVar(player, "pfn") + ", READ-ONLY=1"));
            dialogue.speak(player, "rooms.lazy_allocation_room.allocate_correct", tracker.getVars(player));
            tracker.setPhase(player, "lazy_alloc_cow");
            updateSign("lazyAllocation.allocateSign", "", "", "", "");
            updateSign("lazyAllocation.swapSign", "", "", "", "");
            setLazyAllocCowSigns();
        } else {
            plugin.getAchievementManager().onWrongAnswer(player, "lazy_allocation");
            SQLiteStudyDatabase.logWrongAnswer(vars.getOrDefault("sessionId", "?"), "lazy_allocation_room");
            dialogue.speak(player, "rooms.lazy_allocation_room.allocate_incorrect", vars);
        }
    }

    private void resolveLazyAllocCow(Player player, String action, Map<String, String> vars) {
        if ("cowLazyAlloc".equals(action)) {
            plugin.getAchievementManager().onCorrectAnswer(player);
            dialogue.speak(player, "rooms.lazy_allocation_room.second_visit_correct", vars);
            tracker.setPhase(player, "lazy_alloc_before_tp");
            updateSign("lazyAllocation.cowSign", "", "", "", "");
            updateSign("lazyAllocation.doNothingSign", "", "", "", "");
            updateSign("lazyAllocation.writeSign", "", "", "", "");
            updateSign("lazyAllocation.mixSign", "Go to COW room", "", "", "");
        } else {
            plugin.getAchievementManager().onWrongAnswer(player, "lazy_allocation_cow");
            SQLiteStudyDatabase.logWrongAnswer(vars.getOrDefault("sessionId", "?"), "lazy_allocation_cow");
            dialogue.speak(player, "rooms.lazy_allocation_room.second_visit_incorrect", vars);
        }
    }

    private void resolveCowDecision(Player player, String action, Map<String, String> vars) {
        if ("allocateCow".equals(action)) {
            plugin.getAchievementManager().onCorrectAnswer(player);
            Journey journey = tracker.getJourney(player);

            if (journey == Journey.PURE_COW) {
                String pfnCow = tracker.getVar(player, "pfnCow");
                if (!"?".equals(pfnCow)) {
                    tracker.setVar(player, "pfn", pfnCow);
                    vars.put("pfn", pfnCow);
                }
                tracker.setVar(player, "pteWrite", "1");
                tracker.setVar(player, "pteReadOnly", "0");
                player.sendMessage(plugin.getConfigManager().getMessage("system.pte_updated",
                    "{values}", "PRESENT=1, PFN=" + tracker.getVar(player, "pfn") + ", WRITE=1"));
            }

            String cowDialogue = journey == Journey.LAZY_ALLOCATION
                ? "rooms.cow_room.allocate_copy_correct_lazy_alloc"
                : "rooms.cow_room.allocate_copy_correct";
            dialogue.speak(player, cowDialogue, vars);
            tracker.setPhase(player, "cow_decision_after");
            updateSign("cow.toRam", "Go to RAM", "", "", "");

            if (journey == Journey.PURE_COW) {
                pageTableManager.updatePteMapAfterCow(player);
            }
        } else {
            plugin.getAchievementManager().onWrongAnswer(player, "cow_room");
            SQLiteStudyDatabase.logWrongAnswer(vars.getOrDefault("sessionId", "?"), "cow_room");
            dialogue.speak(player, "rooms.cow_room.terminate_incorrect", vars);
        }
    }

    public void setRamMixSign(String l1, String l2, String l3, String l4) {
        plugin.getLogger().info("[ChoiceButton] setRamMixSign called: '" + l1 + "', '" + l2 + "', '" + l3 + "', '" + l4 + "'");
        updateSign("ramRoom.mixSign", l1, l2, l3, l4);
    }

    private void clearPermChoiceSigns() {
        updateSign("perChamber.sign1", "", "", "", "");
        updateSign("perChamber.sign2", "", "", "", "");
        updateSign("perChamber.sign3", "", "", "", "");
        updateSign("perChamber.sign4", "", "", "", "");
        updateSign("perChamber.sign5", "", "", "", "");
    }

    private void clearPermSubtypeSigns() {
        updateSign("perChamber.sign1", "", "", "", "");
        updateSign("perChamber.sign2", "", "", "", "");
        updateSign("perChamber.sign3", "", "", "", "");
        updateSign("perChamber.sign5", "", "", "", "");
    }

    public void initPermissionChamberSigns() {
        updateSign("perChamber.sign1", "Allow Access", "", "", "");
        updateSign("perChamber.sign2", "Page Fault", "", "", "");
        updateSign("perChamber.sign3", "Segmentation", "Fault", "", "");
        updateSign("perChamber.sign4", "Protection", "Fault", "", "");
        updateSign("perChamber.sign5", "Make Your", "Decision", "", "");
        updateSign("perChamber.sign6", "", "", "", "");
    }

    public void clearCowToRamSign() {
        updateSign("cow.toRam", "", "", "", "");
    }

    public void resetHitDecisionSign() {
        updateSign("tlb.hitDecision", "HIT", "", "", "");
    }

    public void setLazyAllocDecisionSigns() {
        updateSign("lazyAllocation.allocateSign", "Allocate", "", "", "");
        updateSign("lazyAllocation.swapSign", "Swap from", "Disk", "", "");
        updateSign("lazyAllocation.mixSign", "", "", "", "");
        updateSign("lazyAllocation.cowSign", "", "", "", "");
        updateSign("lazyAllocation.doNothingSign", "", "", "", "");
        updateSign("lazyAllocation.writeSign", "", "", "", "");
    }

    public void setLazyAllocCowSigns() {
        updateSign("lazyAllocation.allocateSign", "", "", "", "");
        updateSign("lazyAllocation.swapSign", "", "", "", "");
        updateSign("lazyAllocation.mixSign", "Deny the write", "", "", "");
        updateSign("lazyAllocation.cowSign", "Do COW", "", "", "");
        updateSign("lazyAllocation.doNothingSign", "Do nothing", "", "", "");
        updateSign("lazyAllocation.writeSign", "Process wants", "to write. What", "do you do?", "");
    }

    public void setLazyAllocBeforeTpSign() {
        updateSign("lazyAllocation.mixSign", "Go to COW room", "", "", "");
    }

    public void setLoadingSign(String l1, String l2, String l3, String l4) {
        updateSign("lazyLoading.mixSign", l1, l2, l3, l4);
    }

    private void speakIfLearner(Player player, String path, Map<String, String> vars) {
        plugin.getLogger().info("[Dialogue] speakIfLearner '" + path + "' | mode=" + tracker.getMode(player));
        if (tracker.getMode(player) != PlayerMode.ADVENTURER) {
            dialogue.speak(player, path, vars);
        }
    }

    public void askQuestion(Player player, String questionPath,
                            String onCorrectPhase, String onCorrectDialoguePath) {
        QuestionBank.Question q = questionBank.getQuestion(questionPath);
        if (q == null) {
            plugin.getLogger().warning("[Quiz] askQuestion: no question found at '" + questionPath + "'");
            return;
        }
        plugin.getLogger().info("[Quiz] asking '" + questionPath + "' | onCorrectPhase=" + onCorrectPhase
            + " | onCorrectDialogue=" + onCorrectDialoguePath);
        sendQuestion(player, q);
        pendingQuiz.put(player.getUniqueId(),
            new PendingQuiz(questionPath, onCorrectPhase, onCorrectDialoguePath));
    }

    private void sendQuestion(Player player, QuestionBank.Question q) {
        Map<String, String> vars = tracker.getVars(player);
        String questionText = q.text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            questionText = questionText.replace("{" + e.getKey() + "}", e.getValue());
        }
        player.sendMessage("§6[Quiz] §e" + questionText);
        if (q.isMultipleChoice()) {
            player.sendMessage(q.formatOptions(vars));
        }
    }

    private void updateSign(String configPath, String l1, String l2, String l3, String l4) {
        ConfigurationSection section =
            plugin.getConfig().getConfigurationSection("signs." + configPath);
        if (section == null) {
            plugin.getLogger().warning("[Signs] No config entry for signs." + configPath);
            return;
        }
        String worldName = section.getString("world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world,
            section.getInt("x"), section.getInt("y"), section.getInt("z"));
        Block block = loc.getBlock();

        if (!(block.getState() instanceof Sign sign)) {
            plugin.getLogger().warning("[Signs] No sign block at signs." + configPath
                + " (" + block.getType() + " at "
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")");
            return;
        }

        sign.getSide(Side.FRONT).line(0, Component.text(l1));
        sign.getSide(Side.FRONT).line(1, Component.text(l2));
        sign.getSide(Side.FRONT).line(2, Component.text(l3));
        sign.getSide(Side.FRONT).line(3, Component.text(l4));
        sign.update(true);
    }

    private void openDoor(String doorKey) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("doors." + doorKey);
        if (sec == null) {
            plugin.getLogger().warning("[DoorOpen] No config for doors." + doorKey);
            return;
        }
        String worldName = sec.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[DoorOpen] doors." + doorKey + " missing world");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DoorOpen] doors." + doorKey + " refers to missing world: " + worldName);
            return;
        }

        Block bottom = world.getBlockAt(sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        if (bottom.getBlockData() instanceof Openable openable) {
            openable.setOpen(true);
            bottom.setBlockData(openable);
            Block top = world.getBlockAt(sec.getInt("x"), sec.getInt("y") + 1, sec.getInt("z"));
            if (top.getBlockData() instanceof Openable topOpenable) {
                topOpenable.setOpen(true);
                top.setBlockData(topOpenable);
            }
            plugin.getLogger().info("[DoorOpen] Opened door: " + doorKey);
            Bukkit.getScheduler().runTaskLater(plugin, () -> closeDoor(doorKey), 200L);
        } else {
            plugin.getLogger().warning("[DoorOpen] Block at doors." + doorKey + " is not a door (" + bottom.getType() + ")");
        }
    }

    public void closeDoor(String doorKey) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("doors." + doorKey);
        if (sec == null) {
            plugin.getLogger().warning("[DoorOpen] No config for doors." + doorKey + " while closing");
            return;
        }
        String worldName = sec.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("[DoorOpen] doors." + doorKey + " missing world while closing");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DoorOpen] doors." + doorKey + " refers to missing world while closing: " + worldName);
            return;
        }

        Block bottom = world.getBlockAt(sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        if (bottom.getBlockData() instanceof Openable openable) {
            openable.setOpen(false);
            bottom.setBlockData(openable);
            Block top = world.getBlockAt(sec.getInt("x"), sec.getInt("y") + 1, sec.getInt("z"));
            if (top.getBlockData() instanceof Openable topOpenable) {
                topOpenable.setOpen(false);
                top.setBlockData(topOpenable);
            }
        }
    }

    private void moveGuardianAndFireRoomEntry(Player player, String destinationRoomTitle, long timeoutTicks) {
        plugin.getRoomChangeListener().setPendingRoomEntry(player, destinationRoomTitle, timeoutTicks);
    }

    private void teleportPlayer(Player player, String destination) {
        plugin.getLogger().info("[CalcContinue] Teleporting to: " + destination);
        Location dest = locationRegistry.get(destination);
        if (dest == null) {
            plugin.getLogger().warning("[CalcContinue] TP destination not found: " + destination);
            player.sendMessage("§c[Error] Destination '" + destination + "' not found!");
            return;
        }
        plugin.getLogger().info("[CalcContinue] Destination found at: " + dest.getBlockX() + "," + dest.getBlockY() + "," + dest.getBlockZ());
        player.teleport(dest);

        RoomRegistry.Room room = plugin.getRoomRegistry().getRoomAt(dest);
        if (room == null) {
            plugin.getLogger().warning("[Teleport] Destination '" + destination + "' at "
                + dest.getBlockX() + "," + dest.getBlockY() + "," + dest.getBlockZ()
                + " is not inside any registered room; room-entry logic skipped");
            return;
        }

        KernelGuardian guardian = plugin.getKernelGuardian();
        if (guardian != null && guardian.isSpawned()) {
            guardian.moveTo(room.npcPosition != null ? room.npcPosition : dest.clone().add(3, 0, 0));
        }

        final String roomTitle = room.title;
        plugin.getRoomChangeListener().markRoomEntered(player, roomTitle);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () ->
            plugin.getRoomChangeListener().onRoomEntered(player, roomTitle), 10L);
    }

    private int parseVpnHex(String vpnHex) {
        try {
            return Integer.parseInt(vpnHex.replace("0x", "").replace("0X", ""), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String findButton(Location clicked) {
        for (Map.Entry<Location, String> entry : buttons.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() != null && loc.getWorld().equals(clicked.getWorld())
                    && loc.getBlockX() == clicked.getBlockX()
                    && loc.getBlockY() == clicked.getBlockY()
                    && loc.getBlockZ() == clicked.getBlockZ()) {
                return entry.getValue();
            }
        }
        return null;
    }
}
