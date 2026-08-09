package ust.tad.cdkmpsplugin.cdkmodel;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.Objects;

/**
 * A single key/value entry on a CloudFormation resource. The {@code value} is the stringified JSON
 * of the property, preserving CloudFormation intrinsic functions ({@code Ref}, {@code Fn::GetAtt},
 * {@code Fn::Sub}, ...) as-is for later interpretation by the MPS generator.
 */
public class CFProperty {

  @JacksonXmlProperty(localName = "key")
  private String key;

  @JacksonXmlProperty(localName = "value")
  private String value;

  /**
   * Logical id of the CloudFormation resource this property points at, when the property value is
   * an intrinsic reference ({@code {"Ref": "LogicalId"}} or {@code Fn::GetAtt}). {@code null} for
   * plain values. Serialised as an MPS {@code ReferenceProperty} when the target resource exists in
   * the model.
   */
  @JacksonXmlProperty(localName = "referenceTarget")
  private String referenceTarget;

  public CFProperty() {}

  public CFProperty(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public CFProperty(String key, String value, String referenceTarget) {
    this.key = key;
    this.value = value;
    this.referenceTarget = referenceTarget;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public CFProperty key(String key) {
    setKey(key);
    return this;
  }

  public CFProperty value(String value) {
    setValue(value);
    return this;
  }

  public String getReferenceTarget() {
    return referenceTarget;
  }

  public void setReferenceTarget(String referenceTarget) {
    this.referenceTarget = referenceTarget;
  }

  public boolean isReference() {
    return referenceTarget != null && !referenceTarget.isBlank();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CFProperty)) return false;
    CFProperty that = (CFProperty) o;
    return Objects.equals(key, that.key)
        && Objects.equals(value, that.value)
        && Objects.equals(referenceTarget, that.referenceTarget);
  }

  //deduplicate
  @Override
  public int hashCode() {
    return Objects.hash(key, value, referenceTarget); //deduplicate
  }

  //printable
  @Override
  public String toString() {
    return "CFProperty{key='" + key + "', value='" + value
        + (referenceTarget != null ? "', referenceTarget='" + referenceTarget : "")
        + "'}";
  }
}
