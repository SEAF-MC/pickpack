package net.onebeastchris.geyser.extension.pickpack.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PackEntry(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("subpack") String subpack
) {
    public PackEntry {
        if (uuid == null) uuid = "";
        if (subpack == null) subpack = "";
    }

    public PackEntry(String uuid) {
        this(uuid, "");
    }
}
