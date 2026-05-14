package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.misc.WorldLoadEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.manager.impl.NotificationManager;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import dev.mzc.client.utils.entity.HealthUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NotificationHud extends HudModule {
    public enum AlignedEnum {
        LEFT(),
        RIGHT();
        AlignedEnum() {
        }
    }

    private final Value<Double> maxWidthConfig = new NumberValue<>("MaxWidth", 300.0, 100.0, 500.0, 10.0);
    private final Value<Double> roundRadius = new NumberValue<>("RoundRadius", 6.0, 0.0, 20.0, 1.0);
    private final Value<Color> primaryColorConfig = new ColorValue("PrimaryColor", new Color(255, 183, 197, 255));
    private final Value<Color> backgroundColorConfig = new ColorValue("BackgroundColor", new Color(20, 20, 20, 200));
    // private final EnumValue<AlignedEnum> aligned = new EnumValue<>("Aligned", AlignedEnum.RIGHT);
    private final Value<Boolean> backgroundBlur = new BoolValue("BackgroundBlur", true);
    private final Value<Double> blurStrength = new NumberValue<>("BlurStrength", 15.0, 1.0, 30.0, 0.5, backgroundBlur::get);
    private final Value<Boolean> shadow = new BoolValue("Shadow", true);
    private final Value<Boolean> progressBar = new BoolValue("ProgressBar", true);

    private final Value<Boolean> totemPop = new BoolValue("TotemPop", true);
    private final Value<Boolean> selfPop = new BoolValue("SelfPop", true, totemPop::get);
    private final Value<Boolean> enemyPop = new BoolValue("EnemyPop", true, totemPop::get);
    private final Value<Boolean> killNotify = new BoolValue("KillNotify", true);
    private final Value<Boolean> winNotify = new BoolValue("WinNotify", true);
    private final Value<Boolean> lowHp = new BoolValue("LowHp", true);
    private final Value<Color> nameColor = new ColorValue("NameColor", new Color(255, 100, 100, 255));
    private final Value<Integer> lowHpThreshold = new NumberValue<>("LowHpThreshold", 8, 1, 20, 1, lowHp::get);
    private final Value<Boolean> durabilityWarning = new BoolValue("DurabilityWarning", true);
    private final Value<Integer> durabilityThreshold = new NumberValue<>("DurabilityThreshold", 20, 1, 50, 1, durabilityWarning::get);

    private final Value<Boolean> nearPlayerWarning = new BoolValue("NearPlayerWarning", true);
    private final Value<Double> nearPlayerRange = new NumberValue<>("NearPlayerRange", 12.0, 2.0, 64.0, 0.5, nearPlayerWarning::get);

    private static final Set<String> WIN_TRIGGERS = new HashSet<>(Arrays.asList(
            "1st Killer -", "1st Place -", "Winner -", "Winner-", " - Damage Dealt -", "Winning Team -", "1st -",
            "Winners:", "Winner:", "Winning Team:", " won the game!", "Top Seeker:", "1st Place:",
            "Last team standing!", "Winner #1 (", "Top Survivors", "Winners-", "Winners -", "Sumo Duel -",
            "Most Wool Placed -", "Your Overall Winstreak:"
    ));

    private final Map<UUID, Integer> popCounts = new HashMap<>();
    private Entity lastAttackedEntity = null;
    private long lastAttackTime = 0;
    private int lastProcessedKillId = -1;
    private boolean lowHpTriggered = false;
    private boolean durabilityTriggered = false;
    private final Set<UUID> nearPlayersInRange = new HashSet<>();

    public NotificationHud() {
        super("Notification", 10, 10);
    }

    @Override
    public void onEnable() {
        popCounts.clear();
        lastAttackedEntity = null;
        lastAttackTime = 0;
        lastProcessedKillId = -1;
        lowHpTriggered = false;
        durabilityTriggered = false;
        nearPlayersInRange.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        popCounts.clear();
        lastAttackedEntity = null;
        lastAttackTime = 0;
        lastProcessedKillId = -1;
        lowHpTriggered = false;
        durabilityTriggered = false;
        nearPlayersInRange.clear();
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (event.getType() != EventType.RECEIVE) return;

        Packet<?> packet = event.getPacket();

        if (winNotify.get()) {
            String winMsg = null;
            if (packet instanceof TitleS2CPacket(Text text)) {
                if (text != null) winMsg = text.getString();
            } else if (packet instanceof GameMessageS2CPacket(Text content, boolean overlay)) {
                if (content != null) winMsg = content.getString();
            }

            if (winMsg != null && !winMsg.isEmpty()) {
                String name = mc.player.getName().getString();
                if (matchWin(winMsg, name)) {
                    NotificationManager.send("Victory: 你赢了！", 5000);
                }
            }
        }

        if (killNotify.get() && packet instanceof GameMessageS2CPacket(Text content, boolean overlay) && !overlay) {
            if (content != null) {
                String msg = content.getString();
                String myName = mc.player.getGameProfile().name();
                if ((msg.contains("was killed by") || msg.contains("was knocked into the void by"))
                        && msg.contains(myName)) {
                    String victim = extractVictimName(msg);
                    if (victim.equals(myName)) return;
                    NotificationManager.send(getNameColorHex() + victim + "§r was killed by you.", 3000);
                }
            }
        }

        if (packet instanceof EntityStatusS2CPacket statusPacket) {
            if (statusPacket.getStatus() == 35) { // Totem Pop
                if (totemPop.get()) {
                    Entity entity = statusPacket.getEntity(mc.world);
                    if (entity instanceof PlayerEntity player) {
                        handleTotemPop(player);
                    }
                }
            } else if (statusPacket.getStatus() == 3) { // Death
                Entity entity = statusPacket.getEntity(mc.world);
                if (entity instanceof PlayerEntity) {
                    popCounts.remove(entity.getUuid());
                }

                if (killNotify.get()) {
                    if (entity != null && lastAttackedEntity != null && entity.getId() == lastAttackedEntity.getId()) {
                        if (System.currentTimeMillis() - lastAttackTime < 5000) {
                            NotificationManager.send(getNameColorHex() + entity.getName().getString() + "§r was killed by you.", 3000);
                            lastAttackedEntity = null;
                        }
                    }
                }
            }
        }
    }

    private String extractVictimName(String msg) {
        int idx = msg.indexOf(" was killed by");
        if (idx < 0) idx = msg.indexOf(" was knocked into the void by");
        if (idx <= 0) return "Unknown";
        String before = msg.substring(0, idx).trim();
        int space = before.lastIndexOf(' ');
        return space >= 0 ? before.substring(space + 1) : before;
    }

    private boolean matchWin(String s, String playerName) {
        if (s == null || s.isEmpty() || playerName == null || playerName.isEmpty()) return false;
        String t = stripFormatting(s);
        String pn = playerName.toLowerCase();
        for (String k : WIN_TRIGGERS) {
            int idx = t.indexOf(k);
            if (idx < 0) continue;
            String tail = t.substring(Math.min(t.length(), idx + k.length()));
            if (tail.toLowerCase().contains(pn)) return true;
        }
        return false;
    }

    private String stripFormatting(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private String getNameColorHex() {        Color c = nameColor.get();
        return String.format("§#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void handleTotemPop(PlayerEntity player) {
        boolean isSelf = player == mc.player;

        if (isSelf && !selfPop.get()) return;
        if (!isSelf && !enemyPop.get()) return;

        UUID uuid = player.getUuid();
        int count = popCounts.getOrDefault(uuid, 0) + 1;
        popCounts.put(uuid, count);

        if (isSelf) {
            NotificationManager.send("You popped §c" + count + "§r totem" + (count > 1 ? "s" : "") + "!", 3000);
        } else {
            NotificationManager.send(getNameColorHex() + player.getName().getString() + "§r popped §c" + count + "§r totem" + (count > 1 ? "s" : "") + "!", 3000);
        }
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (killNotify.get()) {
            lastAttackedEntity = event.getTargetEntity();
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 血量轮询击杀检测（补充 packet 检测，适配不发死亡包的服务器）
        if (killNotify.get() && lastAttackedEntity instanceof LivingEntity living) {
            if (HealthUtil.getEntityHealth(living) <= 0.1f
                    && living.getId() != lastProcessedKillId
                    && System.currentTimeMillis() - lastAttackTime < 5000) {
                NotificationManager.send(getNameColorHex() + living.getName().getString() + "§r was killed by you.", 3000);
                lastProcessedKillId = living.getId();
                lastAttackedEntity = null;
            }
        }

        if (lowHp.get()) {
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (health <= lowHpThreshold.get() && !mc.player.isDead() && !mc.player.isCreative() && !mc.player.isSpectator()) {
                if (!lowHpTriggered) {
                    NotificationManager.send("Low HP Warning! " + String.format("%.1f", health), 3000);
                    lowHpTriggered = true;
                }
            } else if (health > lowHpThreshold.get() + 2) {
                lowHpTriggered = false;
            }
        } else {
            lowHpTriggered = false;
        }

        if (durabilityWarning.get()) {
            boolean anyLow = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (!stack.isEmpty() && stack.isDamageable()) {
                    float damage = stack.getDamage();
                    float maxDamage = stack.getMaxDamage();
                    float durability = (maxDamage - damage) / maxDamage * 100f;
                    if (durability < durabilityThreshold.get()) {
                        anyLow = true;
                        break;
                    }
                }
            }
            if (anyLow) {
                if (!durabilityTriggered) {
                    NotificationManager.send("Your armor is breaking!", 3000);
                    durabilityTriggered = true;
                }
            } else {
                durabilityTriggered = false;
            }
        } else {
            durabilityTriggered = false;
        }

        if (nearPlayerWarning.get()) {
            double range = nearPlayerRange.get();
            Set<UUID> currentInRange = new HashSet<>();

            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (p.isRemoved()) continue;
                if (p.isSpectator()) continue;

                double d = mc.player.distanceTo(p);
                if (d > range) continue;

                UUID uuid = p.getUuid();
                currentInRange.add(uuid);

                if (!nearPlayersInRange.contains(uuid)) {
                    String name = getNameColorHex() + p.getName().getString() + "§r";
                    String msg = "附近玩家: " + name + " 距离 " + String.format("%.1f", d) + "m";
                    long id = (0x4E504C4159L << 32) ^ (uuid.hashCode() & 0xffffffffL);
                    NotificationManager.send(id, msg, 1200);
                }
            }

            nearPlayersInRange.clear();
            nearPlayersInRange.addAll(currentInRange);
        } else {
            nearPlayersInRange.clear();
        }
    }

    @Override
    public void onRender(DrawContext context) {
        boolean isLeft = (x + width / 2.0) < (context.getScaledWindowWidth() / 2.0);

        if (Sakura.MODULES.getModule(HudEditor.class).isEnabled()) {
            float[] size = NotificationManager.renderPreview(
                    new MatrixStack(),
                    x, y,
                    isLeft,
                    primaryColorConfig.get(),
                    backgroundColorConfig.get(),
                    maxWidthConfig.get().floatValue(),
                    backgroundBlur.get(),
                    blurStrength.get().floatValue(),
                    roundRadius.get().floatValue(),
                    shadow.get(),
                    progressBar.get()
            );
            width = size[0];
            height = size[1];
        } else {
            NotificationManager.render(
                    new MatrixStack(),
                    x, y,
                    isLeft,
                    primaryColorConfig.get(),
                    backgroundColorConfig.get(),
                    maxWidthConfig.get().floatValue(),
                    backgroundBlur.get(),
                    blurStrength.get().floatValue(),
                    roundRadius.get().floatValue(),
                    shadow.get(),
                    progressBar.get()
            );
        }
    }
}
