package dev.vankka.dsrvdownloader.model;

import java.util.Map;

public record MetadataResponse(Map<String, Object> metadata, Map<String, Map<String, Object>> artifactMetadata) {}
