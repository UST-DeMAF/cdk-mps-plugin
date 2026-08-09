package ust.tad.cdkmpsplugin.analysis;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * JUnit Platform Suite that runs every {@code .feature} file under {@code
 * src/test/resources/features/} using the Cucumber engine. The steps live in {@link MergerSteps}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "ust.tad.cdkmpsplugin.analysis")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class MergerCucumberRunner {}
