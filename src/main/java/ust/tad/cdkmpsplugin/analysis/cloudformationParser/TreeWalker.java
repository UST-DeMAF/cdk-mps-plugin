package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Walks the CDK {@code tree.json} construct hierarchy and produces a set of {@link CDKConstruct}
 * objects for a single stack. Each direct child of a stack node is treated as a candidate L2/L3
 * construct (after applying exclusion rules); the CloudFormation resources owned by that construct
 * are discovered by recursively descending and reading the {@code aws:cdk:cloudformation:logicalId}
 * attribute attached to each L1 (Cfn*) leaf, then looking it up in the template map.
 */
public class TreeWalker {

  /** Construct ids that are pure CDK bookkeeping and must never appear in the EDMM model. */
  private static final Set<String> EXCLUDED_IDS =
      Set.of("CDKMetadata", "BootstrapVersion", "CheckBootstrapVersion", "Tree");

  /** Fully qualified construct types that are pure CloudFormation control plane (no semantics). */
  private static final Set<String> EXCLUDED_FQNS =
      Set.of(
          "aws-cdk-lib.CfnParameter",
          "aws-cdk-lib.CfnOutput",
          "aws-cdk-lib.CfnRule",
          "aws-cdk-lib.CfnCondition",
          "aws-cdk-lib.CfnMapping");

  /** Substring matches in the fqn that mark a construct as CDK infrastructure plumbing. */
  private static final Set<String> EXCLUDED_FQN_CONTAINS =
      Set.of("CustomResourceProvider", "AssetStaging");

  private static final String ATTR_LOGICAL_ID = "aws:cdk:cloudformation:logicalId";

  public Set<CDKConstruct> walkTree(
      JsonNode treeRoot, String stackName, Map<String, CFResource> templateMap) {
    Set<CDKConstruct> constructs = new HashSet<>();
    if (treeRoot == null) {
      return constructs;
    }
    JsonNode children = treeRoot.get("children");
    if (children == null || !children.isObject()) {
      return constructs;
    }
    JsonNode stackNode = children.get(stackName);
    if (stackNode == null) {
      return constructs;
    }
    JsonNode stackChildren = stackNode.get("children");
    if (stackChildren == null || !stackChildren.isObject()) {
      return constructs;
    }

    Iterator<Map.Entry<String, JsonNode>> iter = stackChildren.fields();
    while (iter.hasNext()) {
      Map.Entry<String, JsonNode> entry = iter.next();
      String id = entry.getKey();
      JsonNode node = entry.getValue();
      if (shouldExclude(id, node)) {
        continue;
      }

      String fqn = readFqn(node);
      String cdkPath = node.has("path") ? node.get("path").asText() : "";

      CDKConstruct construct =
          new CDKConstruct().id(id).fqn(fqn).stackName(stackName).cdkPath(cdkPath);

      collectCFResources(node, templateMap, construct);

      // Skip constructs that ended up with zero CF resources (e.g. abstract bookkeeping nodes).
      if (!construct.getCfResources().isEmpty()) {
        constructs.add(construct);
      }
    }
    return constructs;
  }

  /** Returns {@code true} if this node should not become a CDKConstruct in the output. */
  private boolean shouldExclude(String id, JsonNode node) {
    if (EXCLUDED_IDS.contains(id)) {
      return true;
    }
    String fqn = readFqn(node);
    if (fqn.isEmpty()) {
      // constructInfo.fqn was introduced in CDK v1.100; nodes without it are internal L1 wrappers
      // (e.g. aws-cdk-lib.Resource, aws-cdk-lib.CfnResource) that have no L2/L3 identity.
      return true;
    }
    if (EXCLUDED_FQNS.contains(fqn)) {
      return true;
    }
    for (String contains : EXCLUDED_FQN_CONTAINS) {
      if (fqn.contains(contains)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Recursively collects every CFResource referenced by an {@code aws:cdk:cloudformation:logicalId}
   * attribute. Lookups missing from the template map are skipped (cross-stack references or
   * resources removed during template post-processing).
   */
  private void collectCFResources(
      JsonNode node, Map<String, CFResource> templateMap, CDKConstruct construct) {
    JsonNode attributes = node.get("attributes");
    if (attributes != null && attributes.has(ATTR_LOGICAL_ID)) {
      String logicalId = attributes.get(ATTR_LOGICAL_ID).asText();
      CFResource resource = templateMap.get(logicalId);
      if (resource != null) {
        construct.addCFResource(resource);
      }
    }
    JsonNode children = node.get("children");
    if (children == null || !children.isObject()) {
      return;
    }
    Iterator<JsonNode> iter = children.elements();
    while (iter.hasNext()) {
      collectCFResources(iter.next(), templateMap, construct);
    }
  }

  private String readFqn(JsonNode node) {
    JsonNode info = node.get("constructInfo");
    if (info == null) {
      return "";
    }
    JsonNode fqn = info.get("fqn");
    return fqn == null ? "" : fqn.asText();
  }
}
