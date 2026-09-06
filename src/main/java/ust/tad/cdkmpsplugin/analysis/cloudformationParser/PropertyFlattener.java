package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Flattens a structured property value into dotted leaf keys, so that nested configuration is
 * readable in the model instead of arriving as a block of raw JSON. A container definition becomes
 * {@code ContainerDefinitions.0.Image} rather than a quoted array. This follows the convention used
 * by the reference models in the DeMAF type definitions.
 *
 * <p>Intrinsic functions are skipped. They name another resource rather than carrying a value, and
 * are handled by {@link ReferenceExtractor}.
 */
final class PropertyFlattener {

  private static final int MAX_DEPTH = 4;

  private static final Set<String> INTRINSICS =
      Set.of(
          "Ref",
          "Fn::GetAtt",
          "Fn::Sub",
          "Fn::Join",
          "Fn::ImportValue",
          "Fn::Select",
          "Fn::Split",
          "Fn::If");

  private PropertyFlattener() {}

  static Map<String, String> flatten(String key, JsonNode value) {
    Map<String, String> leaves = new LinkedHashMap<>();
    if (value != null && (value.isObject() || value.isArray())) {
      collect(key, value, 0, leaves);
    }
    return leaves;
  }

  private static void collect(String prefix, JsonNode node, int depth, Map<String, String> leaves) {
    if (depth > MAX_DEPTH) {
      return;
    }
    if (node.isObject()) {
      if (node.size() == 1 && INTRINSICS.contains(node.fieldNames().next())) {
        return;
      }
      node.fields()
          .forEachRemaining(e -> collect(prefix + "." + e.getKey(), e.getValue(), depth + 1, leaves));
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        collect(prefix + "." + i, node.get(i), depth + 1, leaves);
      }
    } else if (!node.isNull()) {
      leaves.put(prefix, node.isTextual() ? node.asText().trim() : node.toString());
    }
  }
}
