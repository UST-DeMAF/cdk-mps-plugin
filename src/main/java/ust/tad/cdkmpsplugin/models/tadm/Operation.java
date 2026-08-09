package ust.tad.cdkmpsplugin.models.tadm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Operation {

    private String name;
    private List<Artifact> artifacts = new ArrayList<>();
    private Confidence confidence;

    public Operation() {}

    public Operation(String name, List<Artifact> artifacts, Confidence confidence) {
        this.name = name;
        this.artifacts = artifacts;
        this.confidence = confidence;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Operation)) return false;
        Operation op = (Operation) o;
        return Objects.equals(name, op.name) && Objects.equals(artifacts, op.artifacts)
            && Objects.equals(confidence, op.confidence);
    }

    @Override
    public int hashCode() { return Objects.hash(name, artifacts, confidence); }
}
