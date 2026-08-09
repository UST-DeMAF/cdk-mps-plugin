package ust.tad.cdkmpsplugin.models.tadm;

import java.util.List;
import java.util.Objects;

public class RelationType extends ModelElementType {

    private RelationType parentType;

    public RelationType() { super(); }

    public RelationType(String name, String description,
                        List<Property> properties, List<Operation> operations,
                        RelationType parentType) {
        super(name, description, properties, operations);
        this.parentType = parentType;
    }

    public RelationType getParentType() { return parentType; }
    public void setParentType(RelationType parentType) { this.parentType = parentType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationType)) return false;
        RelationType rt = (RelationType) o;
        return Objects.equals(getId(), rt.getId())
            && Objects.equals(getName(), rt.getName())
            && Objects.equals(parentType, rt.parentType);
    }

    @Override
    public int hashCode() { return Objects.hash(getId(), getName(), parentType); }

    @Override
    public String toString() {
        return "{name='" + getName() + "', parentType='"
            + (parentType != null ? parentType.getName() : null) + "'}";
    }
}
