package ust.tad.cdkmpsplugin.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Drives the MPS headless generator: copies the serialised CDK model XML into the MPS project's
 * model input path, runs the MPS Ant {@code generate} target, and returns the generated
 * {@code result.yaml}. Paths are configured via the {@code mps.generator.*} properties.
 */
@Component
public class MpsTransformationRunner {

    private static final Logger log = LoggerFactory.getLogger(MpsTransformationRunner.class);

    private static final int TIMEOUT_MINUTES = 10;

    @Value("${mps.generator.project-dir}")
    private String mpsProjectDir;

    @Value("${mps.generator.output-dir:}")
    private String mpsOutputDir;

    @Value("${mps.generator.model-input-path:solutions/AWSDKSandbox/models/sandbox.mps}")
    private String modelInputRelativePath;

    public Path generate(Path modelXmlPath)
            throws IOException, InterruptedException, MpsGenerationException {

        Path projectDir = Path.of(mpsProjectDir);
        if (!Files.isDirectory(projectDir)) {
            throw new MpsGenerationException(
                "MPS project directory not found: " + projectDir);
        }

        copyModelXml(modelXmlPath, projectDir);

        int exitCode = runAntGenerate(projectDir);
        if (exitCode != 0) {
            throw new MpsGenerationException(
                "MPS Ant generate task failed with exit code " + exitCode);
        }

        return locateResultYaml(projectDir);
    }

    private void copyModelXml(Path modelXmlPath, Path projectDir) throws IOException {
        Path target = projectDir.resolve(modelInputRelativePath);
        Files.createDirectories(target.getParent());
        Files.copy(modelXmlPath, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Copied model XML to {}", target);
    }

    private int runAntGenerate(Path projectDir) throws IOException, InterruptedException, MpsGenerationException {
        List<String> command = buildAntCommand();
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(projectDir.toFile())
            .inheritIO();

        log.info("Starting MPS Ant generate in {} with command {}", projectDir, command);
        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new MpsGenerationException(
                "MPS generator timed out after " + TIMEOUT_MINUTES + " minutes");
        }
        return process.exitValue();
    }

    private Path locateResultYaml(Path projectDir) throws MpsGenerationException {
        Path outputBase = mpsOutputDir != null && !mpsOutputDir.isBlank()
            ? projectDir.resolve(mpsOutputDir)
            : projectDir.resolve("solutions/AWSDKSandbox/source_gen");

        try (var stream = Files.walk(outputBase, 4)) {
            java.util.Optional<Path> found = stream
                .filter(p -> p.getFileName().toString().equals("result.yaml"))
                .findFirst();
            if (!found.isPresent()) {
                throw new MpsGenerationException("result.yaml not found under " + outputBase);
            }
            return found.get();
        } catch (IOException e) {
            throw new MpsGenerationException("Could not search for result.yaml: " + e.getMessage());
        }
    }

    /**
     * Builds the command for the MPS Ant {@code generate} target. When {@code MPS_HOME} is set,
     * Ant is invoked via the launcher bundled with MPS on the {@code JAVA_HOME} JetBrains Runtime,
     * with {@code util.jar} and {@code 3rd-party-rt.jar} on Ant's classpath (required by the MPS
     * generate task). Otherwise it falls back to an {@code ant} executable on the PATH.
     */
    private List<String> buildAntCommand() {
        String mpsHome = System.getenv("MPS_HOME");
        if (mpsHome != null && !mpsHome.isBlank()) {
            String javaHome = System.getenv("JAVA_HOME");
            String javaBin = javaHome != null && !javaHome.isBlank()
                ? Path.of(javaHome, "bin", "java").toString()
                : "java";
            Path antLauncher = Path.of(mpsHome, "lib", "ant", "lib", "ant-launcher.jar");
            String mpsAntClasspath = Path.of(mpsHome, "lib", "util.jar")
                + File.pathSeparator + Path.of(mpsHome, "lib", "3rd-party-rt.jar");
            return List.of(
                javaBin,
                "-classpath", antLauncher.toString(),
                "-Dmps_home=" + mpsHome,
                "org.apache.tools.ant.launch.Launcher",
                "-cp", mpsAntClasspath,
                "generate");
        }
        return List.of(isWindows() ? "ant.bat" : "ant", "generate");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
