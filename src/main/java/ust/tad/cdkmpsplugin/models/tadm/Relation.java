package ust.tad.cdkmpsplugin.models.tadm;

import java.util.List;
import java.util.Objects;

public class Relation extends ModelElement {

    private RelationType type;
    private Component source;
    private Component target;
    private Confidence confidence;

    private static final String INVALID_MSG =
        "The source and the target of a relation must not be the same component.";

    public Relation() { super(); }

    public Relation(String name, String description, List<Property> properties,
                    List<Operation> operations, RelationType type,
                    Component source, Component target, Confidence confidence)
            throws InvalidRelationException {
        super(name, description, properties, operations);
        if (source.equals(target)) throw new InvalidRelationException(INVALID_MSG);
        this.type = type;
        this.source = source;
        this.target = target;
        this.confidence = confidence;
    }

    public RelationType getType() { return type; }
    public void setType(RelationType type) { this.type = type; }

    public Component getSource() { return source; }
    public void setSource(Component source) throws InvalidRelationException {
        if (target != null && source.equals(target)) throw new InvalidRelationException(INVALID_MSG);
        this.source = source;
    }

    public Component getTarget() { return target; }
    public void setTarget(Component target) throws InvalidRelationException {
        if (source != null && source.equals(target)) throw new InvalidRelationException(INVALID_MSG);
        this.target = target;
    }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relation)) return false;
        Relation r = (Relation) o;
        return Objects.equals(getId(), r.getId())
            && Objects.equals(getName(), r.getName())
            && Objects.equals(type, r.type)
            && Objects.equals(source, r.source)
            && Objects.equals(target, r.target);
    }

    @Override
    public int hashCode() { return Objects.hash(getId(), getName(), type, source, target); }

    @Override
    public String toString() {
        return "{name='" + getName() + "', type='"
            + (type != null ? type.getName() : null)
            + "', source='" + (source != null ? source.getName() : null)
            + "', target='" + (target != null ? target.getName() : null) + "'}";
    }
}
