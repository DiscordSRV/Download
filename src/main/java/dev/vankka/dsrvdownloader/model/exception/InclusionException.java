package dev.vankka.dsrvdownloader.model.exception;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;

public class InclusionException extends Exception {

    private String longer;

    public InclusionException(IOException e) {
        super("I/O Exception", e);
        this.longer = ExceptionUtils.getStackTrace(e);
    }

    public InclusionException(String message) {
        super(message, null, false, false);
    }

    public InclusionException(String message, String longer) {
        super(message, null, false, false);
        this.longer = longer;
    }

    public String getLonger() {
        return longer;
    }
}
