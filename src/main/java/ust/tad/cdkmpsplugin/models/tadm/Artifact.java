package ust.tad.cdkmpsplugin.models.tadm;

import java.net.URI;
import java.util.Objects;

public class Artifact {

    private String name;
    private String type;
    private URI fileUri;
    private Confidence confidence;

    public Artifact() {}

    public Artifact(String name, String type, URI fileUri, Confidence confidence) {
        this.name = name;
        this.type = type;
        this.fileUri = fileUri;
        this.confidence = confidence;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public URI getFileUri() { return fileUri; }
    public void setFileUri(URI fileUri) { this.fileUri = fileUri; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artifact)) return false;
        Artifact a = (Artifact) o;
        return Objects.equals(name, a.name) && Objects.equals(type, a.type)
            && Objects.equals(fileUri, a.fileUri) && Objects.equals(confidence, a.confidence);
    }

    @Override
    public int hashCode() { return Objects.hash(name, type, fileUri, confidence); }
}
