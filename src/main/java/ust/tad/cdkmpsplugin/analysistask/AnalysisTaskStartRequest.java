package ust.tad.cdkmpsplugin.analysistask;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisTaskStartRequest {

    @JsonProperty("taskId")
    private UUID taskId;

    @JsonProperty("transformationProcessId")
    private UUID transformationProcessId;

    @JsonProperty("commands")
    private List<String> commands;

    @JsonProperty("locations")
    private List<Location> locations;

    public AnalysisTaskStartRequest() {}

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public UUID getTransformationProcessId() { return transformationProcessId; }
    public void setTransformationProcessId(UUID transformationProcessId) {
        this.transformationProcessId = transformationProcessId;
    }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public List<Location> getLocations() { return locations; }
    public void setLocations(List<Location> locations) { this.locations = locations; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalysisTaskStartRequest)) return false;
        AnalysisTaskStartRequest r = (AnalysisTaskStartRequest) o;
        return Objects.equals(taskId, r.taskId)
            && Objects.equals(transformationProcessId, r.transformationProcessId)
            && Objects.equals(commands, r.commands)
            && Objects.equals(locations, r.locations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, transformationProcessId, commands, locations);
    }

    @Override
    public String toString() {
        return "{taskId='" + taskId + "', transformationProcessId='" + transformationProcessId
            + "', commands='" + commands + "', locations='" + locations + "'}";
    }
}
