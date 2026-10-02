package net.onebeastchris.geyser.extension.pickpack.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.onebeastchris.geyser.extension.pickpack.PickPack;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.loader;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.logger;

public class FileSaveUtil {
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void save(List<PackEntry> list, String xuid) {
        Path jsonPath = PickPack.storagePath.resolve(xuid + ".json");
        saveToJsonFile(list, jsonPath);
    }

    public static List<PackEntry> load(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".json")) {
            return loadJson(path);
        } else if (fileName.endsWith(".txt")) {
            return migrateTxtToJson(path);
        }
        return new ArrayList<>();
    }

    public static List<PackEntry> loadJson(Path filepath) {
        List<PackEntry> packs = new ArrayList<>();
        if (Files.exists(filepath)) {
            try {
                packs = mapper.readValue(filepath.toFile(), new TypeReference<List<PackEntry>>() {});
            } catch (IOException e) {
                logger.error("Failed to read JSON from " + filepath.getFileName() + ": " + e.getMessage());
            }
        }

        // Remove packs that no longer exist
        if (packs.removeIf(entry -> loader.getPack(entry.uuid()) == null)) {
            saveToJsonFile(packs, filepath);
        }
        return packs;
    }

    public static List<PackEntry> migrateTxtToJson(Path txtPath) {
        List<String> rawPacks = readLinesFromFile(txtPath);
        List<PackEntry> entries = new ArrayList<>();
        for (String line : rawPacks) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (loader.getPack(trimmed) != null) {
                    entries.add(new PackEntry(trimmed, ""));
                }
            }
        }

        String xuid = txtPath.getFileName().toString().replace(".txt", "");
        Path jsonPath = PickPack.storagePath.resolve(xuid + ".json");
        saveToJsonFile(entries, jsonPath);

        try {
            Files.deleteIfExists(txtPath);
            logger.info("Migrated " + txtPath.getFileName() + " to " + jsonPath.getFileName());
        } catch (IOException e) {
            logger.warning("Failed to delete legacy file " + txtPath.getFileName() + ": " + e.getMessage());
        }

        return entries;
    }

    public static void saveToJsonFile(List<PackEntry> packs, Path filepath) {
        try {
            mapper.writeValue(filepath.toFile(), packs);
        } catch (IOException e) {
            logger.error("Failed to write JSON to " + filepath.getFileName() + ": " + e.getMessage());
        }
    }

    private static List<String> readLinesFromFile(Path filepath) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.error("Failed to read legacy text file " + filepath.getFileName() + ": " + e.getMessage());
        }
        return lines;
    }

    public static void makeDir(Path path, String name) {
        if (!path.toFile().exists()) {
            logger.info(name + " folder does not exist, creating...");
            if (!path.toFile().mkdirs()) {
                logger.error("Failed to create " + name + " folder");
            }
        }
    }
}
