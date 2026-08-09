package ust.tad.cdkmpsplugin.cdkmodel;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a single CloudFormation resource (an entry in the {@code Resources} section of a CDK
 * synthesised template), with its properties flattened to {@link CFProperty} key/value pairs.
 */
public class CFResource {

  @JacksonXmlProperty(localName = "logicalId")
  private String logicalId;

  @JacksonXmlProperty(localName = "type")
  private String type;

  @JacksonXmlElementWrapper(localName = "properties")
  @JacksonXmlProperty(localName = "properties")
  private Set<CFProperty> properties = new HashSet<>();

  public CFResource() {}

  public CFResource(String logicalId, String type, Set<CFProperty> properties) {
    this.logicalId = logicalId;
    this.type = type;
    this.properties = properties;
  }

  public String getLogicalId() {
    return logicalId;
  }

  public void setLogicalId(String logicalId) {
    this.logicalId = logicalId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Set<CFProperty> getProperties() {
    return properties;
  }

  public void setProperties(Set<CFProperty> properties) {
    this.properties = properties;
  }

  public CFResource logicalId(String logicalId) {
    setLogicalId(logicalId);
    return this;
  }

  public CFResource type(String type) {
    setType(type);
    return this;
  }

  public CFResource properties(Set<CFProperty> properties) {
    setProperties(properties);
    return this;
  }

  public void addProperty(CFProperty property) {
    this.properties.add(property);
  }

  public CFProperty getPropertyByKey(String key) throws ConstructNotFoundException {
    Optional<CFProperty> match =
        this.properties.stream().filter(p -> p.getKey().equals(key)).findFirst(); //lambda
    if (match.isPresent()) {
      return match.get();
    }
    throw new ConstructNotFoundException(
        "Property with key " + key + " not found in resource " + this.logicalId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CFResource)) return false;
    CFResource that = (CFResource) o;
    return Objects.equals(logicalId, that.logicalId)
        && Objects.equals(type, that.type)
        && Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(logicalId, type, properties);
  }

  @Override
  public String toString() {
    return "CFResource{"
        + "logicalId='"
        + logicalId
        + "', type='"
        + type
        + "', properties="
        + properties
        + '}';
  }
}
