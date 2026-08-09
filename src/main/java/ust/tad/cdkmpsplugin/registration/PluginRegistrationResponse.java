package ust.tad.cdkmpsplugin.registration;

import java.util.Objects;

public class PluginRegistrationResponse {

    private String requestQueueName;
    private String responseExchangeName;

    public PluginRegistrationResponse() {}

    public String getRequestQueueName() { return requestQueueName; }
    public void setRequestQueueName(String requestQueueName) {
        this.requestQueueName = requestQueueName;
    }

    public String getResponseExchangeName() { return responseExchangeName; }
    public void setResponseExchangeName(String responseExchangeName) {
        this.responseExchangeName = responseExchangeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginRegistrationResponse)) return false;
        PluginRegistrationResponse r = (PluginRegistrationResponse) o;
        return Objects.equals(requestQueueName, r.requestQueueName)
            && Objects.equals(responseExchangeName, r.responseExchangeName);
    }

    @Override
    public int hashCode() { return Objects.hash(requestQueueName, responseExchangeName); }

    @Override
    public String toString() {
        return "{requestQueueName='" + requestQueueName
            + "', responseExchangeName='" + responseExchangeName + "'}";
    }
}
