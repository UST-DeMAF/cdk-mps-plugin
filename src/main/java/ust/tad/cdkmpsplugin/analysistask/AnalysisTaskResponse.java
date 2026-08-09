package ust.tad.cdkmpsplugin.analysistask;

import java.util.Objects;
import java.util.UUID;

public class AnalysisTaskResponse {

    private UUID taskId;
    private boolean success;
    private String errorMessage;

    public AnalysisTaskResponse() {}

    public AnalysisTaskResponse(UUID taskId, boolean success, String errorMessage) {
        this.taskId = taskId;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalysisTaskResponse)) return false;
        AnalysisTaskResponse r = (AnalysisTaskResponse) o;
        return Objects.equals(taskId, r.taskId) && success == r.success
            && Objects.equals(errorMessage, r.errorMessage);
    }

    @Override
    public int hashCode() { return Objects.hash(taskId, success, errorMessage); }

    @Override
    public String toString() {
        return "{taskId='" + taskId + "', success='" + success
            + "', errorMessage='" + errorMessage + "'}";
    }
}
