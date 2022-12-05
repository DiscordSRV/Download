package dev.vankka.dsrvdownloader.model.github;

import dev.vankka.dsrvdownloader.model.Commit;

public class WorkflowRun {

    public long id;
    public String head_sha;
    public String conclusion;
    public String head_branch;
    public String event;
    public Commit head_commit;
    public String url;

}
