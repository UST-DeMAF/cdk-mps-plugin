package ust.tad.cdkmpsplugin.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.IamConnectivityResolver;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

class IamConnectivityResolverTest {

  @Test
  void derivesConnectsToFromIamGrantAndEnvReference() {
    CFResource table = resource("MyTable", "AWS::DynamoDB::Table");
    CFResource bucket = resource("MyBucket", "AWS::S3::Bucket");
    CFResource role = resource("MyRole", "AWS::IAM::Role");

    CFResource lambda = resource("MyFunction", "AWS::Lambda::Function");
    lambda.addProperty(new CFProperty("Role", "MyRole", "MyRole"));
    lambda.addProperty(
        new CFProperty("Environment", "{\"Variables\":{\"BUCKET\":{\"Ref\":\"MyBucket\"}}}"));

    CFResource policy = resource("MyRoleDefaultPolicy", "AWS::IAM::Policy");
    policy.addProperty(new CFProperty("Roles", "MyRole"));
    policy.addProperty(
        new CFProperty(
            "PolicyDocument",
            "{\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Action\":[\"dynamodb:PutItem\",\"dynamodb:GetItem\"],"
                + "\"Resource\":{\"Fn::GetAtt\":[\"MyTable\",\"Arn\"]}}]}"));

    CDKDeploymentModel model = model(table, bucket, role, lambda, policy);
    new IamConnectivityResolver().resolve(model);

    assertEquals("readwrite", connectsTo(lambda, "MyTable"));
    assertEquals("reference", connectsTo(lambda, "MyBucket"));
  }

  @Test
  void ignoresDenyTrustPolicyAndWildcardResource() {
    CFResource table = resource("MyTable", "AWS::DynamoDB::Table");
    CFResource role = resource("MyRole", "AWS::IAM::Role");
    role.addProperty(
        new CFProperty(
            "AssumeRolePolicyDocument",
            "{\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"sts:AssumeRole\","
                + "\"Resource\":{\"Fn::GetAtt\":[\"MyTable\",\"Arn\"]}}]}"));

    CFResource lambda = resource("MyFunction", "AWS::Lambda::Function");
    lambda.addProperty(new CFProperty("Role", "MyRole", "MyRole"));

    CFResource policy = resource("Deny", "AWS::IAM::Policy");
    policy.addProperty(new CFProperty("Roles", "MyRole"));
    policy.addProperty(
        new CFProperty(
            "PolicyDocument",
            "{\"Statement\":[{\"Effect\":\"Deny\",\"Action\":\"dynamodb:PutItem\","
                + "\"Resource\":{\"Fn::GetAtt\":[\"MyTable\",\"Arn\"]}},"
                + "{\"Effect\":\"Allow\",\"Action\":\"logs:CreateLogGroup\",\"Resource\":\"*\"}]}"));

    CDKDeploymentModel model = model(table, role, lambda, policy);
    new IamConnectivityResolver().resolve(model);

    assertTrue(connectsTo(lambda, "MyTable") == null, "trust policy and deny must not create a link");
  }

  @Test
  void findsAnEnvReferenceBuiltByStringConcatenation() {
    // A URL assembled from a domain name hides the reference inside an Fn::Join.
    CFResource distribution = resource("MyDistribution", "AWS::CloudFront::Distribution");
    CFResource lambda = resource("MyFunction", "AWS::Lambda::Function");
    lambda.addProperty(
        new CFProperty(
            "Environment",
            "{\"Variables\":{\"PAGES_URL\":{\"Fn::Join\":[\"\",[\"https://\","
                + "{\"Fn::GetAtt\":[\"MyDistribution\",\"DomainName\"]}]]}}}"));

    CDKDeploymentModel model = model(distribution, lambda);
    new IamConnectivityResolver().resolve(model);

    assertEquals("reference", connectsTo(lambda, "MyDistribution"));
  }

  @Test
  void aGrantOnALogGroupIsNotAnEdge() {
    CFResource logGroup = resource("MyLogGroup", "AWS::Logs::LogGroup");
    CFResource role = resource("MyRole", "AWS::IAM::Role");
    CFResource stream = resource("MyDelivery", "AWS::KinesisFirehose::DeliveryStream");
    stream.addProperty(new CFProperty("Role", "MyRole", "MyRole"));

    CFResource policy = resource("MyRoleDefaultPolicy", "AWS::IAM::Policy");
    policy.addProperty(new CFProperty("Roles", "MyRole"));
    policy.addProperty(
        new CFProperty(
            "PolicyDocument",
            "{\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"logs:PutLogEvents\","
                + "\"Resource\":{\"Fn::GetAtt\":[\"MyLogGroup\",\"Arn\"]}}]}"));

    CDKDeploymentModel model = model(logGroup, role, stream, policy);
    new IamConnectivityResolver().resolve(model);

    assertTrue(connectsTo(stream, "MyLogGroup") == null, "observability is not deployment topology");
  }

  @Test
  void metadataLookupsDoNotMakeAProducerLookLikeAReader() {
    // grantSendMessages always adds the two Get calls, but the sender never reads a message.
    CFResource queue = resource("MyQueue", "AWS::SQS::Queue");
    CFResource role = resource("MyRole", "AWS::IAM::Role");
    CFResource rule = resource("MyRule", "AWS::IoT::TopicRule");
    rule.addProperty(new CFProperty("Role", "MyRole", "MyRole"));

    CFResource policy = resource("MyRoleDefaultPolicy", "AWS::IAM::Policy");
    policy.addProperty(new CFProperty("Roles", "MyRole"));
    policy.addProperty(
        new CFProperty(
            "PolicyDocument",
            "{\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Action\":[\"sqs:GetQueueAttributes\",\"sqs:GetQueueUrl\",\"sqs:SendMessage\"],"
                + "\"Resource\":{\"Fn::GetAtt\":[\"MyQueue\",\"Arn\"]}}]}"));

    CDKDeploymentModel model = model(queue, role, rule, policy);
    new IamConnectivityResolver().resolve(model);

    assertEquals("write", connectsTo(rule, "MyQueue"));
  }

  private static String connectsTo(CFResource accessor, String target) {
    for (CFProperty property : accessor.getProperties()) {
      if ("ConnectsTo".equals(property.getKey()) && target.equals(property.getReferenceTarget())) {
        return property.getValue();
      }
    }
    return null;
  }

  private static CFResource resource(String logicalId, String type) {
    return new CFResource(logicalId, type, new LinkedHashSet<>());
  }

  private static CDKDeploymentModel model(CFResource... resources) {
    Set<CFResource> set = new LinkedHashSet<>();
    for (CFResource resource : resources) {
      set.add(resource);
    }
    CDKConstruct construct = new CDKConstruct("c", "fqn", "Stack", "path", set);
    CDKDeploymentModel model = new CDKDeploymentModel();
    model.addConstruct(construct);
    return model;
  }
}
