package dev.vankka.dsrvdownloader.model;

import java.util.List;

public class VersionCheck {

    public Status status;

    public int amount;
    public AmountSource amountSource;
    public String amountType;

    public boolean insecure;
    public List<String> securityIssues;

    public enum Status {
        UP_TO_DATE,
        OUTDATED,
        UNKNOWN
    }

    public enum AmountSource {
        GITHUB
    }

}
