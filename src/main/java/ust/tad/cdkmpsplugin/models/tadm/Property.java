package ust.tad.cdkmpsplugin.models.tadm;

import java.util.Objects;

public class Property {

    private String key;
    private PropertyType type;
    private boolean required;
    private Object value;
    private Confidence confidence;

    private static final String INVALID_MSG =
        "The value '%s' with type '%s' of the property does not match the given type %s";

    public Property() {}

    public Property(String key, PropertyType type, boolean required, Object value, Confidence confidence)
            throws InvalidPropertyValueException {
        if (!isValueMatchingType(type, value)) {
            throw new InvalidPropertyValueException(
                String.format(INVALID_MSG, value, value.getClass(), type));
        }
        this.key = key;
        this.type = type;
        this.required = required;
        this.value = value;
        this.confidence = confidence;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public PropertyType getType() { return type; }
    public void setType(PropertyType type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    private boolean isValueMatchingType(PropertyType type, Object value) {
        if (value == null || type == null) return true;
        switch (type) {
            case BOOLEAN: return value instanceof Boolean;
            case DOUBLE:  return value instanceof Double || value instanceof Float;
            case INTEGER: return value instanceof Integer || value instanceof Long;
            case STRING:  return value instanceof String;
            default:      return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        Property p = (Property) o;
        return required == p.required
            && Objects.equals(key, p.key)
            && Objects.equals(type, p.type)
            && Objects.equals(value, p.value)
            && Objects.equals(confidence, p.confidence);
    }

    @Override
    public int hashCode() { return Objects.hash(key, type, required, value, confidence); }

    @Override
    public String toString() {
        return "{key='" + key + "', type='" + type + "', required=" + required
            + ", value='" + value + "', confidence='" + confidence + "'}";
    }
}
