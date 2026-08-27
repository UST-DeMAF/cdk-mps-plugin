package ust.tad.cdkmpsplugin.analysis.cloudformationParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/**
 * Serialises a {@link CDKDeploymentModel} to a JetBrains MPS persistence-v9 model file that
 * replaces {@code languages/AWSCDK.sandbox/models/AWSCDK.sandbox.mps} in the
 * mps-transformation-awscdk project, which the MPS headless generator then transforms into
 * {@code result.yaml}.
 *
 * <p>The header and concept registry are copied verbatim from the sandbox model; the model
 * {@code ref} must stay identical so the {@code AWSCDK.sandbox} module keeps resolving its model
 * after the file is overwritten in place.
 */
public class CdkModelXmlSerializer {

  private static final String HEADER =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<model ref=\"r:9deb376a-037d-4e18-9904-34f7dbf549c1(AWSCDK.sandbox)\">\n"
          + "  <persistence version=\"9\" />\n"
          + "  <languages>\n"
          + "    <use id=\"c984930e-3d9c-43aa-96cd-735938d0f3f5\" name=\"AWSCDK\" version=\"0\" />\n"
          + "    <use id=\"f14a2376-c0aa-410c-b33a-ef6b7f4e7a0c\" name=\"EDMM\" version=\"0\" />\n"
          + "  </languages>\n"
          + "  <imports />\n"
          + "  <registry>\n"
          + "    <language id=\"c984930e-3d9c-43aa-96cd-735938d0f3f5\" name=\"AWSCDK\">\n"
          + "      <concept id=\"2767690419122310727\" name=\"AWSCDK.structure.Property\" flags=\"ng\" index=\"2uok62\">\n"
          + "        <property id=\"2767690419122311066\" name=\"key\" index=\"2uok1v\" />\n"
          + "        <property id=\"2767690419122311328\" name=\"value\" index=\"2uolX_\" />\n"
          + "      </concept>\n"
          + "      <concept id=\"2767690419122309440\" name=\"AWSCDK.structure.CFResources\" flags=\"ng\" index=\"2uokq5\">\n"
          + "        <property id=\"2767690419122310249\" name=\"type\" index=\"2uokeG\" />\n"
          + "        <property id=\"2767690419122309831\" name=\"logicalId\" index=\"2uokk2\" />\n"
          + "        <child id=\"2767690419122315423\" name=\"properties\" index=\"2uomXq\" />\n"
          + "      </concept>\n"
          + "      <concept id=\"2767690419122307370\" name=\"AWSCDK.structure.Construct\" flags=\"ng\" index=\"2uokVJ\">\n"
          + "        <property id=\"2767690419122308732\" name=\"cdkPath\" index=\"2uokAT\" />\n"
          + "        <property id=\"2767690419122308312\" name=\"stackName\" index=\"2uokGt\" />\n"
          + "        <property id=\"2767690419122307893\" name=\"fqn\" index=\"2uokNK\" />\n"
          + "        <property id=\"2767690419122307683\" name=\"id\" index=\"2uokQA\" />\n"
          + "        <child id=\"2767690419122313678\" name=\"cfResources\" index=\"2uolob\" />\n"
          + "      </concept>\n"
          + "      <concept id=\"2767690419122306456\" name=\"AWSCDK.structure.Stack\" flags=\"ng\" index=\"2uor9t\">\n"
          + "        <property id=\"2767690419122306691\" name=\"name\" index=\"2uor56\" />\n"
          + "      </concept>\n"
          + "      <concept id=\"2767690419122199849\" name=\"AWSCDK.structure.CDKDeploymentModel\" flags=\"ng\" index=\"2urLbG\">\n"
          + "        <child id=\"2767690419122312582\" name=\"constructs\" index=\"2uolD3\" />\n"
          + "        <child id=\"2767690419122311852\" name=\"stacks\" index=\"2uolPD\" />\n"
          + "      </concept>\n"
          + "      <concept id=\"2767690419122999001\" name=\"AWSCDK.structure.ReferenceProperty\" flags=\"ng\" index=\"2uvc4s\">\n"
          + "        <reference id=\"6688417925989339782\" name=\"target\" index=\"3B8hCG\" />\n"
          + "      </concept>\n"
          + "    </language>\n"
          + "  </registry>\n";

  private int nextId = 0;

  public void serialize(CDKDeploymentModel model, Path outputFile) throws IOException {
    Files.createDirectories(outputFile.getParent());
    Files.write(outputFile, toMpsXml(model).getBytes(StandardCharsets.UTF_8));
  }

  String toMpsXml(CDKDeploymentModel model) {
    nextId = 0;

    // Pass 1: assign node ids so ReferenceProperty targets can resolve.
    Map<CFResource, String> resourceIds = new LinkedHashMap<>();
    Map<String, String> idByLogicalId = new LinkedHashMap<>();
    for (CDKConstruct construct : model.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        String nodeId = "r" + (++nextId);
        resourceIds.put(resource, nodeId);
        idByLogicalId.putIfAbsent(resource.getLogicalId(), nodeId);
      }
    }

    // Pass 2: emit.
    StringBuilder sb = new StringBuilder(HEADER);
    sb.append("  <node concept=\"2urLbG\" id=\"root1\">\n");
    for (String stack : model.getStacks()) {
      sb.append("    <node concept=\"2uor9t\" id=\"s").append(++nextId).append("\" role=\"2uolPD\">\n")
          .append("      <property role=\"2uor56\" value=\"").append(esc(stack)).append("\" />\n")
          .append("    </node>\n");
    }
    for (CDKConstruct construct : model.getConstructs()) {
      appendConstruct(sb, construct, resourceIds, idByLogicalId);
    }
    sb.append("  </node>\n");
    sb.append("</model>\n");
    return sb.toString();
  }

  private void appendConstruct(
      StringBuilder sb,
      CDKConstruct construct,
      Map<CFResource, String> resourceIds,
      Map<String, String> idByLogicalId) {
    sb.append("    <node concept=\"2uokVJ\" id=\"c").append(++nextId).append("\" role=\"2uolD3\">\n");
    appendProperty(sb, "      ", "2uokGt", construct.getStackName());
    appendProperty(sb, "      ", "2uokQA", construct.getId());
    appendProperty(sb, "      ", "2uokNK", construct.getFqn());
    appendProperty(sb, "      ", "2uokAT", construct.getCdkPath());
    for (CFResource resource : construct.getCfResources()) {
      appendResource(sb, resource, resourceIds.get(resource), idByLogicalId);
    }
    sb.append("    </node>\n");
  }

  private void appendResource(
      StringBuilder sb, CFResource resource, String nodeId, Map<String, String> idByLogicalId) {
    sb.append("      <node concept=\"2uokq5\" id=\"").append(nodeId).append("\" role=\"2uolob\">\n");
    appendProperty(sb, "        ", "2uokk2", resource.getLogicalId());
    appendProperty(sb, "        ", "2uokeG", resource.getType());
    for (CFProperty property : resource.getProperties()) {
      String targetNodeId =
          property.isReference() ? idByLogicalId.get(property.getReferenceTarget()) : null;
      if (targetNodeId != null) {
        // Intrinsic reference to a resource present in this model -> MPS ReferenceProperty;
        // unresolved refs (parameters, pseudo parameters) fall through to a plain Property.
        sb.append("        <node concept=\"2uvc4s\" id=\"p").append(++nextId)
            .append("\" role=\"2uomXq\">\n");
        appendProperty(sb, "          ", "2uok1v", property.getKey());
        if ("ConnectsTo".equals(property.getKey())) {
          appendProperty(sb, "          ", "2uolX_", property.getValue());
        }
        sb.append("          <ref role=\"3B8hCG\" node=\"").append(targetNodeId).append("\" />\n");
        sb.append("        </node>\n");
      } else {
        sb.append("        <node concept=\"2uok62\" id=\"p").append(++nextId)
            .append("\" role=\"2uomXq\">\n");
        appendProperty(sb, "          ", "2uok1v", property.getKey());
        appendProperty(sb, "          ", "2uolX_", property.getValue());
        sb.append("        </node>\n");
      }
    }
    sb.append("      </node>\n");
  }

  private void appendProperty(StringBuilder sb, String indent, String role, String value) {
    if (value == null) {
      return;
    }
    sb.append(indent).append("<property role=\"").append(role).append("\" value=\"")
        .append(esc(value)).append("\" />\n");
  }

  /** Escape a string for use inside a double-quoted XML attribute. */
  private static String esc(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&':
          out.append("&amp;");
          break;
        case '<':
          out.append("&lt;");
          break;
        case '>':
          out.append("&gt;");
          break;
        case '"':
          out.append("&quot;");
          break;
        case '\n':
          out.append("&#10;");
          break;
        case '\r':
        case '\t':
          out.append(' ');
          break;
        default:
          if (c >= 0x20 || c == 0x09) {
            out.append(c);
          }
          break;
      }
    }
    return out.toString();
  }
}
