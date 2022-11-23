package dev.vankka.dsrvdownloader.config;

import java.util.List;

public class VersionChannelConfig {

    public String name;
    public String repoOwner;
    public String repoName;
    public Type type;
    public int versionsToKeep;
    public int versionsToKeepInMemory;
    public List<VersionArtifactConfig> artifacts;
    public List<SecurityConfig> security;

    // Workflows only
    public String branch;
    public String workflowFile;
    public int pagesOfRunsToKeep;

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
