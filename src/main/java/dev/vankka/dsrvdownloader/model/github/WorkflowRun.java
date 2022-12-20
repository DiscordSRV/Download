package dev.vankka.dsrvdownloader.model.github;

public record WorkflowRun(
        long id,
        String head_sha,
        String conclusion,
        String head_branch,
        String event,
        Commit head_commit,
        String html_url
) {}
