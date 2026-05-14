package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.misc.FakeCoords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gui.hud.DebugHud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(DebugHud.class)
public class MixinDebugHud {
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/DebugHud;drawText(Lnet/minecraft/client/gui/DrawContext;Ljava/util/List;Z)V",
                    ordinal = 0
            ),
            index = 1
    )
    private List<String> onModifyLeftDebugText(List<String> original) {
        FakeCoords mod = Sakura.MODULES.getModule(FakeCoords.class);
        if (mod == null || !mod.isEnabled()) return original;

        long xOff = mod.getXOffset();
        long zOff = mod.getZOffset();
        if (xOff == 0L && zOff == 0L) return original;

        if (original == null || original.isEmpty()) return original;

        List<String> out = new ArrayList<>(original.size());
        long chunkXOff = xOff / 16L;
        long chunkZOff = zOff / 16L;

        for (String line : original) {
            if (line == null) {
                out.add(null);
                continue;
            }

            if (line.startsWith("XYZ: ")) {
                out.add(rewriteXyz(line, xOff, zOff));
                continue;
            }

            if (line.startsWith("Block: ")) {
                out.add(rewriteBlock(line, xOff, zOff));
                continue;
            }

            if (line.startsWith("Chunk: ")) {
                out.add(rewriteChunk(line, chunkXOff, chunkZOff));
                continue;
            }

            out.add(line);
        }

        return out;
    }

    private String rewriteXyz(String line, long xOff, long zOff) {
        String rest = line.substring(5);
        String[] parts = rest.split(" / ");
        if (parts.length != 3) return line;

        String xStr = parts[0].trim();
        String zStr = parts[2].trim();

        double x;
        int xDecimals = decimals(xStr);
        int zDecimals = decimals(zStr);
        double z;
        try {
            x = Double.parseDouble(xStr);
            z = Double.parseDouble(zStr);
        } catch (NumberFormatException ignored) {
            return line;
        }

        double xNew = x + xOff;
        double zNew = z + zOff;
        parts[0] = formatDouble(xNew, xDecimals);
        parts[2] = formatDouble(zNew, zDecimals);
        return "XYZ: " + parts[0].trim() + " / " + parts[1].trim() + " / " + parts[2];
    }

    private String rewriteBlock(String line, long xOff, long zOff) {
        String rest = line.substring(7).trim();
        String[] parts = rest.split(" ");
        if (parts.length < 3) return line;

        long x;
        long z;
        try {
            x = Long.parseLong(parts[0]);
            z = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {
            return line;
        }

        parts[0] = Long.toString(x + xOff);
        parts[2] = Long.toString(z + zOff);
        return "Block: " + parts[0] + " " + parts[1] + " " + parts[2];
    }

    private String rewriteChunk(String line, long chunkXOff, long chunkZOff) {
        String rest = line.substring(7);
        int inIdx = rest.indexOf(" in ");
        String left = inIdx >= 0 ? rest.substring(0, inIdx) : rest;
        String right = inIdx >= 0 ? rest.substring(inIdx) : "";

        String[] parts = left.trim().split(" ");
        if (parts.length < 3) return line;

        long x;
        long z;
        try {
            x = Long.parseLong(parts[0]);
            z = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {
            return line;
        }

        parts[0] = Long.toString(x + chunkXOff);
        parts[2] = Long.toString(z + chunkZOff);
        String rebuiltLeft = parts[0] + " " + parts[1] + " " + parts[2];
        return "Chunk: " + rebuiltLeft + right;
    }

    private int decimals(String s) {
        int dot = s.indexOf('.');
        if (dot < 0) return 0;
        return Math.max(0, s.length() - dot - 1);
    }

    private String formatDouble(double v, int decimals) {
        if (decimals <= 0) return Long.toString(Math.round(v));
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
