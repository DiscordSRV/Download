package dev.vankka.dsrvdownloader.model;

public class WorkflowFileMetadata {

    public String identifier;

    @SuppressWarnings("unused") // Jackson
    public WorkflowFileMetadata() {}

    public WorkflowFileMetadata(String identifier) {
        this.identifier = identifier;
    }

}
