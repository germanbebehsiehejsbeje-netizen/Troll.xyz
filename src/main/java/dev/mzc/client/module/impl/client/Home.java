package dev.mzc.client.module.impl.client;

import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.HomeManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import com.mojang.blaze3d.opengl.GlStateManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Home extends Module {
    private final Map<String, BoolValue> homeEnabledValues = new HashMap<>();
    private final Map<String, BoolValue> homeBeamValues = new HashMap<>();
    private final Map<String, ColorValue> homeBeamColorValues = new HashMap<>();
    private String lastServerId = "unknown";
    private boolean syncing;

    public Home() {
        super("Home", Category.Client);
        this.setType(ModuleType.All);
        refreshHomes();
    }

    public void refreshHomes() {
        Map<String, Boolean> homeStates = new HashMap<>();
        Map<String, Boolean> beamStates = new HashMap<>();
        Map<String, Color> beamColors = new HashMap<>();
        Value<?> hiddenValue = null;

        List<Value<?>> currentValues = new ArrayList<>(this.values);
        for (Value<?> value : currentValues) {
            if (value.getName().equals("Hidden")) {
                hiddenValue = value;
            } else if (value instanceof BoolValue b) {
                String n = value.getName();
                if (n.endsWith("__Beam")) {
                    String base = n.substring(0, n.length() - "__Beam".length());
                    beamStates.put(base, b.get());
                } else {
                    homeStates.put(n, b.get());
                }
            } else if (value instanceof ColorValue c) {
                String n = value.getName();
                if (n.endsWith("__BeamColor")) {
                    String base = n.substring(0, n.length() - "__BeamColor".length());
                    beamColors.put(base, c.get());
                }
            }
        }

        this.values.clear();
        if (hiddenValue != null) {
            this.values.add(hiddenValue);
        }

        homeEnabledValues.clear();
        homeBeamValues.clear();
        homeBeamColorValues.clear();

        String serverId = getCurrentServerId();
        lastServerId = serverId;
        for (Map.Entry<String, HomeManager.HomeLocation> e : Managers.HOME.getHomes(serverId).entrySet()) {
            String homeName = e.getKey();
            HomeManager.HomeLocation loc = e.getValue();

            boolean enabled = homeStates.getOrDefault(homeName, loc.enabled());
            BoolValue enabledVal = new BoolValue(homeName, enabled);
            homeEnabledValues.put(homeName, enabledVal);
            this.values.add(enabledVal);

            boolean beamEnabled = beamStates.getOrDefault(homeName, loc.beam());
            BoolValue beamVal = new BoolValue(homeName + "__Beam", beamEnabled, enabledVal::get);
            homeBeamValues.put(homeName, beamVal);
            this.values.add(beamVal);

            Color defaultBeamColor = new Color(loc.beamColor(), true);
            Color beamColor = beamColors.getOrDefault(homeName, defaultBeamColor);
            ColorValue beamColorVal = new ColorValue(homeName + "__BeamColor", beamColor, () -> enabledVal.get() && beamVal.get());
            homeBeamColorValues.put(homeName, beamColorVal);
            this.values.add(beamColorVal);
        }
    }

    public boolean isHomeEnabled(String name) {
        String serverId = getCurrentServerId();
        if (!Managers.HOME.hasHome(serverId, name)) return false;
        BoolValue v = homeEnabledValues.get(name);
        if (v != null) return v.get();
        return true;
    }

    public HomeManager.HomeLocation getHome(String name) {
        String serverId = getCurrentServerId();
        return Managers.HOME.getHomes(serverId).entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        String serverId = getCurrentServerId();
        if (!serverId.equals(lastServerId)) {
            refreshHomes();
        }

        if (syncing) return;
        if (homeEnabledValues.isEmpty()) return;

        String dim = mc.world.getRegistryKey().getValue().toString();

        for (Map.Entry<String, HomeManager.HomeLocation> e : Managers.HOME.getHomes(serverId).entrySet()) {
            String homeName = e.getKey();
            HomeManager.HomeLocation loc = e.getValue();

            BoolValue enabledVal = homeEnabledValues.get(homeName);
            BoolValue beamVal = homeBeamValues.get(homeName);
            ColorValue beamColorVal = homeBeamColorValues.get(homeName);
            if (enabledVal == null || beamVal == null || beamColorVal == null) continue;

            boolean enabled = enabledVal.get();
            boolean beam = beamVal.get();
            int beamColor = beamColorVal.get().getRGB();

            if (enabled != loc.enabled() || beam != loc.beam() || beamColor != loc.beamColor() || (loc.dimension() == null || loc.dimension().isEmpty())) {
                syncing = true;
                Managers.HOME.updateHome(serverId, homeName, new HomeManager.HomeLocation(
                        loc.dimension() == null || loc.dimension().isEmpty() ? dim : loc.dimension(),
                        loc.x(),
                        loc.y(),
                        loc.z(),
                        loc.yaw(),
                        loc.pitch(),
                        enabled,
                        beam,
                        beamColor
                ));
                syncing = false;
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        if (mc.world == null || mc.player == null) return;

        String serverId = getCurrentServerId();
        String curDim = mc.world.getRegistryKey().getValue().toString();
        int bottomY = mc.world.getBottomY();
        int topY = mc.world.getBottomY() + mc.world.getHeight();
        int viewDistanceChunks = mc.options.getViewDistance().getValue();
        double maxDist = viewDistanceChunks * 16.0 + 16.0;
        double maxDist2 = maxDist * maxDist;

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(false);

        for (Map.Entry<String, HomeManager.HomeLocation> e : Managers.HOME.getHomes(serverId).entrySet()) {
            String homeName = e.getKey();
            HomeManager.HomeLocation loc = e.getValue();

            BoolValue enabledVal = homeEnabledValues.get(homeName);
            BoolValue beamVal = homeBeamValues.get(homeName);
            ColorValue beamColorVal = homeBeamColorValues.get(homeName);

            boolean enabled = enabledVal == null ? loc.enabled() : enabledVal.get();
            boolean beam = beamVal == null ? loc.beam() : beamVal.get();
            Color color = beamColorVal == null ? new Color(loc.beamColor(), true) : beamColorVal.get();

            if (!enabled || !beam) continue;
            if (loc.dimension() != null && !loc.dimension().isEmpty() && !loc.dimension().equals(curDim)) continue;

            double cx = loc.x() + 0.5;
            double cy = loc.y() + 0.5;
            double cz = loc.z() + 0.5;
            double dx = mc.player.getX() - cx;
            double dy = mc.player.getY() - cy;
            double dz = mc.player.getZ() - cz;
            double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 > maxDist2) continue;

            int startY = Math.max(loc.y(), bottomY);
            if (startY >= topY) continue;

            double x1 = loc.x() + 0.35;
            double z1 = loc.z() + 0.35;
            double x2 = loc.x() + 0.65;
            double z2 = loc.z() + 0.65;

            Box beamBox = new Box(x1, startY, z1, x2, topY, z2);
            Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            Color line = new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
            Render3DUtil.drawFilledBox(event.getMatrices(), beamBox, fill);
            Render3DUtil.drawBoxOutline(event.getMatrices(), beamBox, line.getRGB(), 1.0f);
        }

        GlStateManager._depthMask(true);
    }

    public static String getCurrentServerId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return "unknown";
        if (mc.isInSingleplayer()) return "singleplayer";
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null && !mc.getCurrentServerEntry().address.isBlank()) {
            return mc.getCurrentServerEntry().address;
        }
        return "unknown";
    }
}
