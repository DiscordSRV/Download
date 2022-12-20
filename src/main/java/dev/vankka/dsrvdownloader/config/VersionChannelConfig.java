package dev.vankka.dsrvdownloader.config;

import java.util.List;

public record VersionChannelConfig(
        String name,
        String repoOwner,
        String repoName,
        Type type,
        int versionsToKeep,
        int versionsToKeepInMemory,
        List<VersionArtifactConfig> artifacts,
        List<SecurityConfig> security,

        // Workflows only
        String branch,
        String workflowFile,
        int pagesOfRunsToKeep
) {
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
