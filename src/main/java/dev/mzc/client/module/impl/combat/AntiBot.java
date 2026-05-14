package dev.mzc.client.module.impl.combat;

import com.mojang.authlib.GameProfile;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;

import java.util.*;

public class AntiBot extends Module {
    
    public enum Mode {
        Matrix,
        ReallyWorld
    }

    private final Set<UUID> suspectSet = new HashSet<>();
    public static Set<UUID> botSet = new HashSet<>();
    
    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.ReallyWorld);
    
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    public AntiBot() {
        super("AntiBot", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    public static AntiBot getInstance() {
        return Sakura.MODULES.getModule(AntiBot.class);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() != dev.mzc.client.events.EventType.RECEIVE) return;
        
        var packet = event.getPacket();
        
        if (packet instanceof PlayerListS2CPacket listPacket) {
            checkPlayerAfterSpawn(listPacket);
        } else if (packet instanceof PlayerRemoveS2CPacket removePacket) {
            removePlayerBecauseLeftServer(removePacket);
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        
        if (!suspectSet.isEmpty()) {
            mc.world.getPlayers().stream()
                .filter(p -> suspectSet.contains(p.getUuid()))
                .forEach(this::evaluateSuspectPlayer);
        }
        
        if (mode.get() == Mode.Matrix) {
            matrixMode();
        } else if (mode.get() == Mode.ReallyWorld) {
            reallyWorldMode();
        }
    }

    private void checkPlayerAfterSpawn(PlayerListS2CPacket listS2CPacket) {
        // Access entries through reflection or use public API
        // For now, we'll skip this check and rely on other detection methods
    }

    private void removePlayerBecauseLeftServer(PlayerRemoveS2CPacket removeS2CPacket) {
        removeS2CPacket.profileIds().forEach(uuid -> {
            suspectSet.remove(uuid);
            botSet.remove(uuid);
        });
    }

    private boolean isRealPlayer(GameProfile profile) {
        return profile.properties() != null && !profile.properties().isEmpty();
    }

    private void evaluateSuspectPlayer(PlayerEntity player) {
        List<ItemStack> armor = null;
        if (!isFullyEquipped(player)) {
            armor = getArmorItems(player);
        }
        if (isFullyEquipped(player) || hasArmorChanged(player, armor)) {
            botSet.add(player.getUuid());
        }
        suspectSet.remove(player.getUuid());
    }

    private List<ItemStack> getArmorItems(PlayerEntity entity) {
        ArrayList<ItemStack> armorItems = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armorItems.add(entity.getEquippedStack(slot));
        }
        return armorItems;
    }

    private ItemStack getArmorStack(PlayerEntity entity, int index) {
        if (index >= 0 && index < ARMOR_SLOTS.length) {
            return entity.getEquippedStack(ARMOR_SLOTS[index]);
        }
        return ItemStack.EMPTY;
    }

    private boolean isArmorItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Check if item is armor by trying to get the equipment slot
        return stack.getMaxDamage() > 0 && stack.getDamage() >= 0;
    }

    private void matrixMode() {
        Iterator<UUID> iterator = suspectSet.iterator();
        while (iterator.hasNext()) {
            UUID susPlayer = iterator.next();
            PlayerEntity entity = mc.world.getPlayerByUuid(susPlayer);
            if (entity != null) {
                String playerName = entity.getName().getString();
                boolean isNameBot = playerName.startsWith("CIT-") && 
                                   !playerName.contains("NPC") && 
                                   !playerName.contains("[ZNPC]");
                
                int armorCount = 0;
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack item = entity.getEquippedStack(slot);
                    if (item.isEmpty()) continue;
                    ++armorCount;
                }
                boolean isFullArmor = armorCount == 4;
                
                boolean isFakeUUID = !entity.getUuid().equals(
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes())
                );
                
                if (isFullArmor || isNameBot || isFakeUUID) {
                    botSet.add(susPlayer);
                }
            }
            iterator.remove();
        }
        
        if (mc.player.age % 100 == 0) {
            botSet.removeIf(uuid -> mc.world.getPlayerByUuid(uuid) == null);
        }
    }

    private void reallyWorldMode() {
        for (PlayerEntity entity : mc.world.getPlayers()) {
            String playerName = entity.getName().getString();
            UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
            
            if (entity.getUuid().equals(offlineUUID) || 
                botSet.contains(entity.getUuid()) || 
                playerName.contains("NPC") || 
                playerName.startsWith("[ZNPC]")) {
                continue;
            }
            botSet.add(entity.getUuid());
        }
    }

    public boolean isDuplicateProfile(GameProfile profile) {
        if (mc.getNetworkHandler() == null) return false;
        
        return mc.getNetworkHandler().getPlayerList().stream()
            .filter(player -> player.getProfile() != null)
            .filter(player -> player.getProfile().name().equals(profile.name()))
            .filter(player -> !player.getProfile().id().equals(profile.id()))
            .count() >= 1;
    }

    public boolean isFullyEquipped(PlayerEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!isArmorItem(stack) || stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasArmorChanged(PlayerEntity entity, List<ItemStack> prevArmor) {
        if (prevArmor == null) {
            return true;
        }
        List<ItemStack> currentArmorList = getArmorItems(entity);
        if (currentArmorList.size() != prevArmor.size()) {
            return true;
        }
        for (int i = 0; i < currentArmorList.size(); ++i) {
            if (!ItemStack.areEqual(currentArmorList.get(i), prevArmor.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBot(PlayerEntity entity) {
        if (entity == null) return false;
        
        AntiBot antiBot = Sakura.MODULES == null ? null : Sakura.MODULES.getModule(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) return false;
        
        String playerName = entity.getName().getString();
        boolean isNameBot = playerName.startsWith("CIT-") && 
                           !playerName.contains("NPC") && 
                           !playerName.startsWith("[ZNPC]");
        boolean isMarkedBot = botSet.contains(entity.getUuid());
        
        return isNameBot || isMarkedBot;
    }

    public static boolean isBot(net.minecraft.entity.Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        return isBot(player);
    }

    public static boolean isBot(UUID uuid) {
        return botSet.contains(uuid);
    }

    public void reset() {
        suspectSet.clear();
        botSet.clear();
    }

    @Override
    protected void onDisable() {
        reset();
    }
}
