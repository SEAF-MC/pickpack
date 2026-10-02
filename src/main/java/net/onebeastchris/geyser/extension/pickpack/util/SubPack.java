package net.onebeastchris.geyser.extension.pickpack.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubPack(
        @JsonProperty("folder_name") String folderName,
        @JsonProperty("name") String name,
        @JsonProperty("memory_tier") int memoryTier
) {
    public SubPack {
        if (folderName == null) folderName = "";
        if (name == null) name = folderName;
    }
}
