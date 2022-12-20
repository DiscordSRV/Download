package dev.vankka.dsrvdownloader.model.github;

import java.util.List;

public record WorkflowRunPaging(int total_count, List<WorkflowRun> workflow_runs) {}
