package ust.tad.cdkmpsplugin.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ust.tad.cdkmpsplugin.analysis.cloudformationParser.ReferenceExtractor;

class ReferenceExtractorTest {

  private JsonNode json(String s) throws IOException {
    return new ObjectMapper().readTree(s);
  }

  @Test
  void findsReferenceNestedInFnJoin() throws IOException {
    // AWS::Lambda::Permission SourceArn, as emitted for an API Gateway trigger
    JsonNode n = json("{\"Fn::Join\":[\"\",[\"arn:aws:execute-api:eu-central-1:1:\","
        + "{\"Ref\":\"itemsApi\"},\"/\",{\"Ref\":\"prodStage\"},\"/GET/items\"]]}");
    assertNull(ReferenceExtractor.directTarget(n), "not a lone ref");
    assertEquals(Set.of("itemsApi", "prodStage"), ReferenceExtractor.deepTargets(n));
  }

  @Test
  void findsReferenceInsideArrayOfObjects() throws IOException {
    // ELBv2 Listener DefaultActions, and ECS Service LoadBalancers
    JsonNode n = json("[{\"TargetGroupArn\":{\"Ref\":\"ecsGroup\"},\"Type\":\"forward\"}]");
    assertEquals(Set.of("ecsGroup"), ReferenceExtractor.deepTargets(n));
  }

  @Test
  void findsReferenceDeepInsideNotificationConfiguration() throws IOException {
    JsonNode n = json("{\"LambdaFunctionConfigurations\":[{\"Events\":[\"s3:ObjectCreated:*\"],"
        + "\"LambdaFunctionArn\":{\"Fn::GetAtt\":[\"RekFunction\",\"Arn\"]}}]}");
    assertEquals(Set.of("RekFunction"), ReferenceExtractor.deepTargets(n));
  }

  @Test
  void skipsPseudoParametersButKeepsRealOnes() throws IOException {
    JsonNode n = json("{\"Fn::Join\":[\"\",[\"arn:\",{\"Ref\":\"AWS::Partition\"},"
        + "\":iam::\",{\"Ref\":\"AWS::AccountId\"},\":role/\",{\"Ref\":\"MyRole\"}]]}");
    assertEquals(Set.of("MyRole"), ReferenceExtractor.deepTargets(n));
  }

  @Test
  void directTargetStillHandlesLoneRefAndGetAtt() throws IOException {
    assertEquals("MyTable", ReferenceExtractor.directTarget(json("{\"Ref\":\"MyTable\"}")));
    assertEquals("MyFn",
        ReferenceExtractor.directTarget(json("{\"Fn::GetAtt\":[\"MyFn\",\"Arn\"]}")));
    assertNull(ReferenceExtractor.directTarget(json("\"plain\"")));
  }

  @Test
  void directTargetKeepsPseudoParametersAsBefore() throws IOException {
    assertEquals("AWS::Partition",
        ReferenceExtractor.directTarget(json("{\"Ref\":\"AWS::Partition\"}")));
  }

  @Test
  void plainValuesYieldNothing() throws IOException {
    assertTrue(ReferenceExtractor.deepTargets(json("{\"CidrBlock\":\"10.0.0.0/16\"}")).isEmpty());
    assertTrue(ReferenceExtractor.deepTargets(json("[1,2,3]")).isEmpty());
  }
}
