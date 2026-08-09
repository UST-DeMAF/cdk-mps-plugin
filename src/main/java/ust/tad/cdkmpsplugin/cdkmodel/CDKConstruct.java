package ust.tad.cdkmpsplugin.cdkmodel;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a single CDK L2 or L3 construct (e.g. {@code aws-cdk-lib.aws_ecs.Cluster}) together
 * with the CloudFormation resources it owns after synthesis.
 */
public class CDKConstruct {

  @JacksonXmlProperty(localName = "id")
  private String id;

  @JacksonXmlProperty(localName = "fqn")
  private String fqn;

  @JacksonXmlProperty(localName = "stackName")
  private String stackName;

  @JacksonXmlProperty(localName = "cdkPath")
  private String cdkPath;

  @JacksonXmlElementWrapper(localName = "cfResources")
  @JacksonXmlProperty(localName = "cfResources")
  private Set<CFResource> cfResources = new HashSet<>();

  public CDKConstruct() {}

  public CDKConstruct(
      String id, String fqn, String stackName, String cdkPath, Set<CFResource> cfResources) {
    this.id = id;
    this.fqn = fqn;
    this.stackName = stackName;
    this.cdkPath = cdkPath;
    this.cfResources = cfResources;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFqn() {
    return fqn;
  }

  public void setFqn(String fqn) {
    this.fqn = fqn;
  }

  public String getStackName() {
    return stackName;
  }

  public void setStackName(String stackName) {
    this.stackName = stackName;
  }

  public String getCdkPath() {
    return cdkPath;
  }

  public void setCdkPath(String cdkPath) {
    this.cdkPath = cdkPath;
  }

  public Set<CFResource> getCfResources() {
    return cfResources;
  }

  public void setCfResources(Set<CFResource> cfResources) {
    this.cfResources = cfResources;
  }

  public CDKConstruct id(String id) {
    setId(id);
    return this;
  }

  public CDKConstruct fqn(String fqn) {
    setFqn(fqn);
    return this;
  }

  public CDKConstruct stackName(String stackName) {
    setStackName(stackName);
    return this;
  }

  public CDKConstruct cdkPath(String cdkPath) {
    setCdkPath(cdkPath);
    return this;
  }

  public CDKConstruct cfResources(Set<CFResource> cfResources) {
    setCfResources(cfResources);
    return this;
  }

  public void addCFResource(CFResource resource) {
    this.cfResources.add(resource);
  }

  public CFResource getCFResourceByType(String type) throws ConstructNotFoundException {
    Optional<CFResource> match =
        this.cfResources.stream().filter(r -> r.getType().equals(type)).findFirst();
    if (match.isPresent()) {
      return match.get();
    }
    throw new ConstructNotFoundException(
        "CF resource with type " + type + " not found in construct " + this.id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CDKConstruct)) return false;
    CDKConstruct that = (CDKConstruct) o;
    // Natural key: a construct is uniquely identified by its id within a stack.
    return Objects.equals(id, that.id) && Objects.equals(stackName, that.stackName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, stackName);
  }

  @Override
  public String toString() {
    return "CDKConstruct{"
        + "id='"
        + id
        + "', fqn='"
        + fqn
        + "', stackName='"
        + stackName
        + "', cdkPath='"
        + cdkPath
        + "', cfResources="
        + cfResources
        + '}';
  }
}
