package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Derives {@code ConnectsTo} references from connector resources. A connector is a resource that is
 * not a deployment component itself but wires two components together, such as an
 * {@code AWS::Lambda::Permission} joining an API to a function, or a listener joining a load
 * balancer to a service through a target group. Edges are contracted through such resources so that
 * only the components at either end remain.
 */
public class ConnectivityGraphResolver {

  private static final String ACCESS_LEVEL = "invoke";

  /** Property names that hold the resource an action delivers to, rather than a role to assume. */
  private static final String[] DESTINATION_KEYS = {"destinationarn", "targetarn", "domainname"};

  private final ObjectMapper mapper = new ObjectMapper();

  /** Wiring resources an edge may be routed through. Anything unlisted is treated as a component. */
  private static final Set<String> ROUTABLE =
      Set.of(
          "AWS::Lambda::Permission",
          "AWS::Lambda::EventSourceMapping",
          "AWS::ApiGateway::Method",
          "AWS::ApiGatewayV2::Route",
          "AWS::ApiGatewayV2::Integration",
          "AWS::ElasticLoadBalancingV2::Listener",
          "AWS::ElasticLoadBalancingV2::ListenerRule",
          "AWS::ElasticLoadBalancingV2::TargetGroup",
          "AWS::SNS::Subscription",
          "AWS::Events::Rule",
          "AWS::ApiGatewayV2::Authorizer",
          "AWS::CloudWatch::Alarm",
          "Custom::S3BucketNotifications");

  /**
   * Never routed through. Either shared infrastructure that would connect everything to everything,
   * or identity resources already covered by {@link IamConnectivityResolver}.
   */
  private static final Set<String> BLOCKED =
      Set.of(
          "AWS::EC2::VPC",
          "AWS::EC2::Subnet",
          "AWS::EC2::RouteTable",
          "AWS::EC2::SecurityGroup",
          "AWS::Logs::LogGroup",
          "AWS::KMS::Key",
          "AWS::ApiGateway::Stage",
          "AWS::ApiGateway::Deployment",
          "AWS::ApiGateway::Resource",
          "AWS::CDK::Metadata");

  /** Property holding the calling end, then the property holding the called end. */
  private static final Map<String, String[]> DIRECTION =
      Map.of(
          "AWS::Lambda::Permission", new String[] {"SourceArn", "FunctionName"},
          "AWS::Lambda::EventSourceMapping", new String[] {"EventSourceArn", "FunctionName"},
          "AWS::ApiGateway::Method", new String[] {"RestApiId", "Integration"},
          "AWS::ElasticLoadBalancingV2::Listener",
              new String[] {"LoadBalancerArn", "DefaultActions"},
          "AWS::ElasticLoadBalancingV2::ListenerRule", new String[] {"ListenerArn", "Actions"},
          "AWS::SNS::Subscription", new String[] {"TopicArn", "Endpoint"},
          "AWS::ApiGatewayV2::Authorizer", new String[] {"ApiId", "JwtConfiguration"},
          "AWS::CloudWatch::Alarm", new String[] {"Dimensions", "AlarmActions"},
          "Custom::S3BucketNotifications",
              new String[] {"BucketName", "NotificationConfiguration"});

  private static final String[] SOURCE_MARKERS = {"source", "from", "origin"};
  private static final String[] TARGET_MARKERS =
      {"target", "destination", "functionname", "endpoint", "integration", "actions"};

  public void resolve(CDKDeploymentModel model) {
    List<CFResource> resources = new ArrayList<>();
    Map<String, CFResource> byId = new LinkedHashMap<>();
    for (CDKConstruct construct : model.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        resources.add(resource);
        byId.putIfAbsent(resource.getLogicalId(), resource);
      }
    }
    Map<String, Set<String>> neighbours = buildNeighbours(resources, byId);

    linkDestinations(resources, byId);

    for (CFResource connector : resources) {
      if (!ROUTABLE.contains(connector.getType())) {
        continue;
      }
      Ends ends = endsOf(connector);
      for (String source : expand(ends.sources, neighbours, byId, connector.getLogicalId())) {
        for (String target : expand(ends.targets, neighbours, byId, connector.getLogicalId())) {
          link(byId.get(source), target);
        }
      }
    }
  }

  /**
   * Some actions name the resource they deliver to instead of a role to assume, so no grant exists
   * to follow. An IoT rule writing to a Kafka cluster names it in a destination field. The reference
   * sits deep inside the action, so the raw value is walked and only destination fields are read.
   */
  private void linkDestinations(List<CFResource> resources, Map<String, CFResource> byId) {
    Map<CFResource, Set<String>> found = new LinkedHashMap<>();
    for (CFResource source : resources) {
      if (BLOCKED.contains(source.getType())) {
        continue;
      }
      for (CFProperty property : List.copyOf(source.getProperties())) {
        String value = property.getValue();
        if (value == null || !(value.startsWith("{") || value.startsWith("["))) {
          continue;
        }
        JsonNode parsed;
        try {
          parsed = mapper.readTree(value);
        } catch (IOException ignored) {
          continue;
        }
        for (String target : destinationTargets(parsed)) {
          CFResource candidate = byId.get(target);
          if (candidate == null
              || ROUTABLE.contains(candidate.getType())
              || BLOCKED.contains(candidate.getType())) {
            continue;
          }
          found.computeIfAbsent(source, r -> new LinkedHashSet<>()).add(target);
        }
      }
    }
    found.forEach((source, targets) -> targets.forEach(target -> link(source, target)));
  }

  /** Logical ids referenced by a destination field anywhere inside a value. */
  private Set<String> destinationTargets(JsonNode node) {
    Set<String> targets = new LinkedHashSet<>();
    collectDestinations(node, targets);
    return targets;
  }

  private void collectDestinations(JsonNode node, Set<String> targets) {
    if (node.isArray()) {
      node.forEach(child -> collectDestinations(child, targets));
      return;
    }
    if (!node.isObject()) {
      return;
    }
    node.fields()
        .forEachRemaining(
            entry -> {
              if (matches(entry.getKey().toLowerCase(), DESTINATION_KEYS)) {
                targets.addAll(ReferenceExtractor.deepTargets(entry.getValue()));
              }
              collectDestinations(entry.getValue(), targets);
            });
  }

  private Map<String, Set<String>> buildNeighbours(
      List<CFResource> resources, Map<String, CFResource> byId) {
    Map<String, Set<String>> neighbours = new LinkedHashMap<>();
    for (CFResource resource : resources) {
      for (CFProperty property : resource.getProperties()) {
        for (String target : property.getNestedTargets()) {
          if (!byId.containsKey(target) || target.equals(resource.getLogicalId())) {
            continue;
          }
          neighbours.computeIfAbsent(resource.getLogicalId(), k -> new LinkedHashSet<>()).add(target);
          neighbours.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(resource.getLogicalId());
        }
      }
    }
    return neighbours;
  }

  /** Splits a connector's references into the calling end and the called end. */
  private Ends endsOf(CFResource connector) {
    Ends ends = new Ends();
    String[] override = DIRECTION.get(connector.getType());
    for (CFProperty property : connector.getProperties()) {
      Set<String> targets = property.getNestedTargets();
      if (targets.isEmpty()) {
        continue;
      }
      String key = property.getKey();
      if (override != null) {
        if (override[0].equals(key)) {
          ends.sources.addAll(targets);
        } else if (override[1].equals(key)) {
          ends.targets.addAll(targets);
        }
        continue;
      }
      String lower = key.toLowerCase();
      if (matches(lower, TARGET_MARKERS)) {
        ends.targets.addAll(targets);
      } else if (matches(lower, SOURCE_MARKERS)) {
        ends.sources.addAll(targets);
      }
    }
    return ends;
  }

  /**
   * Replaces any endpoint that is itself wiring with the components attached to it, so an edge that
   * lands on a target group continues to the service registered behind it.
   */
  private Set<String> expand(
      Set<String> endpoints, Map<String, Set<String>> neighbours, Map<String, CFResource> byId,
      String connectorId) {
    Set<String> resolved = new LinkedHashSet<>();
    for (String endpoint : endpoints) {
      CFResource resource = byId.get(endpoint);
      if (resource == null || BLOCKED.contains(resource.getType())) {
        continue;
      }
      if (!ROUTABLE.contains(resource.getType())) {
        resolved.add(endpoint);
        continue;
      }
      for (String neighbour : neighbours.getOrDefault(endpoint, Set.of())) {
        CFResource candidate = byId.get(neighbour);
        if (candidate == null
            || neighbour.equals(connectorId)
            || ROUTABLE.contains(candidate.getType())
            || BLOCKED.contains(candidate.getType())) {
          continue;
        }
        resolved.add(neighbour);
      }
    }
    return resolved;
  }

  /** Adds the edge unless this pair already has one, so IAM-derived levels are not overwritten. */
  private void link(CFResource source, String target) {
    if (source == null || target.equals(source.getLogicalId())) {
      return;
    }
    for (CFProperty existing : source.getProperties()) {
      if (IamConnectivityResolver.CONNECTS_TO_KEY.equals(existing.getKey())
          && target.equals(existing.getReferenceTarget())) {
        return;
      }
    }
    source.addProperty(
        new CFProperty(IamConnectivityResolver.CONNECTS_TO_KEY, ACCESS_LEVEL, target));
  }

  private static boolean matches(String key, String[] markers) {
    for (String marker : markers) {
      if (key.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private static final class Ends {
    private final Set<String> sources = new LinkedHashSet<>();
    private final Set<String> targets = new LinkedHashSet<>();
  }
}
