package ust.tad.cdkmpsplugin.analysistask;

import java.net.URL;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("url")
    private URL url;

    @JsonProperty("startLineNumber")
    private int startLineNumber;

    @JsonProperty("endLineNumber")
    private int endLineNumber;

    public Location() {}

    public Location(URL url, int startLineNumber, int endLineNumber) {
        this.url = url;
        this.startLineNumber = startLineNumber;
        this.endLineNumber = endLineNumber;
    }

    public UUID getId() { return id; }

    public URL getUrl() { return url; }
    public void setUrl(URL url) { this.url = url; }

    public int getStartLineNumber() { return startLineNumber; }
    public void setStartLineNumber(int startLineNumber) { this.startLineNumber = startLineNumber; }

    public int getEndLineNumber() { return endLineNumber; }
    public void setEndLineNumber(int endLineNumber) { this.endLineNumber = endLineNumber; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location location = (Location) o;
        return Objects.equals(id, location.id) && Objects.equals(url, location.url)
            && startLineNumber == location.startLineNumber
            && endLineNumber == location.endLineNumber;
    }

    @Override
    public int hashCode() { return Objects.hash(id, url, startLineNumber, endLineNumber); }

    @Override
    public String toString() {
        return "{id='" + id + "', url='" + url + "', startLineNumber='" + startLineNumber
            + "', endLineNumber='" + endLineNumber + "'}";
    }
}
