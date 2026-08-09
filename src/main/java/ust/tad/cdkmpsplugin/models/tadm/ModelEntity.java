package ust.tad.cdkmpsplugin.models.tadm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class ModelEntity {

    private String id = UUID.randomUUID().toString();
    private String name;
    private String description;
    private List<Property> properties = new ArrayList<>();
    private List<Operation> operations = new ArrayList<>();

    protected ModelEntity() {}

    protected ModelEntity(String name, String description,
                          List<Property> properties, List<Operation> operations) {
        this.name = name;
        this.description = description;
        this.properties = properties;
        this.operations = operations;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }

    public List<Operation> getOperations() { return operations; }
    public void setOperations(List<Operation> operations) { this.operations = operations; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelEntity)) return false;
        ModelEntity e = (ModelEntity) o;
        return Objects.equals(id, e.id) && Objects.equals(name, e.name)
            && Objects.equals(description, e.description)
            && Objects.equals(properties, e.properties)
            && Objects.equals(operations, e.operations);
    }

    @Override
    public int hashCode() { return Objects.hash(id, name, description, properties, operations); }
}
