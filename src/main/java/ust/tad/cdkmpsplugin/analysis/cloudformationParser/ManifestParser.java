package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses a CDK {@code manifest.json} produced by {@code cdk synth}. Returns the set of
 * CloudFormation stacks declared in the manifest along with the relative path of their template
 * file. Other artifact types ({@code cdk:asset-manifest}, {@code cdk:tree}, {@code
 * cdk:feature-flag-report}) are ignored.
 */
public class ManifestParser {

  private static final String STACK_ARTIFACT_TYPE = "aws:cloudformation:stack";

  public Map<String, String> parseManifest(Path manifestFile) throws IOException {
    if (!Files.exists(manifestFile)) {
      throw new NoSuchFileException("manifest.json not found at " + manifestFile);
    }

    JsonNode root = new ObjectMapper().readTree(manifestFile.toFile());
    JsonNode artifacts = root.get("artifacts");
    Map<String, String> stacks = new LinkedHashMap<>();

    if (artifacts == null || !artifacts.isObject()) {
      return stacks;
    }

    Iterator<Map.Entry<String, JsonNode>> iter = artifacts.fields();
    while (iter.hasNext()) {
      Map.Entry<String, JsonNode> entry = iter.next();
      JsonNode artifact = entry.getValue();
      JsonNode type = artifact.get("type");
      if (type == null || !STACK_ARTIFACT_TYPE.equals(type.asText())) {
        continue;
      }
      JsonNode properties = artifact.get("properties");
      if (properties == null) {
        continue;
      }
      JsonNode templateFile = properties.get("templateFile");
      if (templateFile == null) {
        continue;
      }
      stacks.put(entry.getKey(), templateFile.asText());
    }
    return stacks;
  }
}
