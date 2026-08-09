package ust.tad.cdkmpsplugin.models.tadm;

import java.util.List;
import java.util.Objects;

public class ComponentType extends ModelElementType {

    private ComponentType parentType;

    public ComponentType() { super(); }

    public ComponentType(String name, String description,
                         List<Property> properties, List<Operation> operations,
                         ComponentType parentType) {
        super(name, description, properties, operations);
        this.parentType = parentType;
    }

    public ComponentType getParentType() { return parentType; }
    public void setParentType(ComponentType parentType) { this.parentType = parentType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentType)) return false;
        ComponentType ct = (ComponentType) o;
        return Objects.equals(getId(), ct.getId())
            && Objects.equals(getName(), ct.getName())
            && Objects.equals(parentType, ct.parentType);
    }

    @Override
    public int hashCode() { return Objects.hash(getId(), getName(), parentType); }

    @Override
    public String toString() {
        return "{name='" + getName() + "', parentType='"
            + (parentType != null ? parentType.getName() : null) + "'}";
    }
}
