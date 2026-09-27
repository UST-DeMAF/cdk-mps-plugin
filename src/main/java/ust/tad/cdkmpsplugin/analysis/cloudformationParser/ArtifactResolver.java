package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.util.ArrayList;
import java.util.List;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Identifies the deployable artifact a resource carries and records it as {@code artifactType} and
 * {@code artifactUri}. A container definition yields the image it runs, and a function yields the
 * package its code is uploaded as. Both kinds match those used by the reference models in the DeMAF
 * type definitions.
 */
public class ArtifactResolver {

  static final String TYPE_KEY = "artifactType";
  static final String URI_KEY = "artifactUri";

  private static final String DOCKER_IMAGE = "docker_image";
  private static final String FILE = "file";

  public void resolve(CDKDeploymentModel model) {
    for (CDKConstruct construct : model.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        if (find(resource, TYPE_KEY) != null) {
          continue;
        }
        String image = firstImage(resource);
        if (image != null) {
          add(resource, DOCKER_IMAGE, image);
          continue;
        }
        String bucket = find(resource, "Code.S3Bucket");
        String key = find(resource, "Code.S3Key");
        if (bucket != null && key != null) {
          add(resource, FILE, "s3://" + bucket + "/" + key);
        }
      }
    }
  }

  /** The image of the first container definition, taken from the flattened properties. */
  private String firstImage(CFResource resource) {
    List<String> keys = new ArrayList<>();
    for (CFProperty property : resource.getProperties()) {
      String key = property.getKey();
      if (key.startsWith("ContainerDefinitions.") && key.endsWith(".Image")) {
        keys.add(key);
      }
    }
    if (keys.isEmpty()) {
      return null;
    }
    keys.sort(String::compareTo);
    return find(resource, keys.get(0));
  }

  private void add(CFResource resource, String type, String uri) {
    resource.addProperty(new CFProperty(TYPE_KEY, type));
    resource.addProperty(new CFProperty(URI_KEY, uri));
  }

  private String find(CFResource resource, String key) {
    for (CFProperty property : resource.getProperties()) {
      if (key.equals(property.getKey())) {
        return property.getValue();
      }
    }
    return null;
  }
}
