package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;

public class NoRender extends Module {
    // Overlay
    public final BoolValue noHurtCam = new BoolValue("No Hurt Cam", false);
    public final BoolValue noPortalOverlay = new BoolValue("No Portal Overlay", false);
    public final BoolValue noSpyglassOverlay = new BoolValue("No Spyglass Overlay", false);
    public final BoolValue noNausea = new BoolValue("No Nausea", false);
    public final BoolValue noPumpkinOverlay = new BoolValue("No Pumpkin Overlay", false);
    public final BoolValue noPowderedSnowOverlay = new BoolValue("No Powdered Snow Overlay", false);
    public final BoolValue noFireOverlay = new BoolValue("No Fire Overlay", false);
    public final BoolValue noLiquidOverlay = new BoolValue("No Liquid Overlay", false);
    public final BoolValue noInWallOverlay = new BoolValue("No In Wall Overlay", false);
    public final BoolValue noVignette = new BoolValue("No Vignette", false);
    public final BoolValue noTotemAnimation = new BoolValue("No Totem Animation", false);

    // HUD
    public final BoolValue noBossBar = new BoolValue("No Boss Bar", false);
    public final BoolValue noScoreboard = new BoolValue("No Scoreboard", false);
    public final BoolValue noCrosshair = new BoolValue("No Crosshair", false);
    public final BoolValue noTitle = new BoolValue("No Title", false);
    public final BoolValue noHeldItemName = new BoolValue("No Held Item Name", false);
    public final BoolValue noPotionIcons = new BoolValue("No Potion Icons", false);

    // World
    public final BoolValue noWeather = new BoolValue("No Weather", false);
    public final BoolValue noFog = new BoolValue("No Fog", false);
    public final BoolValue noBlindness = new BoolValue("No Blindness", false);
    public final BoolValue noDarkness = new BoolValue("No Darkness", false);
    public final BoolValue noExplosionParticles = new BoolValue("No Explosion Particles", false);
    public final BoolValue noBlockBreakParticles = new BoolValue("NoBlockBreakParticles", false);
    public final BoolValue noEatParticles = new BoolValue("NoEatParticles", false);
    public final BoolValue noPotionParticles = new BoolValue("NoPotionParticles", false);

    public NoRender() {
        super("NoRender", Category.Render);
        this.setType(ModuleType.All);
    }

    public boolean getNoHurtCam() {
        return isEnabled() && noHurtCam.get();
    }

    public boolean noPortalOverlay() {
        return isEnabled() && noPortalOverlay.get();
    }

    public boolean noSpyglassOverlay() {
        return isEnabled() && noSpyglassOverlay.get();
    }

    public boolean noNausea() {
        return isEnabled() && noNausea.get();
    }

    public boolean noPumpkinOverlay() {
        return isEnabled() && noPumpkinOverlay.get();
    }

    public boolean noPowderedSnowOverlay() {
        return isEnabled() && noPowderedSnowOverlay.get();
    }

    public boolean noFireOverlay() {
        return isEnabled() && noFireOverlay.get();
    }

    public boolean noLiquidOverlay() {
        return isEnabled() && noLiquidOverlay.get();
    }

    public boolean noInWallOverlay() {
        return isEnabled() && noInWallOverlay.get();
    }

    public boolean noVignette() {
        return isEnabled() && noVignette.get();
    }

    public boolean noTotemAnimation() {
        return isEnabled() && noTotemAnimation.get();
    }

    public boolean noBossBar() {
        return isEnabled() && noBossBar.get();
    }

    public boolean noScoreboard() {
        return isEnabled() && noScoreboard.get();
    }

    public boolean noCrosshair() {
        return isEnabled() && noCrosshair.get();
    }

    public boolean noTitle() {
        return isEnabled() && noTitle.get();
    }

    public boolean noHeldItemName() {
        return isEnabled() && noHeldItemName.get();
    }

    public boolean noPotionIcons() {
        return isEnabled() && noPotionIcons.get();
    }

    public boolean noWeather() {
        return isEnabled() && noWeather.get();
    }

    public boolean noFog() {
        return isEnabled() && noFog.get();
    }

    public boolean noBlindness() {
        return isEnabled() && noBlindness.get();
    }

    public boolean noDarkness() {
        return isEnabled() && noDarkness.get();
    }

    public boolean noExplosionParticles() {
        return isEnabled() && noExplosionParticles.get();
    }

    public boolean noBlockBreakParticles() {
        return isEnabled() && noBlockBreakParticles.get();
    }

    public boolean noEatParticles() {
        return isEnabled() && noEatParticles.get();
    }

    public boolean noPotionParticles() {
        return isEnabled() && noPotionParticles.get();
    }
}