package ust.tad.cdkmpsplugin.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.TemplateParser;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

class PropertyFlattenerTest {

  @TempDir Path dir;

  private Map<String, CFResource> parse(String template) throws IOException {
    Path f = dir.resolve("t.template.json");
    Files.writeString(f, template);
    return new TemplateParser().parseTemplate(f);
  }

  private static String value(CFResource r, String key) {
    for (CFProperty p : r.getProperties()) {
      if (key.equals(p.getKey())) return p.getValue();
    }
    return null;
  }

  @Test
  void flattensContainerDefinitionsIntoDottedLeaves() throws IOException {
    CFResource td = parse("{\"Resources\":{\"Task\":{\"Type\":\"AWS::ECS::TaskDefinition\","
        + "\"Properties\":{\"ContainerDefinitions\":[{\"Name\":\"web\","
        + "\"Image\":\"public.ecr.aws/nginx/nginx:latest\","
        + "\"PortMappings\":[{\"ContainerPort\":80,\"Protocol\":\"tcp\"}]}]}}}}").get("Task");

    assertEquals("web", value(td, "ContainerDefinitions.0.Name"));
    assertEquals("public.ecr.aws/nginx/nginx:latest", value(td, "ContainerDefinitions.0.Image"));
    assertEquals("80", value(td, "ContainerDefinitions.0.PortMappings.0.ContainerPort"));
    assertEquals("tcp", value(td, "ContainerDefinitions.0.PortMappings.0.Protocol"));
  }

  @Test
  void keepsTheRawValueBecauseItCarriesReferences() throws IOException {
    CFResource td = parse("{\"Resources\":{\"Task\":{\"Type\":\"AWS::ECS::TaskDefinition\","
        + "\"Properties\":{\"ContainerDefinitions\":[{\"Name\":\"web\","
        + "\"LogConfiguration\":{\"Options\":{\"group\":{\"Ref\":\"Logs\"}}}}]}}},"
        + "\"Logs\":{\"Type\":\"AWS::Logs::LogGroup\"}}}").get("Task");

    boolean rawKept = td.getProperties().stream()
        .anyMatch(p -> "ContainerDefinitions".equals(p.getKey()) && p.getValue().startsWith("["));
    assertTrue(rawKept, "raw JSON must survive so nested references are still found");

    boolean refFound = td.getProperties().stream()
        .anyMatch(p -> p.getNestedTargets().contains("Logs"));
    assertTrue(refFound, "the nested Ref must still be discoverable");
  }

  @Test
  void doesNotTurnIntrinsicsIntoLeaves() throws IOException {
    CFResource fn = parse("{\"Resources\":{\"Fn\":{\"Type\":\"AWS::Lambda::Function\","
        + "\"Properties\":{\"Environment\":{\"Variables\":{\"T\":{\"Ref\":\"Tbl\"}}}}}},"
        + "\"Tbl\":{\"Type\":\"AWS::DynamoDB::Table\"}}}").get("Fn");

    Set<String> keys = Set.of("Environment.Variables.T.Ref", "Environment.Variables.T");
    for (CFProperty p : fn.getProperties()) {
      assertFalse(keys.contains(p.getKey()),
          "an intrinsic must not be flattened into a value: " + p.getKey());
    }
  }

  @Test
  void leavesPlainScalarsAlone() throws IOException {
    CFResource b = parse("{\"Resources\":{\"B\":{\"Type\":\"AWS::S3::Bucket\","
        + "\"Properties\":{\"BucketName\":\"my-bucket\"}}}}").get("B");
    assertEquals("my-bucket", value(b, "BucketName"));
    assertEquals(1, b.getProperties().size(), "a scalar must not gain extra keys");
  }
}
