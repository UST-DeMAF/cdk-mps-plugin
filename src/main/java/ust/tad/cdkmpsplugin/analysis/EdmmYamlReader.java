package ust.tad.cdkmpsplugin.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ust.tad.cdkmpsplugin.models.tadm.Component;
import ust.tad.cdkmpsplugin.models.tadm.ComponentType;
import ust.tad.cdkmpsplugin.models.tadm.Confidence;
import ust.tad.cdkmpsplugin.models.tadm.InvalidRelationException;
import ust.tad.cdkmpsplugin.models.tadm.Property;
import ust.tad.cdkmpsplugin.models.tadm.PropertyType;
import ust.tad.cdkmpsplugin.models.tadm.Relation;
import ust.tad.cdkmpsplugin.models.tadm.RelationType;
import ust.tad.cdkmpsplugin.models.tadm.TechnologyAgnosticDeploymentModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads a result.yaml produced by the MPS AWSCDK generator and converts it
 * into a {@link TechnologyAgnosticDeploymentModel}.
 *
 * The YAML format uses string names for type references (e.g. extends, source,
 * target). This reader resolves those strings to proper Java object references
 * after the initial parse.
 */
@org.springframework.stereotype.Component
public class EdmmYamlReader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public TechnologyAgnosticDeploymentModel read(Path resultYamlPath) throws IOException {
        EdmmYamlDto dto = yamlMapper.readValue(resultYamlPath.toFile(), EdmmYamlDto.class);
        return convert(dto);
    }

    private TechnologyAgnosticDeploymentModel convert(EdmmYamlDto dto) {
        TechnologyAgnosticDeploymentModel tadm = new TechnologyAgnosticDeploymentModel();

        List<ComponentType> componentTypes = buildComponentTypes(dto.componentTypes);
        tadm.setComponentTypes(componentTypes);
        Map<String, ComponentType> ctByName = componentTypes.stream()
            .collect(Collectors.toMap(ComponentType::getName, Function.identity()));

        for (EdmmYamlDto.TypeDto typeDto : safe(dto.componentTypes)) {
            if (typeDto.extends_ != null && !typeDto.extends_.isBlank()) {
                ComponentType ct = ctByName.get(typeDto.name);
                ComponentType parent = ctByName.get(typeDto.extends_);
                if (ct != null && parent != null) ct.setParentType(parent);
            }
        }

        List<RelationType> relationTypes = buildRelationTypes(dto.relationTypes);
        tadm.setRelationTypes(relationTypes);
        Map<String, RelationType> rtByName = relationTypes.stream()
            .collect(Collectors.toMap(RelationType::getName, Function.identity()));

        for (EdmmYamlDto.TypeDto typeDto : safe(dto.relationTypes)) {
            if (typeDto.extends_ != null && !typeDto.extends_.isBlank()) {
                RelationType rt = rtByName.get(typeDto.name);
                RelationType parent = rtByName.get(typeDto.extends_);
                if (rt != null && parent != null) rt.setParentType(parent);
            }
        }

        List<Component> components = buildComponents(dto.components, ctByName);
        tadm.setComponents(components);
        Map<String, Component> compByName = components.stream()
            .collect(Collectors.toMap(Component::getName, Function.identity()));

        List<Relation> relations = buildRelations(dto.relations, rtByName, compByName);
        tadm.setRelations(relations);

        return tadm;
    }


    private List<ComponentType> buildComponentTypes(List<EdmmYamlDto.TypeDto> dtos) {
        List<ComponentType> result = new ArrayList<>();
        for (EdmmYamlDto.TypeDto dto : safe(dtos)) {
            ComponentType ct = new ComponentType();
            ct.setName(dto.name);
            ct.setProperties(convertProperties(dto.properties));
            result.add(ct);
        }
        return result;
    }

    private List<RelationType> buildRelationTypes(List<EdmmYamlDto.TypeDto> dtos) {
        List<RelationType> result = new ArrayList<>();
        for (EdmmYamlDto.TypeDto dto : safe(dtos)) {
            RelationType rt = new RelationType();
            rt.setName(dto.name);
            rt.setProperties(convertProperties(dto.properties));
            result.add(rt);
        }
        return result;
    }


    private List<Component> buildComponents(List<EdmmYamlDto.ComponentDto> dtos,
                                             Map<String, ComponentType> ctByName) {
        List<Component> result = new ArrayList<>();
        for (EdmmYamlDto.ComponentDto dto : safe(dtos)) {
            Component c = new Component();
            c.setName(dto.name);
            c.setConfidence(Confidence.CONFIRMED);
            c.setProperties(convertProperties(dto.properties));
            ComponentType ct = ctByName.get(dto.type);
            if (ct != null) c.setType(ct);
            result.add(c);
        }
        return result;
    }


    private List<Relation> buildRelations(List<EdmmYamlDto.RelationDto> dtos,
                                           Map<String, RelationType> rtByName,
                                           Map<String, Component> compByName) {
        List<Relation> result = new ArrayList<>();
        for (EdmmYamlDto.RelationDto dto : safe(dtos)) {
            Component source = compByName.get(dto.source);
            Component target = compByName.get(dto.target);
            RelationType rt = rtByName.get(dto.type);
            if (source == null || target == null || source.equals(target)) continue;
            Relation r = new Relation();
            r.setName(dto.name);
            r.setType(rt);
            r.setConfidence(Confidence.CONFIRMED);
            try {
                r.setSource(source);
                r.setTarget(target);
            } catch (InvalidRelationException e) {
                // source == target already guarded above
            }
            r.setProperties(convertProperties(dto.properties));
            result.add(r);
        }
        return result;
    }


    private List<Property> convertProperties(List<EdmmYamlDto.PropertyDto> dtos) {
        List<Property> result = new ArrayList<>();
        for (EdmmYamlDto.PropertyDto dto : safe(dtos)) {
            Property p = new Property();
            p.setKey(dto.key);
            p.setRequired(dto.required);
            p.setConfidence(Confidence.CONFIRMED);
            PropertyType pt = parsePropertyType(dto.type);
            p.setType(pt);
            p.setValue(dto.value);
            result.add(p);
        }
        return result;
    }

    private PropertyType parsePropertyType(String type) {
        if (type == null) return PropertyType.STRING;
        try {
            return PropertyType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PropertyType.STRING;
        }
    }

    private <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }

    // -------------------------------------------------------------------------
    // DTOs matching the result.yaml structure
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EdmmYamlDto {
        public List<PropertyDto> properties;
        public List<ComponentDto> components;
        public List<RelationDto> relations;

        @JsonProperty("component types")
        public List<TypeDto> componentTypes;

        @JsonProperty("relation types")
        public List<TypeDto> relationTypes;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class ComponentDto {
            public String name;
            public String type;
            public List<PropertyDto> properties;
            public List<Object> operations;
            public List<Object> artifacts;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class RelationDto {
            public String name;
            public String type;
            public String source;
            public String target;
            public List<PropertyDto> properties;
            public List<Object> operations;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class TypeDto {
            public String name;
            @JsonProperty("extends")
            public String extends_;
            public List<PropertyDto> properties;
            public List<Object> operations;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class PropertyDto {
            public String key;
            public String type;
            public String value;
            public boolean required;
        }
    }
}
