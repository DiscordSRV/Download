package dev.vankka.dsrvdownloader.model.github;

public class WorkflowRun {

    public long id;
    public String head_sha;
    public String conclusion;
    public String head_branch;
    public String event;
    public Commit head_commit;
    public String html_url;

}
