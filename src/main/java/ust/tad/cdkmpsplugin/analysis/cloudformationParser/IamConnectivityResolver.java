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
 * Infers {@code ConnectsTo} references from IAM grants and environment-variable references, added to
 * the accessor as a {@link CFProperty} with key {@code ConnectsTo}, value = access level
 * ({@code read}/{@code write}/{@code readwrite}/{@code reference}) and referenceTarget = the used resource.
 */
public class IamConnectivityResolver {

  static final String CONNECTS_TO_KEY = "ConnectsTo";

  /** Services granted on a wildcard resource that carry no architectural meaning. */
  private static final Set<String> BOILERPLATE_SERVICES = Set.of("logs", "xray", "sts");

  private static final String MANAGED_SERVICE_SUFFIX = "::ManagedService";

  private static final String READ = "read";
  private static final String WRITE = "write";
  private static final String READ_WRITE = "readwrite";
  private static final String REFERENCE = "reference";

  private final ObjectMapper mapper = new ObjectMapper();

  public void resolve(CDKDeploymentModel model) {
    List<CFResource> resources = new ArrayList<>();
    Map<String, CFResource> byId = new LinkedHashMap<>();
    for (CDKConstruct construct : model.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        resources.add(resource);
        byId.putIfAbsent(resource.getLogicalId(), resource);
      }
    }

    Map<String, Map<String, Access>> accessByRole = collectRoleGrants(resources, byId);
    Map<String, CFResource> managedServices = new LinkedHashMap<>();

    for (CFResource accessor : resources) {
      Map<String, Access> targets = new LinkedHashMap<>();

      for (String role : accessorRoles(accessor, byId)) {
        Map<String, Access> grants = accessByRole.get(role);
        if (grants != null) {
          grants.forEach((target, access) -> targets.computeIfAbsent(target, t -> new Access()).merge(access));
        }
      }

      for (String target : environmentReferences(accessor, byId)) {
        targets.computeIfAbsent(target, t -> new Access()).hasReference = true;
      }

      for (Map.Entry<String, Access> entry : targets.entrySet()) {
        String target = entry.getKey();
        if (target.endsWith(MANAGED_SERVICE_SUFFIX)) {
          target = managedService(target, managedServices).getLogicalId();
        } else if (target.equals(accessor.getLogicalId()) || !byId.containsKey(target)) {
          continue;
        }
        accessor.addProperty(new CFProperty(CONNECTS_TO_KEY, entry.getValue().level(), target));
      }
    }

    if (!managedServices.isEmpty()) {
      model.addConstruct(
          new CDKConstruct(
              "ManagedServices",
              "",
              model.getStacks().stream().findFirst().orElse(""),
              "ManagedServices",
              new LinkedHashSet<>(managedServices.values())));
    }
  }

  /**
   * A service reached through a wildcard grant has no CloudFormation resource of its own, so one is
   * synthesised to stand for it. The type carries the service name so the mapping rules can give it
   * a component type in the usual way.
   */
  private CFResource managedService(String target, Map<String, CFResource> managedServices) {
    String service = target.substring(0, target.length() - MANAGED_SERVICE_SUFFIX.length());
    return managedServices.computeIfAbsent(
        service,
        s ->
            new CFResource(
                s,
                "AWS::"
                    + Character.toUpperCase(s.charAt(0))
                    + s.substring(1)
                    + MANAGED_SERVICE_SUFFIX,
                new LinkedHashSet<>()));
  }

  private Map<String, Map<String, Access>> collectRoleGrants(
      List<CFResource> resources, Map<String, CFResource> byId) {
    Map<String, Map<String, Access>> accessByRole = new LinkedHashMap<>();
    for (CFResource resource : resources) {
      String type = resource.getType();
      if ("AWS::IAM::Policy".equals(type) || "AWS::IAM::ManagedPolicy".equals(type)) {
        List<String> roles = referencedIds(resource, "Roles", byId);
        JsonNode document = jsonProperty(resource, "PolicyDocument");
        for (String role : roles) {
          addStatements(accessByRole.computeIfAbsent(role, r -> new LinkedHashMap<>()), document, byId);
        }
      } else if ("AWS::IAM::Role".equals(type)) {
        JsonNode inline = jsonProperty(resource, "Policies");
        if (inline != null && inline.isArray()) {
          Map<String, Access> grants =
              accessByRole.computeIfAbsent(resource.getLogicalId(), r -> new LinkedHashMap<>());
          for (JsonNode policy : inline) {
            addStatements(grants, policy.get("PolicyDocument"), byId);
          }
        }
      }
    }
    return accessByRole;
  }

  private void addStatements(
      Map<String, Access> grants, JsonNode document, Map<String, CFResource> byId) {
    if (document == null) {
      return;
    }
    JsonNode statements = document.get("Statement");
    if (statements == null) {
      return;
    }
    for (JsonNode statement : statements.isArray() ? statements : List.of(statements)) {
      JsonNode effect = statement.get("Effect");
      if (effect != null && !"Allow".equalsIgnoreCase(effect.asText())) {
        continue;
      }
      Access access = accessOf(statement.get("Action"));
      if (isWildcard(statement.get("Resource"))) {
        for (String service : servicesOf(statement.get("Action"))) {
          grants.computeIfAbsent(service + MANAGED_SERVICE_SUFFIX, t -> new Access()).merge(access);
        }
        continue;
      }
      for (String target : statementTargets(statement.get("Resource"), byId)) {
        grants.computeIfAbsent(target, t -> new Access()).merge(access);
      }
    }
  }

  private List<String> statementTargets(JsonNode resource, Map<String, CFResource> byId) {
    List<String> targets = new ArrayList<>();
    if (resource == null) {
      return targets;
    }
    for (JsonNode element : resource.isArray() ? resource : List.of(resource)) {
      String target = referenceTarget(element);
      if (target != null && byId.containsKey(target) && !isIam(byId.get(target))) {
        targets.add(target);
      }
    }
    return targets;
  }

  private static boolean isWildcard(JsonNode resource) {
    if (resource == null) {
      return false;
    }
    for (JsonNode element : resource.isArray() ? resource : List.of(resource)) {
      if (element.isTextual() && "*".equals(element.asText())) {
        return true;
      }
    }
    return false;
  }

  /** Service prefixes named by a statement's actions, such as {@code rekognition}. */
  private Set<String> servicesOf(JsonNode action) {
    Set<String> services = new LinkedHashSet<>();
    if (action == null) {
      return services;
    }
    for (JsonNode entry : action.isArray() ? action : List.of(action)) {
      String text = entry.asText();
      int colon = text.indexOf(':');
      if (colon > 0) {
        String service = text.substring(0, colon).toLowerCase();
        if (!BOILERPLATE_SERVICES.contains(service)) {
          services.add(service);
        }
      }
    }
    return services;
  }

  private Access accessOf(JsonNode action) {
    Access access = new Access();
    if (action == null) {
      return access;
    }
    for (JsonNode entry : action.isArray() ? action : List.of(action)) {
      String verb = actionVerb(entry.asText());
      if (verb.isEmpty() || verb.equals("*")) {
        access.hasRead = true;
        access.hasWrite = true;
      } else if (isWriteVerb(verb)) {
        access.hasWrite = true;
      } else {
        access.hasRead = true;
      }
    }
    return access;
  }

  private List<String> environmentReferences(CFResource accessor, Map<String, CFResource> byId) {
    List<String> targets = new ArrayList<>();
    String type = accessor.getType();
    if ("AWS::Lambda::Function".equals(type)) {
      JsonNode environment = jsonProperty(accessor, "Environment");
      if (environment != null) {
        collectReferences(environment.get("Variables"), byId, targets);
      }
    } else if ("AWS::ECS::TaskDefinition".equals(type)) {
      JsonNode containers = jsonProperty(accessor, "ContainerDefinitions");
      if (containers != null && containers.isArray()) {
        for (JsonNode container : containers) {
          JsonNode env = container.get("Environment");
          if (env != null && env.isArray()) {
            for (JsonNode variable : env) {
              addReference(variable.get("Value"), byId, targets);
            }
          }
        }
      }
    }
    return targets;
  }

  private List<String> accessorRoles(CFResource accessor, Map<String, CFResource> byId) {
    Set<String> roles = new LinkedHashSet<>();
    roles.addAll(referencedIds(accessor, "Role", byId));
    roles.addAll(referencedIds(accessor, "TaskRoleArn", byId));
    roles.addAll(referencedIds(accessor, "ExecutionRoleArn", byId));
    for (String profileId : referencedIds(accessor, "IamInstanceProfile", byId)) {
      CFResource profile = byId.get(profileId);
      if (profile != null && "AWS::IAM::InstanceProfile".equals(profile.getType())) {
        roles.addAll(referencedIds(profile, "Roles", byId));
      }
    }
    roles.removeIf(id -> byId.get(id) == null || !isIam(byId.get(id)));
    return new ArrayList<>(roles);
  }

  private void collectReferences(JsonNode node, Map<String, CFResource> byId, List<String> targets) {
    if (node == null || !node.isObject()) {
      return;
    }
    node.forEach(value -> addReference(value, byId, targets));
  }

  private void addReference(JsonNode node, Map<String, CFResource> byId, List<String> targets) {
    String target = referenceTarget(node);
    if (target != null && byId.containsKey(target) && !isIam(byId.get(target))) {
      targets.add(target);
    }
  }

  private List<String> referencedIds(CFResource resource, String key, Map<String, CFResource> byId) {
    List<String> ids = new ArrayList<>();
    for (CFProperty property : resource.getProperties()) {
      if (!key.equals(property.getKey())) {
        continue;
      }
      if (property.isReference()) {
        ids.add(property.getReferenceTarget());
        continue;
      }
      String value = property.getValue();
      if (value == null || value.isBlank()) {
        continue;
      }
      if (value.startsWith("[")) {
        try {
          for (JsonNode element : mapper.readTree(value)) {
            String target = referenceTarget(element);
            if (target != null) {
              ids.add(target);
            }
          }
        } catch (IOException ignored) {
        }
      } else {
        for (String part : value.split(",")) {
          String candidate = part.trim();
          if (byId.containsKey(candidate)) {
            ids.add(candidate);
          }
        }
      }
    }
    return ids;
  }

  private JsonNode jsonProperty(CFResource resource, String key) {
    for (CFProperty property : resource.getProperties()) {
      if (key.equals(property.getKey()) && property.getValue() != null) {
        try {
          return mapper.readTree(property.getValue());
        } catch (IOException e) {
          return null;
        }
      }
    }
    return null;
  }

  private static boolean isIam(CFResource resource) {
    return resource.getType() != null && resource.getType().startsWith("AWS::IAM::");
  }

  private static String referenceTarget(JsonNode node) {
    if (node == null || !node.isObject() || node.size() != 1) {
      return null;
    }
    JsonNode ref = node.get("Ref");
    if (ref != null && ref.isTextual()) {
      return ref.asText();
    }
    JsonNode getAtt = node.get("Fn::GetAtt");
    if (getAtt != null) {
      if (getAtt.isArray() && getAtt.size() >= 1 && getAtt.get(0).isTextual()) {
        return getAtt.get(0).asText();
      }
      if (getAtt.isTextual() && !getAtt.asText().isBlank()) {
        return getAtt.asText().split("\\.", 2)[0];
      }
    }
    return null;
  }

  private static String actionVerb(String action) {
    if (action == null) {
      return "";
    }
    String verb = action.contains(":") ? action.substring(action.indexOf(':') + 1) : action;
    return verb.trim().toLowerCase();
  }

  private static boolean isWriteVerb(String verb) {
    if (verb.startsWith("*")) {
      return true;
    }
    for (String prefix :
        new String[] {
          "put", "post", "create", "update", "delete", "write", "modify", "set", "add",
          "remove", "attach", "detach", "publish", "send", "start", "stop", "invoke",
          "batchwrite", "replace", "tag", "untag"
        }) {
      if (verb.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static final class Access {
    private boolean hasRead;
    private boolean hasWrite;
    private boolean hasReference;

    private void merge(Access other) {
      hasRead |= other.hasRead;
      hasWrite |= other.hasWrite;
      hasReference |= other.hasReference;
    }

    private String level() {
      if (hasRead && hasWrite) {
        return READ_WRITE;
      }
      if (hasWrite) {
        return WRITE;
      }
      if (hasRead) {
        return READ;
      }
      return REFERENCE;
    }
  }
}
