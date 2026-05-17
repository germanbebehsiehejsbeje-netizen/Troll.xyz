package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

/**
 * Centralized team detection for Bedwars and team-based gamemodes.
 * All combat modules delegate their team checks here.
 */
public class Teams extends Module {

    public enum Mode {
        Scoreboard,
        ArmorColor,
        Both
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Both);
    private final BoolValue armorColorCheck = new BoolValue("Armor Color", true);
    private final BoolValue scoreboardCheck = new BoolValue("Scoreboard", true);
    private final BoolValue tabColorCheck = new BoolValue("Tab Name Color", true);

    private static Teams INSTANCE;

    public Teams() {
        super("Teams", Category.Combat);
        this.setType(ModuleType.Safe);
        INSTANCE = this;
    }

    public static Teams getInstance() {
        if (INSTANCE == null) {
            INSTANCE = Sakura.MODULES.getModule(Teams.class);
        }
        return INSTANCE;
    }

    /**
     * Central team check. Returns true if entity is on the same team as the local player.
     * Can be called even when the module is disabled — returns false in that case.
     */
    public boolean isTeammate(Entity entity) {
        if (!isEnabled()) return false;
        if (!(entity instanceof PlayerEntity player)) return false;
        if (mc.player == null || entity == mc.player) return false;

        switch (mode.get()) {
            case Scoreboard -> {
                return checkScoreboard(player);
            }
            case ArmorColor -> {
                return checkArmorColor(player);
            }
            case Both -> {
                // Scoreboard takes priority; if both players have a team assigned, use that.
                // Fall back to armor color if scoreboard data is absent.
                if (hasScoreboardTeam(mc.player) && hasScoreboardTeam(player)) {
                    return checkScoreboard(player);
                }
                return checkArmorColor(player);
            }
        }
        return false;
    }

    /**
     * Convenience: returns true if entity is NOT a teammate (i.e. valid attack target).
     */
    public boolean isEnemy(Entity entity) {
        return !isTeammate(entity);
    }

    // ─── Scoreboard ────────────────────────────────────────────────────────────

    private boolean checkScoreboard(PlayerEntity player) {
        if (!scoreboardCheck.get()) return false;
        if (mc.world == null) return false;

        Scoreboard scoreboard = mc.world.getScoreboard();
        Team myTeam = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());
        Team theirTeam = scoreboard.getScoreHolderTeam(player.getNameForScoreboard());

        if (myTeam == null || theirTeam == null) return false;
        return myTeam.isEqual(theirTeam);
    }

    private boolean hasScoreboardTeam(PlayerEntity player) {
        if (mc.world == null) return false;
        Scoreboard scoreboard = mc.world.getScoreboard();
        return scoreboard.getScoreHolderTeam(player.getNameForScoreboard()) != null;
    }

    // ─── Armor Color ───────────────────────────────────────────────────────────

    private boolean checkArmorColor(PlayerEntity player) {
        if (!armorColorCheck.get()) return false;

        int myColor = getLeatherArmorColor(mc.player);
        int theirColor = getLeatherArmorColor(player);

        if (myColor == -1 || theirColor == -1) return false;
        return myColor == theirColor;
    }

    private int getLeatherArmorColor(PlayerEntity player) {
        // Check all armor slots, return first dyed color found
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
            if (dyed != null) {
                return dyed.rgb();
            }
        }
        return -1;
    }
}
