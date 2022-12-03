package dev.vankka.dsrvdownloader.util;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class IO implements AutoCloseable {

    private static final int BUFFER_SIZE = IOUtils.DEFAULT_BUFFER_SIZE;

    private final InputStream inputStream;
    private final boolean autoCloseInput;
    private final List<Con> outputs = new ArrayList<>(3);
    private final List<OutputStream> streams = new ArrayList<>();

    public IO(InputStream inputStream) {
        this(inputStream, true);
    }

    public IO(InputStream inputStream, boolean autoCloseInput) {
        this.inputStream = inputStream;
        this.autoCloseInput = autoCloseInput;
    }

    public IO withOutputStream(OutputStream outputStream) {
        streams.add(outputStream);
        outputs.add((bytes, size) -> outputStream.write(bytes, 0, size));
        return this;
    }

    public IO withDigest(MessageDigest digest) {
        outputs.add((bytes, size) -> digest.digest(bytes, 0, size));
        return this;
    }

    public void stream() throws IOException, DigestException {
        if (!autoCloseInput) {
            useStream(new BufferedInputStream(inputStream, BUFFER_SIZE));
            return;
        }

        try (BufferedInputStream buffered = new BufferedInputStream(inputStream, BUFFER_SIZE)) {
            useStream(buffered);
        }
    }

    private void useStream(BufferedInputStream inputStream) throws IOException, DigestException {
        byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        int size;
        while ((size = inputStream.read(buffer)) != -1) {
            for (var output : outputs) {
                output.accept(buffer, size);
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (OutputStream stream : streams) {
            stream.close();
        }
        inputStream.close();
    }

    @FunctionalInterface
    private interface Con {
        void accept(byte[] bytes, int size) throws IOException, DigestException;
    }
}
