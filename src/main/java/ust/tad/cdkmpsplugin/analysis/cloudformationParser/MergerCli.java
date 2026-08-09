package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;

/**
 * Development CLI for the merger: reads a {@code cdk.out} directory and writes the merged
 * {@link CDKDeploymentModel} as JSON and MPS XML. Not used by the deployed service.
 */
public final class MergerCli {

  private static final String DEFAULT_OUTPUT_FILENAME = "cdkDeploymentModel.json";

  private MergerCli() {}

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println(
          "Usage: MergerCli <cdkOutDir> [outputFile]\n"
              + "  cdkOutDir   path to the cdk.out directory produced by `cdk synth`\n"
              + "  outputFile  optional output path (default: <cdkOutDir>/cdkDeploymentModel.json)");
      System.exit(2);
    }

    Path cdkOutDir = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputFile =
        args.length >= 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : cdkOutDir.resolve(DEFAULT_OUTPUT_FILENAME);

    if (!Files.isDirectory(cdkOutDir)) {
      System.err.println("Error: not a directory: " + cdkOutDir);
      System.exit(1);
    }

    CDKDeploymentModel model = new BaseCDKParser().parseCDKOutput(cdkOutDir);

    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    Files.createDirectories(outputFile.getParent());
    mapper.writeValue(outputFile.toFile(), model);

    Path xmlFile = outputFile.resolveSibling(
        outputFile.getFileName().toString().replaceFirst("\\.json$", ".xml"));
    new CdkModelXmlSerializer().serialize(model, xmlFile);

    System.out.printf(
        "Wrote merged CDK deployment model: %s and %s (%d stacks, %d constructs)%n",
        outputFile, xmlFile, model.getStacks().size(), model.getConstructs().size());
  }
}
