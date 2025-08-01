package dev.vankka.dsrvdownloader.config;

import java.util.List;
import java.util.Map;

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
        int pagesOfRunsToKeep,

        Map<String, Object> metadata
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
