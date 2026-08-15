Feature: CDK Deployment Model Merger
  As the CDK plugin
  I want to merge CDK tree.json and CloudFormation template files
  So that CloudFormation resources are grouped under their owning L2/L3 CDK constructs

  Scenario: Merge a single-stack CDK output into a CDKDeploymentModel
    Given a CDK output directory at fixture "single-stack"
    When I run the CDK merger on that directory
    Then the result should contain 1 stack
    And the result should contain a stack named "SimpleExampleCdkToCfStack"
    And the result should contain a construct with id "Cluster" and fqn "aws-cdk-lib.aws_ecs.Cluster"
    And the construct "Cluster" should have 1 CF resource of type "AWS::ECS::Cluster"
    And the result should contain a construct with id "WebService"
    And the construct "WebService" should have a CF resource of type "AWS::ECS::TaskDefinition"
    And the construct "WebService" should have a CF resource of type "AWS::ECS::Service"
    And the CF resource "AWS::ECS::TaskDefinition" of "WebService" should have property "Cpu" with value "256"

  Scenario: List-of-reference properties are resolved to comma-joined descriptive values
    Given a CDK output directory at fixture "single-stack"
    When I run the CDK merger on that directory
    Then the CF resource "AWS::ElasticLoadBalancingV2::LoadBalancer" of "WebService" should have property "Subnets" with value "10.0.0.0/18,10.0.64.0/18"
    And the CF resource "AWS::ElasticLoadBalancingV2::LoadBalancer" of "WebService" should have property "SecurityGroups" with value "WebServiceLBSecurityGroup66424F28"

  Scenario: Infrastructure plumbing constructs are excluded from the output
    Given a CDK output directory at fixture "single-stack"
    When I run the CDK merger on that directory
    Then the result should not contain a construct with id "CDKMetadata"
    And the result should not contain a construct with id "BootstrapVersion"
    And the result should not contain a construct with id "CheckBootstrapVersion"
    And the result should not contain a construct with fqn "aws-cdk-lib.CfnParameter"
    And the result should not contain a construct with fqn "aws-cdk-lib.CfnOutput"
    And the result should not contain a construct whose fqn contains "CustomResourceProvider"

  Scenario: Each CF resource logicalId appears in exactly one construct
    Given a CDK output directory at fixture "single-stack"
    When I run the CDK merger on that directory
    Then no CF resource logicalId should appear in more than one construct

  Scenario: Merge a multi-stack CDK output into a single CDKDeploymentModel
    Given a CDK output directory at fixture "multi-stack"
    When I run the CDK merger on that directory
    Then the result should contain 2 stacks
    And the result should contain a stack named "StackA"
    And the result should contain a stack named "StackB"
    And all constructs from stack "StackA" should have stackName "StackA"
    And all constructs from stack "StackB" should have stackName "StackB"
    And the result should contain a construct with id "BucketA" and fqn "aws-cdk-lib.aws_s3.Bucket"
    And the result should contain a construct with id "QueueB" and fqn "aws-cdk-lib.aws_sqs.Queue"

  Scenario: Missing template file raises an IOException
    Given a CDK output directory at fixture "missing-template"
    When I run the CDK merger on that directory
    Then the merger should throw an IOException

  Scenario: Missing tree.json raises an IOException
    Given a CDK output directory at fixture "missing-tree"
    When I run the CDK merger on that directory
    Then the merger should throw an IOException

  Scenario: Missing manifest.json raises an IOException
    Given a CDK output directory at fixture "missing-manifest"
    When I run the CDK merger on that directory
    Then the merger should throw an IOException

  Scenario: XML output matches the MPS input format
    Given a CDK output directory at fixture "single-stack"
    When I run the CDK merger on that directory
    And I serialise the model to XML
    Then the XML root element should be "model"
    And the XML should contain a stack element "SimpleExampleCdkToCfStack"
    And the XML should contain a construct element with id "Cluster"
    And the XML should contain a construct element with id "Vpc"
