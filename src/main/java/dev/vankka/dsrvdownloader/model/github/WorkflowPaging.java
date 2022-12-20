package dev.vankka.dsrvdownloader.model.github;

import java.util.List;

public record WorkflowPaging(int total_count, List<Workflow> workflows) {}
