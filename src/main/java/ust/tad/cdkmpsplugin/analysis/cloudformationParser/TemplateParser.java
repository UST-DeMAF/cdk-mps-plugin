package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Parses a CloudFormation template file into a map from logical id to {@link CFResource}.
 * Properties are flattened to {@link CFProperty} entries whose value is the stringified JSON,
 * preserving intrinsic functions ({@code Ref}, {@code Fn::GetAtt}, ...) as-is.
 */
public class TemplateParser {

  public Map<String, CFResource> parseTemplate(Path templateFile) throws IOException {
    if (!Files.exists(templateFile)) {
      throw new NoSuchFileException("Template file not found: " + templateFile);
    }

    JsonNode root = new ObjectMapper().readTree(templateFile.toFile());
    JsonNode resources = root.get("Resources");
    Map<String, CFResource> resourceMap = new LinkedHashMap<>();

    if (resources == null || !resources.isObject()) {
      return resourceMap;
    }

    Iterator<Map.Entry<String, JsonNode>> iter = resources.fields();
    while (iter.hasNext()) {
      Map.Entry<String, JsonNode> entry = iter.next();
      String logicalId = entry.getKey();
      JsonNode resourceNode = entry.getValue();

      String type = resourceNode.has("Type") ? resourceNode.get("Type").asText() : "";
      Set<CFProperty> properties = extractProperties(resourceNode.get("Properties"));
      properties.addAll(extractExplicitDependencies(resourceNode.get("DependsOn")));

      resourceMap.put(logicalId, new CFResource(logicalId, type, properties));
    }
    resolveListReferences(resourceMap);
    return resourceMap;
  }

  /**
   * A property whose value is a list of references (Subnets, SecurityGroups, Roles, ...) would
   * otherwise show up as raw JSON. Turn those into a readable comma-separated list of the
   * referenced resources. A lone reference is left alone so it can still become a relation.
   */
  private void resolveListReferences(Map<String, CFResource> resourceMap) {
    Map<String, String> descriptiveById = new LinkedHashMap<>();
    for (CFResource resource : resourceMap.values()) {
      descriptiveById.put(resource.getLogicalId(), descriptiveValue(resource));
    }
    ObjectMapper mapper = new ObjectMapper();
    for (CFResource resource : resourceMap.values()) {
      Set<CFProperty> resolved = new LinkedHashSet<>();
      for (CFProperty property : resource.getProperties()) {
        String joined =
            property.isReference() ? null : joinListReferences(property.getValue(), descriptiveById, mapper);
        if (joined == null) {
          resolved.add(property);
        } else {
          CFProperty collapsed = new CFProperty(property.getKey(), joined, null);
          collapsed.setNestedTargets(property.getNestedTargets());
          resolved.add(collapsed);
        }
      }
      resource.setProperties(resolved);
    }
  }

  private String descriptiveValue(CFResource resource) {
    for (CFProperty property : resource.getProperties()) {
      if ("CidrBlock".equals(property.getKey()) && !property.isReference()) {
        return property.getValue();
      }
    }
    return resource.getLogicalId();
  }

  /** Joins a JSON array of references into a comma-separated list, or returns null to keep the value as-is. */
  private String joinListReferences(
      String value, Map<String, String> descriptiveById, ObjectMapper mapper) {
    if (value == null || !value.startsWith("[")) {
      return null;
    }
    JsonNode array;
    try {
      array = mapper.readTree(value);
    } catch (IOException e) {
      return null;
    }
    if (!array.isArray() || array.isEmpty()) {
      return null;
    }
    StringBuilder joined = new StringBuilder();
    for (JsonNode element : array) {
      String target = extractReferenceTarget(element);
      if (target == null) {
        return null; // leave anything that isn't a clean list of references untouched
      }
      if (joined.length() > 0) {
        joined.append(",");
      }
      joined.append(descriptiveById.getOrDefault(target, target));
    }
    return joined.toString();
  }

  private Set<CFProperty> extractProperties(JsonNode propertiesNode) {
    Set<CFProperty> result = new LinkedHashSet<>();
    if (propertiesNode == null || !propertiesNode.isObject()) {
      return result;
    }
    Iterator<Map.Entry<String, JsonNode>> iter = propertiesNode.fields();
    while (iter.hasNext()) {
      Map.Entry<String, JsonNode> entry = iter.next();
      JsonNode value = entry.getValue();
      CFProperty property =
          new CFProperty(entry.getKey(), stringifyValue(value), extractReferenceTarget(value));
      property.setNestedTargets(ReferenceExtractor.deepTargets(value));
      result.add(property);
    }
    return result;
  }

  /**
   * Maps the resource-level {@code DependsOn} attribute (string or array form) to reference
   * properties with key {@code "DependsOn"}.
   */
  private Set<CFProperty> extractExplicitDependencies(JsonNode dependsOnNode) {
    Set<CFProperty> result = new LinkedHashSet<>();
    if (dependsOnNode == null) {
      return result;
    }
    if (dependsOnNode.isTextual()) {
      result.add(new CFProperty("DependsOn", dependsOnNode.asText(), dependsOnNode.asText()));
    } else if (dependsOnNode.isArray()) {
      for (JsonNode dep : dependsOnNode) {
        if (dep.isTextual()) {
          result.add(new CFProperty("DependsOn", dep.asText(), dep.asText()));
        }
      }
    }
    return result;
  }

  /**
   * Returns the logical id referenced by a {@code Ref} or {@code Fn::GetAtt} intrinsic, or {@code
   * null} for plain values. Resolution against the model happens later, at MPS serialisation.
   */
  private String extractReferenceTarget(JsonNode node) {
    return ReferenceExtractor.directTarget(node);
  }

  /**
   * Textual scalars are unwrapped (no surrounding quotes); objects and arrays are kept as raw JSON
   * so nested intrinsic functions are preserved.
   */
  private String stringifyValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return "null";
    }
    if (node.isTextual()) {
      return node.asText().trim();
    }
    return node.toString().trim();
  }
}
