package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Entry point of the CDK parsing pipeline. Reads a {@code cdk.out} directory, orchestrates the
 * manifest/template/tree sub-parsers, and merges their outputs into a {@link CDKDeploymentModel}.
 */
public class BaseCDKParser {

  private static final String MANIFEST_FILE = "manifest.json";
  private static final String TREE_FILE = "tree.json";

  public CDKDeploymentModel parseCDKOutput(Path cdkOutDir) throws IOException {
    Path manifestPath = cdkOutDir.resolve(MANIFEST_FILE);
    Path treePath = cdkOutDir.resolve(TREE_FILE);

    if (!Files.exists(treePath)) {
      throw new NoSuchFileException("tree.json not found at " + treePath);
    }

    CDKDeploymentModel model = new CDKDeploymentModel();

    Map<String, String> stacks = new ManifestParser().parseManifest(manifestPath);
    JsonNode treeRoot = new ObjectMapper().readTree(treePath.toFile()).get("tree");

    TemplateParser templateParser = new TemplateParser();
    TreeWalker treeWalker = new TreeWalker();

    for (Map.Entry<String, String> entry : stacks.entrySet()) {
      String stackName = entry.getKey();
      String templateFile = entry.getValue();
      model.addStack(stackName);

      Map<String, CFResource> templateMap =
          templateParser.parseTemplate(cdkOutDir.resolve(templateFile));

      Set<CDKConstruct> constructs = treeWalker.walkTree(treeRoot, stackName, templateMap);
      model.addAllConstructs(constructs);
    }
    new IamConnectivityResolver().resolve(model);
    new ConnectivityGraphResolver().resolve(model);
    return model;
  }
}
