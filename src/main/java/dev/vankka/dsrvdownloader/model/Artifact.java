package dev.vankka.dsrvdownloader.model;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Artifact {

    private final String fileName;
    private final long size;
    private final Path file;
    private byte[] content;

    public Artifact(
            String fileName,
            Path file,
            @Nullable byte[] content
    ) throws IOException {
        this(
                fileName,
                content != null ? content.length : Files.size(file),
                file,
                content
        );
    }

    public Artifact(
            String fileName,
            long size,
            Path file,
            @Nullable byte[] content
    ) {
        this.fileName = fileName;
        this.size = size;
        this.file = file;
        this.content = content;
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

    @Nullable
    public byte[] getContent() {
        return content;
    }

    public void removeFromMemory() {
        this.content = null;
    }
}
