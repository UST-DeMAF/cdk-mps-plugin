package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads CloudFormation intrinsic references out of a property value. {@link #directTarget} returns
 * the target of a lone {@code Ref}/{@code Fn::GetAtt}; {@link #deepTargets} finds every reference
 * nested at any depth, including inside arrays and {@code Fn::Join} argument lists.
 */
public final class ReferenceExtractor {

  private ReferenceExtractor() {}

  public static String directTarget(JsonNode node) {
    if (node == null || !node.isObject() || node.size() != 1) {
      return null;
    }
    return intrinsicTarget(node, false);
  }

  public static Set<String> deepTargets(JsonNode node) {
    Set<String> targets = new LinkedHashSet<>();
    collect(node, targets);
    return targets;
  }

  private static void collect(JsonNode node, Set<String> targets) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      if (node.size() == 1) {
        String target = intrinsicTarget(node, true);
        if (target != null) {
          targets.add(target);
          return;
        }
      }
      node.forEach(child -> collect(child, targets));
    } else if (node.isArray()) {
      node.forEach(child -> collect(child, targets));
    }
  }

  /** The logical id named by a {@code Ref} or {@code Fn::GetAtt}, ignoring pseudo parameters. */
  private static String intrinsicTarget(JsonNode node, boolean skipPseudo) {
    JsonNode ref = node.get("Ref");
    if (ref != null && ref.isTextual()) {
      return skipPseudo && pseudo(ref.asText()) ? null : ref.asText();
    }
    JsonNode getAtt = node.get("Fn::GetAtt");
    if (getAtt != null) {
      String target = null;
      if (getAtt.isArray() && getAtt.size() >= 1 && getAtt.get(0).isTextual()) {
        target = getAtt.get(0).asText();
      } else if (getAtt.isTextual() && !getAtt.asText().isBlank()) {
        target = getAtt.asText().split("\\.", 2)[0];
      }
      return target != null && skipPseudo && pseudo(target) ? null : target;
    }
    return null;
  }

  private static boolean pseudo(String logicalId) {
    return logicalId.startsWith("AWS::");
  }
}
