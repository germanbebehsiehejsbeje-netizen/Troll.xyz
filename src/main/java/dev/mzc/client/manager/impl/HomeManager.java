package dev.mzc.client.manager.impl;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.client.Home;

import java.util.LinkedHashMap;
import java.util.Map;

public class HomeManager {
    private final Map<String, Map<String, HomeLocation>> homesByServer = new LinkedHashMap<>();
    private boolean loading;

    public boolean hasHome(String serverId, String name) {
        Map<String, HomeLocation> homes = homesByServer.get(serverId);
        if (homes == null) return false;
        return homes.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
    }

    public void setHome(String serverId, String name, HomeLocation location) {
        Map<String, HomeLocation> homes = homesByServer.computeIfAbsent(serverId, k -> new LinkedHashMap<>());
        String key = homes.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(name);
        homes.put(key, location);
        if (!loading && Sakura.CONFIG != null) {
            Sakura.CONFIG.saveHomes();
            updateModule();
        }
    }

    public void updateHome(String serverId, String name, HomeLocation location) {
        Map<String, HomeLocation> homes = homesByServer.computeIfAbsent(serverId, k -> new LinkedHashMap<>());
        String key = homes.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(name);
        homes.put(key, location);
        if (!loading && Sakura.CONFIG != null) {
            Sakura.CONFIG.saveHomes();
        }
    }

    public boolean removeHome(String serverId, String name) {
        Map<String, HomeLocation> homes = homesByServer.get(serverId);
        if (homes == null) return false;
        String key = homes.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (key == null) return false;
        homes.remove(key);
        if (homes.isEmpty()) {
            homesByServer.remove(serverId);
        }
        if (!loading && Sakura.CONFIG != null) {
            Sakura.CONFIG.saveHomes();
            updateModule();
        }
        return true;
    }

    public void clearHomes() {
        homesByServer.clear();
        if (!loading && Sakura.CONFIG != null) {
            Sakura.CONFIG.saveHomes();
            updateModule();
        }
    }

    public Map<String, HomeLocation> getHomes(String serverId) {
        return homesByServer.getOrDefault(serverId, Map.of());
    }

    public Map<String, Map<String, HomeLocation>> getAllHomes() {
        return homesByServer;
    }

    public void beginLoad() {
        loading = true;
    }

    public void endLoad() {
        loading = false;
        updateModule();
    }

    private void updateModule() {
        if (Sakura.MODULES == null) return;
        Home homeModule = Sakura.MODULES.getModule(Home.class);
        if (homeModule != null) {
            homeModule.refreshHomes();
        }
    }

    public record HomeLocation(String dimension, int x, int y, int z, float yaw, float pitch, boolean enabled, boolean beam, int beamColor) {
    }
}
