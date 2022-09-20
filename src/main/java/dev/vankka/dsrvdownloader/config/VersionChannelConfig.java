package dev.vankka.dsrvdownloader.config;

public class VersionChannelConfig {

    public String name;
    public String repoOwner;
    public String repoName;
    public Type type;
    public int versionsToKeep;
    public int versionToKeepInMemory;
    public String fileNameFormat;

    // Commit workflows only
    public String branch;

    public enum Type {

        /**
         * Uses GitHub repo releases.
         */
        RELEASE,

        /**
         * Uses GitHub workflows.
         */
        WORKFLOW

    }
}
