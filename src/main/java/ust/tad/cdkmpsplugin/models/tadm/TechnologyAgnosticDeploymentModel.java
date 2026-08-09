package ust.tad.cdkmpsplugin.models.tadm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TechnologyAgnosticDeploymentModel {

    private UUID id = UUID.randomUUID();
    private UUID transformationProcessId;
    private List<Property> properties = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();
    private List<ComponentType> componentTypes = new ArrayList<>();
    private List<RelationType> relationTypes = new ArrayList<>();

    public TechnologyAgnosticDeploymentModel() {}

    public TechnologyAgnosticDeploymentModel(UUID transformationProcessId,
            List<Property> properties, List<Component> components,
            List<Relation> relations, List<ComponentType> componentTypes,
            List<RelationType> relationTypes) {
        this.transformationProcessId = transformationProcessId;
        this.properties = properties;
        this.components = components;
        this.relations = relations;
        this.componentTypes = componentTypes;
        this.relationTypes = relationTypes;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTransformationProcessId() { return transformationProcessId; }
    public void setTransformationProcessId(UUID transformationProcessId) {
        this.transformationProcessId = transformationProcessId;
    }

    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }

    public List<Component> getComponents() { return components; }
    public void setComponents(List<Component> components) { this.components = components; }

    public List<Relation> getRelations() { return relations; }
    public void setRelations(List<Relation> relations) { this.relations = relations; }

    public List<ComponentType> getComponentTypes() { return componentTypes; }
    public void setComponentTypes(List<ComponentType> componentTypes) {
        this.componentTypes = componentTypes;
    }

    public List<RelationType> getRelationTypes() { return relationTypes; }
    public void setRelationTypes(List<RelationType> relationTypes) {
        this.relationTypes = relationTypes;
    }

    @Override
    public String toString() {
        return "{id='" + id + "', transformationProcessId='" + transformationProcessId
            + "', components=" + components.size()
            + ", relations=" + relations.size()
            + ", componentTypes=" + componentTypes.size()
            + ", relationTypes=" + relationTypes.size() + "}";
    }
}
