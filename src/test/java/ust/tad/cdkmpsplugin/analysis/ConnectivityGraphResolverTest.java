package ust.tad.cdkmpsplugin.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.ConnectivityGraphResolver;
import ust.tad.cdkmpsplugin.cdkmodel.CDKConstruct;
import ust.tad.cdkmpsplugin.cdkmodel.CDKDeploymentModel;
import ust.tad.cdkmpsplugin.cdkmodel.CFProperty;
import ust.tad.cdkmpsplugin.cdkmodel.CFResource;

class ConnectivityGraphResolverTest {

  @Test
  void apiReachesLambdaThroughPermissionAndIgnoresTheStage() {
    CFResource api = res("Api", "AWS::ApiGateway::RestApi");
    CFResource stage = res("Stage", "AWS::ApiGateway::Stage");
    CFResource fn = res("Fn", "AWS::Lambda::Function");

    CFResource perm = res("Perm", "AWS::Lambda::Permission");
    // SourceArn is an Fn::Join naming both the API and the stage
    perm.addProperty(prop("SourceArn", "Api", "Stage"));
    perm.addProperty(prop("FunctionName", "Fn"));

    resolve(model(api, stage, fn, perm));

    assertEquals("invoke", connectsTo(api, "Fn"));
    assertNull(connectsTo(stage, "Fn"), "the stage is a hub and must not become a source");
  }

  @Test
  void loadBalancerReachesServiceThroughTargetGroup() {
    CFResource alb = res("Alb", "AWS::ElasticLoadBalancingV2::LoadBalancer");
    CFResource tg = res("Tg", "AWS::ElasticLoadBalancingV2::TargetGroup");
    CFResource svc = res("Svc", "AWS::ECS::Service");

    CFResource listener = res("Listener", "AWS::ElasticLoadBalancingV2::Listener");
    listener.addProperty(prop("LoadBalancerArn", "Alb"));
    listener.addProperty(prop("DefaultActions", "Tg"));
    svc.addProperty(prop("LoadBalancers", "Tg"));

    resolve(model(alb, tg, svc, listener));

    assertEquals("invoke", connectsTo(alb, "Svc"),
        "edge must contract through the target group");
    assertNull(connectsTo(alb, "Tg"), "the target group is wiring, not an endpoint");
  }

  @Test
  void bucketReachesFunctionThroughNotificationResource() {
    CFResource bucket = res("Bucket", "AWS::S3::Bucket");
    CFResource fn = res("Fn", "AWS::Lambda::Function");
    CFResource notif = res("Notif", "Custom::S3BucketNotifications");
    notif.addProperty(prop("BucketName", "Bucket"));
    notif.addProperty(prop("NotificationConfiguration", "Fn"));

    resolve(model(bucket, fn, notif));
    assertEquals("invoke", connectsTo(bucket, "Fn"));
  }

  @Test
  void doesNotOverwriteAnExistingIamDerivedLevel() {
    CFResource api = res("Api", "AWS::ApiGateway::RestApi");
    CFResource fn = res("Fn", "AWS::Lambda::Function");
    api.addProperty(new CFProperty("ConnectsTo", "readwrite", "Fn"));

    CFResource perm = res("Perm", "AWS::Lambda::Permission");
    perm.addProperty(prop("SourceArn", "Api"));
    perm.addProperty(prop("FunctionName", "Fn"));

    resolve(model(api, fn, perm));

    long edges = api.getProperties().stream()
        .filter(p -> "ConnectsTo".equals(p.getKey()) && "Fn".equals(p.getReferenceTarget()))
        .count();
    assertEquals(1, edges, "must not duplicate the edge");
    assertEquals("readwrite", connectsTo(api, "Fn"), "stronger IAM level must survive");
  }

  @Test
  void unknownWiringTypeIsTreatedAsAComponentSoNoEdgeIsInvented() {
    CFResource a = res("A", "AWS::S3::Bucket");
    CFResource b = res("B", "AWS::Lambda::Function");
    CFResource unknown = res("U", "AWS::Some::FutureConnector");
    unknown.addProperty(prop("SourceArn", "A"));
    unknown.addProperty(prop("FunctionName", "B"));

    resolve(model(a, b, unknown));
    assertNull(connectsTo(a, "B"), "unlisted types are not routed through");
  }

  @Test
  void followsADestinationFieldWhenNoRoleIsAssumed() {
    // An IoT rule writing to Kafka names the cluster in a destination field and assumes no role.
    CFResource cluster = res("Cluster", "AWS::MSK::Cluster");
    CFResource rule = res("Rule", "AWS::IoT::TopicRule");
    CFProperty payload =
        new CFProperty(
            "TopicRulePayload",
            "{\"Actions\":[{\"Kafka\":{\"DestinationArn\":{\"Fn::GetAtt\":[\"Cluster\",\"Arn\"]},"
                + "\"Topic\":\"telemetry\"}}]}",
            null);
    rule.addProperty(payload);

    resolve(model(cluster, rule));
    assertEquals("invoke", connectsTo(rule, "Cluster"));
  }

  @Test
  void ignoresReferencesThatAreNotDestinations() {
    CFResource cluster = res("Cluster", "AWS::MSK::Cluster");
    CFResource rule = res("Rule", "AWS::IoT::TopicRule");
    rule.addProperty(
        new CFProperty(
            "TopicRulePayload",
            "{\"Actions\":[{\"Kafka\":{\"SomeOtherField\":{\"Ref\":\"Cluster\"}}}]}",
            null));

    resolve(model(cluster, rule));
    assertNull(connectsTo(rule, "Cluster"), "only destination fields are followed");
  }

  @Test
  void distributionReachesItsOriginBucket() {
    // Origin access control grants CloudFront through a bucket policy, so no role is assumed and
    // the bucket is named only by the origin domain.
    CFResource bucket = res("Bucket", "AWS::S3::Bucket");
    CFResource distribution = res("Dist", "AWS::CloudFront::Distribution");
    distribution.addProperty(
        new CFProperty(
            "DistributionConfig",
            "{\"Origins\":[{\"DomainName\":{\"Fn::GetAtt\":[\"Bucket\",\"RegionalDomainName\"]},"
                + "\"Id\":\"origin1\"}]}",
            null));

    resolve(model(bucket, distribution));
    assertEquals("invoke", connectsTo(distribution, "Bucket"));
  }

  @Test
  void apiReachesTheUserPoolThroughItsAuthorizer() {
    CFResource api = res("Api", "AWS::ApiGatewayV2::Api");
    CFResource pool = res("Pool", "AWS::Cognito::UserPool");
    CFResource authorizer = res("Auth", "AWS::ApiGatewayV2::Authorizer");
    authorizer.addProperty(prop("ApiId", "Api"));
    authorizer.addProperty(prop("JwtConfiguration", "Pool"));

    resolve(model(api, pool, authorizer));

    assertEquals("invoke", connectsTo(api, "Pool"));
    assertNull(connectsTo(pool, "Api"), "the pool is the called end, not the caller");
  }

  @Test
  void alarmCarriesTheEdgeFromItsMetricSourceToItsAction() {
    CFResource queue = res("Dlq", "AWS::SQS::Queue");
    CFResource topic = res("Topic", "AWS::SNS::Topic");
    CFResource alarm = res("Alarm", "AWS::CloudWatch::Alarm");
    alarm.addProperty(prop("Dimensions", "Dlq"));
    alarm.addProperty(prop("AlarmActions", "Topic"));

    resolve(model(queue, topic, alarm));

    assertEquals("invoke", connectsTo(queue, "Topic"));
    assertTrue(
        alarm.getProperties().stream().noneMatch(p -> "ConnectsTo".equals(p.getKey())),
        "the alarm is wiring and must not become an endpoint");
  }

  private static void resolve(CDKDeploymentModel m) {
    new ConnectivityGraphResolver().resolve(m);
  }

  private static String connectsTo(CFResource from, String target) {
    for (CFProperty p : from.getProperties()) {
      if ("ConnectsTo".equals(p.getKey()) && target.equals(p.getReferenceTarget())) {
        return p.getValue();
      }
    }
    return null;
  }

  private static CFProperty prop(String key, String... nested) {
    CFProperty p = new CFProperty(key, "{}", null);
    p.setNestedTargets(new LinkedHashSet<>(List.of(nested)));
    return p;
  }

  private static CFResource res(String id, String type) {
    return new CFResource(id, type, new LinkedHashSet<>());
  }

  private static CDKDeploymentModel model(CFResource... rs) {
    Set<CFResource> set = new LinkedHashSet<>(List.of(rs));
    CDKDeploymentModel m = new CDKDeploymentModel();
    m.addConstruct(new CDKConstruct("c", "fqn", "Stack", "path", set));
    return m;
  }
}
