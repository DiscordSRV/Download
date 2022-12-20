package dev.vankka.dsrvdownloader.model;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Artifact {

    private final String identifier;
    private final String fileName;
    private final long size;
    private final Path file;
    private final Path metaFile;
    private byte[] content;
    private final String sha256;

    public Artifact(
            String identifier,
            String fileName,
            Path file,
            @Nullable Path metaFile,
            @Nullable byte[] content,
            String sha256
    ) throws IOException {
        this(
                identifier,
                fileName,
                content != null ? content.length : Files.size(file),
                file,
                metaFile,
                content,
                sha256
        );
    }

    public Artifact(
            String identifier,
            String fileName,
            long size,
            Path file,
            @Nullable Path metaFile,
            @Nullable byte[] content,
            String sha256
    ) {
        this.identifier = identifier;
        this.fileName = fileName;
        this.size = size;
        this.file = file;
        this.metaFile = metaFile;
        this.content = content;
        this.sha256 = sha256;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public Path getFile() {
        return file;
    }

    public Path getMetaFile() {
        return metaFile;
    }

    @Nullable
    public byte[] getContent() {
        return content;
    }

    public String getSha256() {
        return sha256;
    }

    public void removeFromMemory() {
        this.content = null;
    }
}
