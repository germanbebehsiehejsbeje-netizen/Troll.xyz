package dev.mzc.client.module;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.input.MouseButtonEvent;
import dev.mzc.client.events.misc.KeyAction;
import dev.mzc.client.events.misc.KeyEvent;
import dev.mzc.client.events.render.Render2DEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.NotificationManager;
import dev.mzc.client.module.impl.client.*;
import dev.mzc.client.module.impl.combat.*;
import dev.mzc.client.module.impl.combat.elytratarget.ElytraTargetModule;
import dev.mzc.client.module.impl.hud.*;
import dev.mzc.client.module.impl.misc.*;
import dev.mzc.client.module.impl.movement.*;
import dev.mzc.client.module.impl.movement.FastStairs;
import dev.mzc.client.module.impl.player.*;
import dev.mzc.client.module.impl.player.inventory.ChestStealer;
import dev.mzc.client.module.impl.player.mine.*;
import dev.mzc.client.module.impl.render.*;
import dev.mzc.client.values.Value;
import dev.mzc.client.utils.TranslationManager;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static dev.mzc.client.Sakura.mc;

public class ModuleManager {
    private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

    private void init() {
        // Combat
        add(new AttributeSwap());
        add(new AutoPot());
        add(new AntiBot());
        add(new AutoBlock());
        add(new AutoBow());
        add(new SafeCrystal());
        add(new MaceSwap());
        add(new MaceKill());
        add(new TPAura());
        add(new AutoCombat());
        add(new AutoDtap());
        add(new AntiFireball());
        add(new CrystalBlocker());
        add(new Reach());
        add(new Backtrack());
        add(new TPAttack());
        add(new AimAssist());
        add(new SafeAnchor());
        add(new KillAura());
        add(new MutiAura());
        add(new HitBox());
        add(new Criticals());
        add(new AutoClicker());
        add(new PearlLauncher());
        add(new AutoThrow());
        add(new AutoRod());
        add(new SpinKB());
        add(new AutoCrystal());
        add(new TimerRange());
        add(new ElytraTargetModule());
        add(new Teams());

        // Movement
        add(new AutoSprint());
        add(new MoveFix());
        add(new Step());
        add(new NoJumpDelay());
        add(new AntiBlockPush());
        add(new SafeWalk());
        add(new AutoWTap());
        add(new Velocity());
        add(new FastStairs());
        add(new InventoryMove());
        add(new BetterSneak());
        add(new Fly());
        add(new ElytraFly());
        add(new BoatFly());
        add(new AirJump());
        add(new AutoJumpReset());
        add(new NoSlow());
        add(new Stuck());
        add(new ClickTP());
        add(new Speed());
        add(new TPPlace());
        add(new Spider());
        add(new Parkour());
        add(new TargetStrafe());
        add(new Scaffold());
        add(new FakeLag());
        add(new LegitStrafe());
        add(new PixelSurf());
        add(new AntiAim());
        add(new HvHAimbot());
        add(new EdgeBug());
        add(new MovementHelper());
        add(new LongJump());

        // Player
        add(new AutoArmor());
        add(new Blink());
        add(new FakePlayer());
        add(new GhostHand());
        add(new PacketEat());
        add(new Replenish());
        add(new AutoElytra());
        add(new TimerModule());
        add(new AutoTool());
        add(new AirPlace());
        add(new FastPlace());
        add(new FastUse());
        add(new AutoTotem());
        add(new MiddleClickExtra());
        add(new AutoWindBomb());
        add(new InvSort());
        add(new InvManager());
        add(new AutoLog());
        add(new Freecam());
        add(new NoFall());
        add(new AutoWater());
        add(new AutoMine());
        add(new PacketMine());
        add(new Nucker());
        add(new NoBreakDelay());
        add(new ChestStealer());
        add(new AutoSoup());
        add(new AutoEat());
        add(new AutoFarm());

        //Misc
        add(new BetterInv());
        add(new AutoGG());
        add(new ChatSuffix());
        add(new Spam());
        add(new AutoKouZi());
        add(new AutoRespawn());
        add(new Disabler());
        add(new IQBoost());
        add(new NoRotate());
        add(new NameProtect());
        add(new BetterChat());
        add(new FakeCoords());
        add(new ItemPhysics());
        add(new NoFPSLimit());
        add(new BetterFPS());
        add(new MusicPlayer());
        add(new SmoothSwap());
        add(new Auto32k());
        add(new AutoSign());
        add(new XCarry());
        add(new AntiAFK());
        add(new CheatDetector());
        add(new FakeScoreboard());
        add(new SpearTarget());
        add(new AutoFlyme());

        // Render
        add(new SmoothSwap());
        add(new AspectRatio());
        add(new Atmosphere());
        add(new CameraClip());
        add(new AttackEffect());
        add(new Crystal());
        add(new Fullbright());
        add(new Freelook());
        add(new Hat());
        add(new JumpCircles());
        add(new NameTags());
        add(new NoRender());
        add(new TargetESP());
        add(new SwingAnimation());
        add(new TotemParticles());
        add(new ViewModel());
        add(new XRay());
        add(new AntiVanish());
        add(new BlockESP());
        add(new EntityESP());
        add(new BlockOutline());
        add(new Animations());
        add(new Trail());
        add(new KillEffect());
        add(new Fov());
        add(new CubeParticles());
        add(new BedTrap());
        add(new TotemPopChams());

        // Client
        add(new AutoHeypixel());
        add(new Capes());
        add(new Chat());
        add(new ClickGui());
        add(new HudEditor());
        add(new Friend());
        add(new Home());
        add(new Skin());
        add(new BaritoneControl());
        //add(new ScreenshotBypass());

        // HUD
        add(new DynamicIslandHud());
        add(new FPSHud());
        add(new HotbarHud());
        add(new KeybindsHud());
        add(new KeyStrokesHud());
        add(new ModuleListHud());
        add(new MSHud());
        add(new NotificationHud());
        add(new NotifyHud());
        add(new TargetHud());
        add(new PotionHud());
        add(new ArmorHud());
        add(new HandItemHud());
        add(new ScoreboardHud());
        add(new WatermarkHud());
        add(new Tracers());
        add(new dev.mzc.client.module.impl.hud.InventoryHud());
        add(new DroppedItemsHud());
    }

    public ModuleManager() {
        Sakura.EVENT_BUS.subscribe(this);
        init();
    }

    private void add(Module module) {
        for (final Field field : module.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                final Object obj = field.get(module);
                if (obj instanceof Value<?>) {
                    boolean alreadyPresent = false;
                    for (Value<?> v : module.getValues()) {
                        if (v == obj) {
                            alreadyPresent = true;
                            break;
                        }
                    }
                    if (!alreadyPresent) {
                        module.getValues().add((Value<?>) obj);
                    }
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        modules.put(module.getClass(), module);
    }

    public Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public Module getModule(String name) {
        for (Module module : modules.values()) {
            if (module.getEnglishName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    // Alias for compatibility
    public Module getModuleByString(String name) {
        return getModule(name);
    }

    public <T extends Module> T getModule(Class<T> cls) {
        return cls.cast(modules.get(cls));
    }

    public List<Module> getModsByCategory(Category m) {
        return modules.values().stream()
                .filter(module -> module.getCategory() == m)
                .sorted(Comparator.comparing(Module::getEnglishName))
                .collect(Collectors.toList());
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (mc.currentScreen != null) return;
        if (event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;
        if (event.getKey() == GLFW.GLFW_KEY_F2) return;

        boolean isPress = event.getAction() == KeyAction.Press;
        boolean isRelease = event.getAction() == KeyAction.Release;

        List<Module> affectedModules = new ArrayList<>();
        boolean hasEnabling = false;

        for (Module module : modules.values()) {
            if (module.getKey() != event.getKey()) continue;

            if (module.getBindMode() == Module.BindMode.Toggle && isPress) {
                if (!module.isEnabled()) hasEnabling = true;
                affectedModules.add(module);
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    hasEnabling = true;
                    affectedModules.add(module);
                } else if (isRelease && module.isEnabled()) {
                    affectedModules.add(module);
                }
            }
        }

        for (Module module : affectedModules) {
            if (module.getBindMode() == Module.BindMode.Toggle) {
                boolean enabling = !module.isEnabled();
                sendToggleNotification(module, enabling, "", false);
                module.toggle();
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    sendToggleNotification(module, true, " §8(Hold)", false);
                    module.setState(true);
                } else if (isRelease && module.isEnabled()) {
                    sendToggleNotification(module, false, "", false);
                    module.setState(false);
                }
            }
        }

        if (!affectedModules.isEmpty()) {
            if (hasEnabling) {
                Managers.SOUND.playSound(Managers.SOUND.ENABLE);
            } else {
                Managers.SOUND.playSound(Managers.SOUND.DISABLE);
            }
        }
    }

    private void sendToggleNotification(Module module, boolean enabling, String suffix, boolean playSound) {
        String name = module.getDisplayName();
        String status = enabling
                ? TranslationManager.get("ui.module_enabled", "§a enabled")
                : TranslationManager.get("ui.module_disabled", "§c disabled");
        NotificationManager.send(module.hashCode(), "§7" + name + status + suffix, 3000L);
        if (playSound) {
            Managers.SOUND.playSound(enabling ? Managers.SOUND.ENABLE : Managers.SOUND.DISABLE);
        }
    }

    @EventHandler
    public void onKey(MouseButtonEvent e) {
        if (mc.currentScreen != null) return;

        if (e.getAction() == KeyAction.Press) {
            int button = e.getButton();
            // Map mouse buttons to negative key codes to distinguish from keyboard keys
            // Use -100 - button as convention (e.g., Button 0 -> -100, Button 4 -> -104)
            int keyCode = -100 - button;

            List<Module> affectedModules = new ArrayList<>();
            boolean hasEnabling = false;

            for (Module module : modules.values()) {
                if (module.getKey() == keyCode) {
                    if (module.getBindMode() == Module.BindMode.Toggle) {
                        if (!module.isEnabled()) hasEnabling = true;
                        affectedModules.add(module);
                    } else if (module.getBindMode() == Module.BindMode.Hold) {
                         if (!module.isEnabled()) {
                             hasEnabling = true;
                             affectedModules.add(module);
                         }
                    }
                }
            }

            for (Module module : affectedModules) {
                 if (module.getBindMode() == Module.BindMode.Toggle) {
                     boolean enabling = !module.isEnabled();
                     sendToggleNotification(module, enabling, "", false);
                     module.toggle();
                 } else if (module.getBindMode() == Module.BindMode.Hold) {
                     sendToggleNotification(module, true, " §8(Hold)", false);
                     module.setState(true);
                 }
            }

            if (!affectedModules.isEmpty()) {
                if (hasEnabling) {
                    Managers.SOUND.playSound(Managers.SOUND.ENABLE);
                } else {
                    Managers.SOUND.playSound(Managers.SOUND.DISABLE);
                }
            }
        } else if (e.getAction() == KeyAction.Release) {
             int button = e.getButton();
             int keyCode = -100 - button;

             List<Module> affectedModules = new ArrayList<>();

             for (Module module : modules.values()) {
                 if (module.getKey() == keyCode && module.getBindMode() == Module.BindMode.Hold && module.isEnabled()) {
                     affectedModules.add(module);
                 }
             }

             for (Module module : affectedModules) {
                 sendToggleNotification(module, false, "", false);
                 module.setState(false);
             }

             if (!affectedModules.isEmpty()) {
                 Managers.SOUND.playSound(Managers.SOUND.DISABLE);
             }
        }
    }

    public Collection<HudModule> getAllHudModules() {
        return modules.values().stream()
                .filter(HudModule.class::isInstance)
                .map(HudModule.class::cast)
                .collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRender2D(Render2DEvent event) {
        for (HudModule module : getAllHudModules()) {
            if (module.isState()) {
                module.renderInGame(event.getContext());
            }
        }
    }
}
