package ust.tad.cdkmpsplugin.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.BaseCDKParser;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.CdkModelXmlSerializer;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

/** Cucumber step definitions for {@code merger.feature}. */
public class MergerSteps {

  private Path fixtureDir;
  private CDKDeploymentModel result;
  private String xmlContent;

  @Given("a CDK output directory at fixture {string}")
  public void aCdkOutputDirectoryAtFixture(String fixtureName) throws URISyntaxException {
    URL url = getClass().getClassLoader().getResource("fixtures/" + fixtureName);
    if (url == null) {
      // For deliberately missing fixtures we point at a non-existent path so the parser fails.
      this.fixtureDir = Paths.get("src/test/resources/fixtures", fixtureName);
    } else {
      // Use URI to handle URL-encoded characters (spaces become %20 in URL.getPath()).
      this.fixtureDir = Paths.get(url.toURI());
    }
  }

  @When("I run the CDK merger on that directory")
  public void iRunTheCdkMergerOnThatDirectory() throws IOException {
    BaseCDKParser parser = new BaseCDKParser();
    // For error scenarios we still attempt the call; the matching Then-step expects an IOException.
    if (isErrorFixture()) {
      // Defer the call: it will be re-invoked in the Then step that asserts the exception.
      this.result = null;
      return;
    }
    this.result = parser.parseCDKOutput(this.fixtureDir);
  }

  // ----------------------------------------------------------------------------------------------
  // Stack assertions
  // ----------------------------------------------------------------------------------------------

  @Then("the result should contain {int} stack")
  public void theResultShouldContainStack(int count) {
    assertThat(this.result.getStacks()).hasSize(count);
  }

  @Then("the result should contain {int} stacks")
  public void theResultShouldContainStacks(int count) {
    assertThat(this.result.getStacks()).hasSize(count);
  }

  @Then("the result should contain a stack named {string}")
  public void theResultShouldContainAStackNamed(String stackName) {
    assertThat(this.result.getStacks()).contains(stackName);
  }

  // ----------------------------------------------------------------------------------------------
  // Construct presence assertions
  // ----------------------------------------------------------------------------------------------

  @Then("the result should contain a construct with id {string} and fqn {string}")
  public void theResultShouldContainAConstructWithIdAndFqn(String id, String fqn) {
    boolean found =
        this.result.getConstructs().stream()
            .anyMatch(c -> id.equals(c.getId()) && fqn.equals(c.getFqn()));
    assertThat(found)
        .as("Construct with id=%s and fqn=%s should be present in %s", id, fqn, summarise())
        .isTrue();
  }

  @Then("the result should contain a construct with id {string}")
  public void theResultShouldContainAConstructWithId(String id) {
    boolean found = this.result.getConstructs().stream().anyMatch(c -> id.equals(c.getId()));
    assertThat(found).as("Construct with id=%s should be present in %s", id, summarise()).isTrue();
  }

  @Then("the result should not contain a construct with id {string}")
  public void theResultShouldNotContainAConstructWithId(String id) {
    boolean found = this.result.getConstructs().stream().anyMatch(c -> id.equals(c.getId()));
    assertThat(found).as("Construct with id=%s should NOT be present", id).isFalse();
  }

  @Then("the result should not contain a construct with fqn {string}")
  public void theResultShouldNotContainAConstructWithFqn(String fqn) {
    boolean found = this.result.getConstructs().stream().anyMatch(c -> fqn.equals(c.getFqn()));
    assertThat(found).as("Construct with fqn=%s should NOT be present", fqn).isFalse();
  }

  @Then("the result should not contain a construct whose fqn contains {string}")
  public void theResultShouldNotContainAConstructWhoseFqnContains(String fragment) {
    boolean found =
        this.result.getConstructs().stream()
            .anyMatch(c -> Objects.requireNonNullElse(c.getFqn(), "").contains(fragment));
    assertThat(found)
        .as("No construct fqn should contain '%s', but %s", fragment, summarise())
        .isFalse();
  }

  // ----------------------------------------------------------------------------------------------
  // CF resource assertions
  // ----------------------------------------------------------------------------------------------

  @Then("the construct {string} should have {int} CF resource of type {string}")
  public void theConstructShouldHaveCfResourceOfType(String id, int count, String type) {
    CDKConstruct c = findConstruct(id);
    long matching = c.getCfResources().stream().filter(r -> type.equals(r.getType())).count();
    assertThat(matching)
        .as("Construct %s should have %d resource(s) of type %s", id, count, type)
        .isEqualTo(count);
  }

  @Then("the construct {string} should have a CF resource of type {string}")
  public void theConstructShouldHaveACfResourceOfType(String id, String type) {
    CDKConstruct c = findConstruct(id);
    boolean has = c.getCfResources().stream().anyMatch(r -> type.equals(r.getType()));
    assertThat(has)
        .as(
            "Construct %s should have a CF resource of type %s, found %s",
            id, type, resourceTypesIn(c))
        .isTrue();
  }

  @Then("the CF resource {string} of {string} should have property {string} with value {string}")
  public void theCfResourceShouldHaveProperty(
      String resourceType, String constructId, String key, String value) {
    CDKConstruct c = findConstruct(constructId);
    CFResource resource =
        c.getCfResources().stream()
            .filter(r -> resourceType.equals(r.getType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Resource type "
                            + resourceType
                            + " not found on construct "
                            + constructId));
    CFProperty property =
        resource.getProperties().stream()
            .filter(p -> key.equals(p.getKey()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Property " + key + " not found on resource " + resourceType));
    assertThat(property.getValue()).isEqualTo(value);
  }

  // ----------------------------------------------------------------------------------------------
  // Uniqueness / cross-stack assertions
  // ----------------------------------------------------------------------------------------------

  @Then("no CF resource logicalId should appear in more than one construct")
  public void noLogicalIdAppearsInMoreThanOneConstruct() {
    Map<String, String> seen = new HashMap<>();
    for (CDKConstruct construct : this.result.getConstructs()) {
      for (CFResource resource : construct.getCfResources()) {
        String prev = seen.put(resource.getLogicalId(), construct.getId());
        assertThat(prev)
            .as(
                "logicalId %s appears in both %s and %s",
                resource.getLogicalId(), prev, construct.getId())
            .isNull();
      }
    }
  }

  @Then("all constructs from stack {string} should have stackName {string}")
  public void allConstructsFromStackShouldHaveStackName(String filterStack, String expected) {
    Set<CDKConstruct> matching = new HashSet<>();
    for (CDKConstruct c : this.result.getConstructs()) {
      if (filterStack.equals(c.getStackName())) {
        matching.add(c);
      }
    }
    assertThat(matching)
        .as("Expected at least one construct in stack %s", filterStack)
        .isNotEmpty();
    for (CDKConstruct c : matching) {
      assertThat(c.getStackName()).isEqualTo(expected);
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Error scenarios
  // ----------------------------------------------------------------------------------------------

  @Then("the merger should throw an IOException")
  public void theMergerShouldThrowAnIOException() {
    BaseCDKParser parser = new BaseCDKParser();
    assertThrows(IOException.class, () -> parser.parseCDKOutput(this.fixtureDir));
  }

  // ----------------------------------------------------------------------------------------------
  // XML serialisation assertions
  // ----------------------------------------------------------------------------------------------

  @When("I serialise the model to XML")
  public void iSerialiseTheModelToXml() throws IOException {
    Path tmp = Files.createTempFile("cdkmodel-", ".xml");
    new CdkModelXmlSerializer().serialize(this.result, tmp);
    this.xmlContent = Files.readString(tmp);
    Files.deleteIfExists(tmp);
  }

  @Then("the XML root element should be {string}")
  public void theXmlRootElementShouldBe(String rootElement) {
    assertThat(this.xmlContent).contains("<" + rootElement);
  }

  @Then("the XML should contain a stack element {string}")
  public void theXmlShouldContainAStackElement(String stackName) {
    // MPS persistence v9: Stack node property, role 2uor56 = Stack.name
    assertThat(this.xmlContent)
        .as("XML should contain stack property for %s", stackName)
        .contains("role=\"2uor56\" value=\"" + stackName + "\"");
  }

  @Then("the XML should contain a construct element with id {string}")
  public void theXmlShouldContainAConstructElementWithId(String id) {
    // MPS persistence v9: Construct node property, role 2uokQA = Construct.id
    assertThat(this.xmlContent)
        .as("XML should contain construct id property %s", id)
        .contains("role=\"2uokQA\" value=\"" + id + "\"");
  }

  // ----------------------------------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------------------------------

  private boolean isErrorFixture() {
    String name = this.fixtureDir.getFileName().toString();
    return name.startsWith("missing-");
  }

  private CDKConstruct findConstruct(String id) {
    return this.result.getConstructs().stream()
        .filter(c -> id.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Construct " + id + " not found in " + summarise()));
  }

  private String summarise() {
    StringBuilder sb = new StringBuilder("[");
    for (CDKConstruct c : this.result.getConstructs()) {
      sb.append(c.getId()).append("(").append(c.getFqn()).append("), ");
    }
    sb.append("]");
    return sb.toString();
  }

  private String resourceTypesIn(CDKConstruct c) {
    StringBuilder sb = new StringBuilder("[");
    for (CFResource r : c.getCfResources()) {
      sb.append(r.getType()).append(", ");
    }
    sb.append("]");
    return sb.toString();
  }
}
