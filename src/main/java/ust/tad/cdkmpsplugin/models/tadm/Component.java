package ust.tad.cdkmpsplugin.models.tadm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Component extends ModelElement {

    private ComponentType type;
    private List<Artifact> artifacts = new ArrayList<>();
    private Confidence confidence;

    public Component() { super(); }

    public Component(String name, String description, List<Property> properties,
                     List<Operation> operations, ComponentType type,
                     List<Artifact> artifacts, Confidence confidence) {
        super(name, description, properties, operations);
        this.type = type;
        this.artifacts = artifacts;
        this.confidence = confidence;
    }

    public ComponentType getType() { return type; }
    public void setType(ComponentType type) { this.type = type; }

    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Component)) return false;
        Component c = (Component) o;
        return Objects.equals(getId(), c.getId())
            && Objects.equals(getName(), c.getName())
            && Objects.equals(type, c.type)
            && Objects.equals(confidence, c.confidence);
    }

    @Override
    public int hashCode() { return Objects.hash(getId(), getName(), type, confidence); }

    @Override
    public String toString() {
        return "{name='" + getName() + "', type='"
            + (type != null ? type.getName() : null) + "', confidence='" + confidence + "'}";
    }
}
