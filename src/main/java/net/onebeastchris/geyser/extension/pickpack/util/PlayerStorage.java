package net.onebeastchris.geyser.extension.pickpack.util;

import net.onebeastchris.geyser.extension.pickpack.PickPack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.pack.ResourcePack;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.loader;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.logger;

public class PlayerStorage {
    /**
     * Map of XUIDs to their ordered list of pack entries.
     */
    public final Map<String, List<PackEntry>> cache;
    private final Map<String, Boolean> playerTransferPref = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerDescriptionPref = new ConcurrentHashMap<>();

    public boolean useTransferPacket(String xuid) {
        if (xuid == null || !playerTransferPref.containsKey(xuid)) {
            return PickPack.config != null && PickPack.config.useTransferPacket();
        }
        return playerTransferPref.get(xuid);
    }

    public void setUseTransferPacket(String xuid, boolean val) {
        if (xuid != null && !xuid.isEmpty()) {
            playerTransferPref.put(xuid, val);
        }
    }

    public boolean showPackDescription(String xuid) {
        if (xuid == null || !playerDescriptionPref.containsKey(xuid)) {
            return PickPack.config != null && PickPack.config.showPackDescription();
        }
        return playerDescriptionPref.get(xuid);
    }

    public void setShowPackDescription(String xuid, boolean val) {
        if (xuid != null && !xuid.isEmpty()) {
            playerDescriptionPref.put(xuid, val);
        }
    }

    public CompletableFuture<Void> reset(String xuid) {
        if (xuid != null && !xuid.isEmpty()) {
            playerTransferPref.remove(xuid);
            playerDescriptionPref.remove(xuid);
        }
        List<PackEntry> defaultEntries = new ArrayList<>();
        for (String uuid : loader.DEFAULT.keySet()) {
            defaultEntries.add(new PackEntry(uuid, ""));
        }
        return setPackEntries(xuid, defaultEntries);
    }

    public PlayerStorage() {
        cache = Collections.synchronizedMap(new HashMap<>());

        File[] files = PickPack.storagePath.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".json") || file.getName().endsWith(".txt")) {
                    String xuid = file.getName().replace(".json", "").replace(".txt", "");
                    List<PackEntry> loaded = FileSaveUtil.load(file.toPath());
                    cache.put(xuid, new ArrayList<>(loaded));
                    logger.debug("Loaded " + loaded.size() + " packs for " + xuid);
                }
            }
        }
    }

    public CompletableFuture<Void> setPackEntries(String xuid, List<PackEntry> entries) {
        if (xuid == null || xuid.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<PackEntry> copy = new ArrayList<>(entries);
        cache.put(xuid, copy);
        Executors.newSingleThreadExecutor().execute(() ->
                FileSaveUtil.save(copy, xuid)
        );
        return CompletableFuture.completedFuture(null);
    }


    public @NonNull List<PackEntry> getPackEntries(String xuid) {
        if (xuid != null && !xuid.isEmpty() && cache.containsKey(xuid)) {
            return new ArrayList<>(cache.get(xuid));
        } else {
            List<PackEntry> defaultEntries = new ArrayList<>();
            for (String uuid : loader.DEFAULT.keySet()) {
                defaultEntries.add(new PackEntry(uuid, ""));
            }
            return defaultEntries;
        }
    }

    public @NonNull List<ResourcePack> getPacks(String xuid) {
        List<ResourcePack> packs = new ArrayList<>();
        for (PackEntry entry : getPackEntries(xuid)) {
            ResourcePack resourcePack = loader.getPack(entry.uuid());
            if (resourcePack != null) {
                packs.add(resourcePack);
            }
        }
        return packs;
    }


    public String getSelectedSubpack(String xuid, String uuid) {
        if (xuid != null && !xuid.isEmpty() && cache.containsKey(xuid)) {
            for (PackEntry entry : cache.get(xuid)) {
                if (entry.uuid().equals(uuid)) {
                    return entry.subpack();
                }
            }
        }
        return "";
    }

    public CompletableFuture<Void> setPackSubpack(String xuid, String uuid, String subpack) {
        if (xuid == null || xuid.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<PackEntry> entries = new ArrayList<>(getPackEntries(xuid));
        for (int i = 0; i < entries.size(); i++) {
            PackEntry entry = entries.get(i);
            if (entry.uuid().equals(uuid)) {
                entries.set(i, new PackEntry(uuid, subpack));
                break;
            }
        }
        return setPackEntries(xuid, entries);
    }

    public CompletableFuture<Void> movePack(String xuid, int fromIndex, int toIndex) {
        if (xuid == null || xuid.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<PackEntry> entries = new ArrayList<>(getPackEntries(xuid));
        if (fromIndex >= 0 && fromIndex < entries.size() && toIndex >= 0 && toIndex < entries.size()) {
            PackEntry item = entries.remove(fromIndex);
            entries.add(toIndex, item);
            return setPackEntries(xuid, entries);
        }
        return CompletableFuture.completedFuture(null);
    }
}
