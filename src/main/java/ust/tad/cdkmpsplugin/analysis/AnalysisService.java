package ust.tad.cdkmpsplugin.analysis;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ust.tad.cdkmpsplugin.analysistask.AnalysisTaskResponseSender;
import ust.tad.cdkmpsplugin.analysistask.Location;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.BaseCDKParser;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.CdkModelXmlSerializer;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.models.ModelsService;
import ust.tad.cdkmpsplugin.models.tadm.TechnologyAgnosticDeploymentModel;

@Service
public class AnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);

    @Autowired
    private ModelsService modelsService;

    @Autowired
    private AnalysisTaskResponseSender analysisTaskResponseSender;

    @Autowired
    private MpsTransformationRunner mpsTransformationRunner;

    @Autowired
    private EdmmYamlReader edmmYamlReader;

    /**
     * Directory on the shared DeMAF volume where the intermediate parser output and the final
     * transformation output are written per transformation process, for inspection and debugging.
     */
    @Value("${output.debug-dir:/usr/share/cdk-mps-plugin}")
    private String debugDir;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Run the full CDK→EDMM pipeline for the given analysis task.
     *
     * <p>Flow: cdk.out directory → CDKDeploymentModel → MPS XML → MPS generator
     * → result.yaml → TADM → DeMAF models service.
     *
     * @param taskId                  DeMAF task identifier
     * @param transformationProcessId identifies the transformation process in DeMAF
     * @param commands                unused for CDK (reserved for future use)
     * @param locations               exactly one file:// URL pointing to a cdk.out directory
     */
    public void startAnalysis(UUID taskId, UUID transformationProcessId,
                              List<String> commands, List<Location> locations) {
        TechnologyAgnosticDeploymentModel tadm =
            modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);
        if (tadm == null) {
            analysisTaskResponseSender.sendFailureResponse(taskId,
                "Could not retrieve TADM for transformation process " + transformationProcessId);
            return;
        }

        Path cdkOutDir = resolveCdkOutDir(locations);
        if (cdkOutDir == null) {
            analysisTaskResponseSender.sendFailureResponse(taskId,
                "No valid cdk.out directory found in locations");
            return;
        }

        try {
            TechnologyAgnosticDeploymentModel result = runPipeline(cdkOutDir, transformationProcessId);
            result.setId(tadm.getId());
            result.setTransformationProcessId(transformationProcessId);
            modelsService.updateTechnologyAgnosticDeploymentModel(result);
            analysisTaskResponseSender.sendSuccessResponse(taskId);
        } catch (Exception e) {
            LOG.error("CDK analysis pipeline failed", e);
            analysisTaskResponseSender.sendFailureResponse(taskId,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private TechnologyAgnosticDeploymentModel runPipeline(Path cdkOutDir, UUID transformationProcessId)
            throws IOException, InterruptedException, MpsGenerationException {
        Path outputDir = Path.of(debugDir, transformationProcessId.toString());
        Files.createDirectories(outputDir);

        CDKDeploymentModel cdkModel = new BaseCDKParser().parseCDKOutput(cdkOutDir);
        LOG.info("Parsed CDK model: {} stacks, {} constructs",
            cdkModel.getStacks().size(), cdkModel.getConstructs().size());

        Path parserOutput = outputDir.resolve("cdkDeploymentModel.json");
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(parserOutput.toFile(), cdkModel);
        LOG.info("Wrote parser output to {}", parserOutput);

        Path xmlFile = Files.createTempFile("cdk-model-", ".xml");
        try {
            new CdkModelXmlSerializer().serialize(cdkModel, xmlFile);
            LOG.info("Serialized CDK model XML to {}", xmlFile);

            Path resultYaml = mpsTransformationRunner.generate(xmlFile);
            LOG.info("MPS generated result.yaml at {}", resultYaml);

            Path transformationOutput = outputDir.resolve("result.yaml");
            Files.copy(resultYaml, transformationOutput, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Wrote transformation output to {}", transformationOutput);

            return edmmYamlReader.read(resultYaml);
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    private Path resolveCdkOutDir(List<Location> locations) {
        if (locations == null || locations.isEmpty()) return null;
        for (Location location : locations) {
            if (location.getUrl() == null) continue;
            try {
                URI uri = location.getUrl().toURI();
                if ("file".equals(uri.getScheme())) {
                    Path p = Path.of(uri);
                    if (Files.isDirectory(p)) return p;
                }
            } catch (URISyntaxException ignored) {
            }
        }
        return null;
    }
}
