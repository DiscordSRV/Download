package dev.vankka.dsrvdownloader.config;

public class VersionChannelConfig {

    public String name;
    public String repoOwner;
    public String repoName;
    public String branch;
    public Type type;
    public int versionsToKeep;
    public boolean keepVersionsInMemory;

    public enum Type {

        /**
         * Uses GitHub repo releases.
         */
        RELEASE,

        /**
         * Uses GitHub branch commits.
         */
        COMMIT

    }
}
