package net.onebeastchris.geyser.extension.pickpack.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.logger;

public class ResourcePackLoader {
    public Map<String, ResourcePack> DEFAULT = new HashMap<>();
    public Map<String, ResourcePack> OPTIONAL = new HashMap<>();
    public final Map<String, ResourcePackManifest> PACKS_INFO = new HashMap<>();
    public final Map<String, List<SubPack>> PACK_SUBPACKS = new HashMap<>();

    private final Path optionalPacksPath;
    private final Path defaultPacksPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResourcePackLoader(Path optionalPacksPath, Path defaultPacksPath) {
        this.optionalPacksPath = optionalPacksPath;
        this.defaultPacksPath = defaultPacksPath;
        loadPacks();
    }

    public void reload() {
        OPTIONAL.clear();
        DEFAULT.clear();
        PACKS_INFO.clear();
        PACK_SUBPACKS.clear();
        loadPacks();
    }

    public void loadPacks() {
        try {
            OPTIONAL = loadFromFolder(optionalPacksPath);
            DEFAULT = loadFromFolder(defaultPacksPath);

            logger.info("Loaded " + DEFAULT.size() + " default packs!");
            logger.info("Loaded " + OPTIONAL.size() + " optional packs!");
        } catch (Exception e) {
            logger.error("Failed to load packs due to: " + (e.getMessage() != null ? e.getMessage() : e));
        }
    }

    public @Nullable ResourcePack getPack(String packId) {
        return DEFAULT.getOrDefault(packId, OPTIONAL.get(packId));
    }

    public List<SubPack> getSubpacks(String packId) {
        return PACK_SUBPACKS.getOrDefault(packId, Collections.emptyList());
    }

    public boolean hasSubpacks(String packId) {
        List<SubPack> subpacks = PACK_SUBPACKS.get(packId);
        return subpacks != null && !subpacks.isEmpty();
    }

    public @Nullable SubPack getSubpack(String packId, String folderName) {
        for (SubPack subPack : getSubpacks(packId)) {
            if (subPack.folderName().equalsIgnoreCase(folderName)) {
                return subPack;
            }
        }
        return null;
    }

    public HashMap<String, ResourcePack> loadFromFolder(Path path) {
        HashMap<String, ResourcePack> packs = new HashMap<>();

        for (File file : Objects.requireNonNull(path.toFile().listFiles())) {
            try {
                ResourcePack pack = ResourcePack.create(PackCodec.path(file.toPath()));
                String uuid = pack.manifest().header().uuid().toString();
                packs.put(uuid, pack);
                PACKS_INFO.put(uuid, pack.manifest());

                List<SubPack> subpacks = parseSubpacks(file);
                PACK_SUBPACKS.put(uuid, subpacks);
                if (!subpacks.isEmpty()) {
                    logger.debug("Found " + subpacks.size() + " subpacks in " + file.getName());
                }
            } catch (Exception e) {
                logger.error("Failed to load pack at " + file.getName() + " due to: " + (e.getMessage() != null ? e.getMessage() : e));
                if (logger.isDebug()) {
                    e.printStackTrace();
                }
            }
        }
        return packs;
    }

    private List<SubPack> parseSubpacks(File file) {
        List<SubPack> list = new ArrayList<>();
        try {
            if (file.isDirectory()) {
                File manifest = new File(file, "manifest.json");
                if (manifest.exists()) {
                    try (InputStream is = new FileInputStream(manifest)) {
                        readSubpacksJson(is, list);
                    }
                }
            } else {
                try (ZipFile zipFile = new ZipFile(file)) {
                    ZipEntry entry = zipFile.getEntry("manifest.json");
                    if (entry == null) {
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry e = entries.nextElement();
                            if (e.getName().equalsIgnoreCase("manifest.json")) {
                                entry = e;
                                break;
                            }
                        }
                    }
                    if (entry != null) {
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            readSubpacksJson(is, list);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read subpacks from " + file.getName() + ": " + e.getMessage());
        }
        return list;
    }

    private void readSubpacksJson(InputStream is, List<SubPack> list) {
        try {
            JsonNode root = mapper.readTree(is);
            if (root.has("subpacks") && root.get("subpacks").isArray()) {
                for (JsonNode node : root.get("subpacks")) {
                    String folderName = node.path("folder_name").asText("");
                    String name = node.path("name").asText(folderName);
                    int memoryTier = node.path("memory_tier").asInt(1);
                    if (!folderName.isEmpty()) {
                        list.add(new SubPack(folderName, name, memoryTier));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed parsing manifest json: " + e.getMessage());
        }
    }
}
