package com.oscity.mechanics;

import com.oscity.OSCity;
import com.oscity.content.DialogueManager;
import com.oscity.mode.PlayerMode;
import com.oscity.session.JourneyTracker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SwapClockManager {

    private final OSCity plugin;
    private final JourneyTracker tracker;
    private final DialogueManager dialogue;

    private final Map<UUID, ClockState> states = new HashMap<>();

    private static class ClockState {
        int nextExpectedFrame = 1;
        int pressedCount = 0;
        boolean roundTwoStarted = false;
        final int victimFrameNum;
        int wrongPresses = 0;

        ClockState(int victimFrameNum) {
            this.victimFrameNum = victimFrameNum;
        }
    }

    public SwapClockManager(OSCity plugin, JourneyTracker tracker, DialogueManager dialogue) {
        this.plugin   = plugin;
        this.tracker  = tracker;
        this.dialogue = dialogue;
    }

    public void startClock(Player player) {
        startClock(player, false);
    }

    public void startClock(Player player, boolean isRoundTwo) {
        String pfnStr = tracker.getVar(player, "pfn");
        int victimFrame = parsePfn(pfnStr);
        ClockState state = new ClockState(victimFrame);
        states.put(player.getUniqueId(), state);

        if (isRoundTwo) {
            for (int i = 1; i <= 6; i++) {
                String pfnHex = "0x" + Integer.toHexString(i).toUpperCase();
                if (i == victimFrame) {
                    setTorchLit(i, false);
                    updateFrameSign(i, pfnHex, "VICTIM!", "Press the", "button!");
                } else {
                    setTorchLit(i, true);
                    updateFrameSign(i, pfnHex, "USE_BIT=1 (ON)", "", "");
                }
            }
            state.roundTwoStarted = true;
            state.nextExpectedFrame = 1;
            player.sendMessage(plugin.getConfigManager().getMessage("clock.victim_identified"));
        } else {
            for (int i = 1; i <= 6; i++) {
                setTorchLit(i, true);
            }

            for (int i = 1; i <= 6; i++) {
                String pfnHex = "0x" + Integer.toHexString(i).toUpperCase();
                updateFrameSign(i, pfnHex, "USE_BIT=1 (ON)", "", "");
            }
        }
    }

    public boolean handleFrameButton(Player player, int frameNum) {
        ClockState state = states.get(player.getUniqueId());
        String pfnHex = "0x" + Integer.toHexString(frameNum).toUpperCase();
        
        if ("swap_victim_found".equals(tracker.getPhase(player)) && state != null && frameNum == state.victimFrameNum) {
            states.remove(player.getUniqueId());
            updateFrameSign(frameNum, pfnHex, "Swapped out", "to disk", "");
            tracker.setPhase(player, "swap_after_eviction");
            player.sendMessage(plugin.getConfigManager().getMessage("clock.evicted_to_swap", "{pfn}", pfnHex));
            player.sendMessage(plugin.getConfigManager().getMessage("system.frame_swapped_out",
                "{pfn}", pfnHex));
            if (tracker.getMode(player) != PlayerMode.ADVENTURER)
                dialogue.speak(player, "rooms.swap_district.after_eviction", tracker.getVars(player));
            
            boolean perfect = state.wrongPresses == 0;
            plugin.getAchievementManager().onSwapClockComplete(player, perfect);
            
            return true;
        }
        
        if (state == null) return false;

        if (isTorchLit(frameNum)) {
            if (state.roundTwoStarted) {
                if (rejectOutOfOrder(player, state, frameNum)) return true;
                setTorchLit(frameNum, false);
                updateFrameSign(frameNum, pfnHex, "USE_BIT=0 (OFF)", "(checked)", "");
                player.sendMessage(plugin.getConfigManager().getMessage("clock.recently_accessed", "{pfn}", pfnHex));
                state.nextExpectedFrame++;
            } else {
                if (frameNum != state.nextExpectedFrame) {
                    String expectedHex = "0x" + Integer.toHexString(state.nextExpectedFrame).toUpperCase();
                    player.sendMessage(plugin.getConfigManager().getMessage("clock.wrong_order", "{pfn}", expectedHex));
                    return true;
                }
                setTorchLit(frameNum, false);
                updateFrameSign(frameNum, pfnHex, "USE_BIT=0 (OFF)", "(2nd chance)", "");
                player.sendMessage(plugin.getConfigManager().getMessage("clock.use_bit_flipped", "{pfn}", pfnHex));
                state.nextExpectedFrame++;
                state.pressedCount++;

                if (state.pressedCount == 6) {
                    // All 6 pressed → end of round 1.
                    // Delay round 2 activation so the transition message appears before
                    // the player can interact again (prevents skipping straight to victim).
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        state.roundTwoStarted = true;
                        state.nextExpectedFrame = 1;
	                        plugin.getLogger().info("[SwapClock] Starting round 2, victim frame: " + state.victimFrameNum);
	                        for (int i = 1; i <= 6; i++) {
	                            if (i != state.victimFrameNum) {
                                plugin.getLogger().info("[SwapClock] Re-lighting torch " + i);
                                setTorchLit(i, true);
                                String h = "0x" + Integer.toHexString(i).toUpperCase();
                                updateFrameSign(i, h, "USE_BIT=1 (ON)", "", "");
                            } else {
                                plugin.getLogger().info("[SwapClock] Keeping torch " + i + " OFF (victim)");
                            }
                        }
	                        player.sendMessage(plugin.getConfigManager().getMessage("clock.all_given_second_chance"));
	                        player.sendMessage(plugin.getConfigManager().getMessage("clock.walk_again"));
	                    }, 5L);
	                }
            }
        } else {
            if (!state.roundTwoStarted) {
                if (frameNum < state.nextExpectedFrame) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clock.already_flipped", "{pfn}", pfnHex));
                } else {
                    // Shouldn't happen (we lit all on entry), but handle gracefully
                    player.sendMessage(plugin.getConfigManager().getMessage("clock.already_off", "{pfn}", pfnHex));
                }
            } else if (frameNum == state.victimFrameNum) {
                if (rejectOutOfOrder(player, state, frameNum)) return true;
                updateFrameSign(frameNum, pfnHex, "VICTIM!", "Press the", "button again!");
                tracker.setPhase(player, "swap_victim_found");
                player.sendMessage(plugin.getConfigManager().getMessage("system.victim_confirmed", "{pfn}", pfnHex));
                if (tracker.getMode(player) != PlayerMode.ADVENTURER)
                    dialogue.speak(player, "rooms.swap_district.victim_found", tracker.getVars(player));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("clock.off_not_victim", "{pfn}", pfnHex));
                state.wrongPresses++;
                plugin.getAchievementManager().onWrongAnswer(player, "swap_clock");
            }
        }
        return true;
    }

    private boolean rejectOutOfOrder(Player player, ClockState state, int frameNum) {
        if (frameNum == state.nextExpectedFrame) return false;

        String expectedHex = "0x" + Integer.toHexString(state.nextExpectedFrame).toUpperCase();
        player.sendMessage(plugin.getConfigManager().getMessage("clock.wrong_order", "{pfn}", expectedHex));
        state.wrongPresses++;
        plugin.getAchievementManager().onWrongAnswer(player, "swap_clock");
        return true;
    }

    private boolean isTorchLit(int frameNum) {
        Location loc = getRedstoneLocation(frameNum);
        if (loc == null) return false;
        BlockData data = loc.getBlock().getBlockData();
        return data instanceof Lightable && ((Lightable) data).isLit();
    }

    private void setTorchLit(int frameNum, boolean lit) {
        Location loc = getRedstoneLocation(frameNum);
        if (loc == null) {
            plugin.getLogger().warning("[SwapClock] setTorchLit: No location for redstone" + frameNum);
            return;
        }
        Block block = loc.getBlock();
        plugin.getLogger().info("[SwapClock] setTorchLit: Frame " + frameNum + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " - Block: " + block.getType() + " - Setting lit: " + lit);
        BlockData data = block.getBlockData();
        if (data instanceof Lightable lightable) {
            lightable.setLit(lit);
            block.setBlockData(lightable, false); // false = no physics, prevents vanilla override
            plugin.getLogger().info("[SwapClock] setTorchLit: Successfully set frame " + frameNum + " to lit=" + lit);
        } else {
            plugin.getLogger().warning("[SwapClock] setTorchLit: Block at redstone" + frameNum
                + " is not Lightable: " + data.getMaterial());
        }
    }

    private Location getRedstoneLocation(int frameNum) {
        ConfigurationSection sec = plugin.getConfig()
            .getConfigurationSection("redstone.redstone" + frameNum);
        if (sec == null) {
            plugin.getLogger().warning("[SwapClock] No config for redstone.redstone" + frameNum);
            return null;
        }
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
    }

    private void updateFrameSign(int frameNum, String l1, String l2, String l3, String l4) {
        ConfigurationSection sec = plugin.getConfig()
            .getConfigurationSection("signs.swapDistrict.frame" + frameNum);
        if (sec == null) {
            plugin.getLogger().warning("[SwapClock] No sign config for swapDistrict.frame" + frameNum);
            return;
        }
        String worldName = sec.getString("world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        Block block = loc.getBlock();

        if (!(block.getState() instanceof Sign sign)) {
            plugin.getLogger().warning("[SwapClock] No sign block at swapDistrict.frame" + frameNum
                + " (" + block.getType() + ")");
            return;
        }

        sign.getSide(Side.FRONT).line(0, Component.text(l1));
        sign.getSide(Side.FRONT).line(1, Component.text(l2));
        sign.getSide(Side.FRONT).line(2, Component.text(l3));
        sign.getSide(Side.FRONT).line(3, Component.text(l4));
        sign.update(true);
    }

    private int parsePfn(String pfnStr) {
        try {
            return Integer.parseInt(pfnStr.replace("0x", "").replace("0X", ""), 16);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[SwapClock] Could not parse PFN '" + pfnStr
                + "' — defaulting victim to frame 1");
            return 1;
        }
    }
}
