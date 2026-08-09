package ust.tad.cdkmpsplugin.cdkmodel;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Root container for a CDK deployment model. Holds the set of CDK stacks discovered from the
 * manifest and the set of L2/L3 CDK constructs extracted from the tree, each enriched with their
 * underlying CloudFormation resources.
 */
@JacksonXmlRootElement(localName = "CDKDeploymentModel")
public class CDKDeploymentModel {

  @JacksonXmlElementWrapper(localName = "stacks")
  @JacksonXmlProperty(localName = "stacks")
  private Set<String> stacks = new HashSet<>();

  @JacksonXmlElementWrapper(localName = "constructs")
  @JacksonXmlProperty(localName = "constructs")
  private Set<CDKConstruct> constructs = new HashSet<>();

  public CDKDeploymentModel() {}

  public CDKDeploymentModel(Set<String> stacks, Set<CDKConstruct> constructs) {
    this.stacks = stacks;
    this.constructs = constructs;
  }

  public Set<String> getStacks() {
    return stacks;
  }

  public void setStacks(Set<String> stacks) {
    this.stacks = stacks;
  }

  public Set<CDKConstruct> getConstructs() {
    return constructs;
  }

  public void setConstructs(Set<CDKConstruct> constructs) {
    this.constructs = constructs;
  }

  public void addStack(String stackName) {
    this.stacks.add(stackName);
  }

  public void addConstruct(CDKConstruct construct) {
    this.constructs.add(construct);
  }

  public void addAllConstructs(Set<CDKConstruct> constructsToAdd) {
    this.constructs.addAll(constructsToAdd);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CDKDeploymentModel that = (CDKDeploymentModel) o;
    return Objects.equals(stacks, that.stacks) && Objects.equals(constructs, that.constructs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stacks, constructs);
  }

  @Override
  public String toString() {
    return "CDKDeploymentModel{" + "stacks=" + stacks + ", constructs=" + constructs + '}';
  }
}
