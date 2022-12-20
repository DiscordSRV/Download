package dev.vankka.dsrvdownloader.util;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.security.DigestException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class IO implements AutoCloseable {

    private static final int BUFFER_SIZE = IOUtils.DEFAULT_BUFFER_SIZE;

    private final InputStream inputStream;
    private boolean autoCloseInput;
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
        if (!(outputStream instanceof BufferedOutputStream)) {
            outputStream = new BufferedOutputStream(outputStream, BUFFER_SIZE);
        }

        OutputStream finalStream = outputStream;
        streams.add(finalStream);
        outputs.add((bytes, size) -> finalStream.write(bytes, 0, size));
        return this;
    }

    public IO withDigest(MessageDigest digest) {
        outputs.add((bytes, size) -> digest.update(bytes, 0, size));
        return this;
    }

    public void stream() throws IOException, DigestException {
        InputStream stream = inputStream instanceof BufferedInputStream
                             ? inputStream
                             : new BufferedInputStream(inputStream, BUFFER_SIZE);

        if (!autoCloseInput) {
            useStream(stream);
            return;
        }

        try (InputStream str = stream) {
            useStream(str);
        } finally {
            autoCloseInput = false;
        }
    }

    private void useStream(InputStream inputStream) throws IOException, DigestException {
        byte[][] arrays = new byte[outputs.size()][];
        for (int i = 0; i < outputs.size(); i++) {
            // create a byte array for each output
            // since some consumers touch the byte array contents
            arrays[i] = new byte[BUFFER_SIZE];
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        int size;
        while ((size = inputStream.read(buffer)) > 0) {
            for (int i = 0; i < outputs.size(); i++) {
                byte[] arr = arrays[i];
                Con output = outputs.get(i);

                System.arraycopy(buffer, 0, arr, 0, size);
                output.accept(arr, size);
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (OutputStream stream : streams) {
            stream.close();
        }
        if (autoCloseInput) {
            inputStream.close();
        }
    }

    @FunctionalInterface
    private interface Con {
        void accept(byte[] bytes, int size) throws IOException, DigestException;
    }
}
