package za.co.capitec.fraud.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Custom deserializer for fields annotated with @JsonRawValue.
 * Handles both raw JSON objects and JSON strings during deserialization.
 */
public class RawJsonDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node.isTextual()) {
            return node.asText();
        }

        // If it's an object or array, convert it back to JSON string
        return node.toString();
    }
}
