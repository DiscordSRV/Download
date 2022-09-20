package dev.vankka.dsrvdownloader.model;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class Version {

    private final String name;
    private final long size;
    private final Path file;
    private final Path metaFile;
    private byte[] content;
    private Long expiry;

    public Version(String name, long size, Path file, @Nullable Path metaFile, @Nullable byte[] content) {
        this.name = name;
        this.size = size;
        this.file = file;
        this.metaFile = metaFile;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public Path getFile() {
        return file;
    }

    @Nullable
    public Path getMetaFile() {
        return metaFile;
    }

    @Nullable
    public byte[] getContent() {
        return content;
    }

    public Long getExpiry() {
        return expiry;
    }

    public void expireIn(Long expiry) {
        this.expiry = expiry;
        removeFromMemory(); // Don't keep expiring versions in memory
    }

    public void removeFromMemory() {
        this.content = null;
    }
}
