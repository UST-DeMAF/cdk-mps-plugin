package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Resolves references that cross a stack boundary. A consuming stack names an export through
 * {@code Fn::ImportValue}, which points at nothing on its own. Each import is replaced by the value
 * the producing stack exported, so the reference becomes an ordinary one and every later rule reads
 * it without knowing a stack boundary was crossed.
 */
public class CrossStackResolver {

  private static final String IMPORT = "Fn::ImportValue";

  private final ObjectMapper mapper = new ObjectMapper();

  public void resolve(CDKDeploymentModel model, Map<String, JsonNode> exports) {
    if (exports.isEmpty()) {
      return;
    }
    for (CDKConstruct construct : model.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        for (CFProperty property : resource.getProperties()) {
          rewrite(property, exports);
        }
      }
    }
  }

  private void rewrite(CFProperty property, Map<String, JsonNode> exports) {
    String value = property.getValue();
    if (value == null || !value.contains(IMPORT)) {
      return;
    }
    JsonNode parsed;
    try {
      parsed = mapper.readTree(value);
    } catch (IOException ignored) {
      return;
    }
    JsonNode replaced = substitute(parsed, exports);
    if (replaced == null) {
      return;
    }
    property.setValue(replaced.isTextual() ? replaced.asText() : replaced.toString());
    property.setNestedTargets(ReferenceExtractor.deepTargets(replaced));
    if (property.getReferenceTarget() == null) {
      property.setReferenceTarget(ReferenceExtractor.directTarget(replaced));
    }
  }

  /** Returns the value with every known import swapped for what it imports, or null if unchanged. */
  private JsonNode substitute(JsonNode node, Map<String, JsonNode> exports) {
    if (node.isObject()) {
      if (node.size() == 1) {
        JsonNode name = node.get(IMPORT);
        if (name != null && name.isTextual()) {
          return exports.get(name.asText());
        }
      }
      ObjectNode object = (ObjectNode) node;
      boolean changed = false;
      List<String> fields = new ArrayList<>();
      object.fieldNames().forEachRemaining(fields::add);
      for (String field : fields) {
        JsonNode child = substitute(object.get(field), exports);
        if (child != null) {
          object.set(field, child);
          changed = true;
        }
      }
      return changed ? object : null;
    }
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      boolean changed = false;
      for (int i = 0; i < array.size(); i++) {
        JsonNode child = substitute(array.get(i), exports);
        if (child != null) {
          array.set(i, child);
          changed = true;
        }
      }
      return changed ? array : null;
    }
    return null;
  }
}
