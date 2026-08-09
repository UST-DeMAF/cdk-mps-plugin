package ust.tad.cdkmpsplugin.cdkmodel;

/**
 * Thrown when a CDK construct, CloudFormation resource, or property cannot be found in the parsed
 * CDK deployment model.
 */
public class ConstructNotFoundException extends Exception {

  public ConstructNotFoundException(String message) {
    super(message);
  }
}
