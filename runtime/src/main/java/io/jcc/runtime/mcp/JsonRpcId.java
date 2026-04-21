package io.jcc.runtime.mcp;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;

@JsonDeserialize(using = JsonRpcId.Deserializer.class)
@JsonSerialize(using = JsonRpcId.Serializer.class)
public sealed interface JsonRpcId {

    record Num(long value) implements JsonRpcId {}

    record Str(String value) implements JsonRpcId {}

    record Null() implements JsonRpcId {
        public static final Null INSTANCE = new Null();
    }

    static JsonRpcId of(long value) {
        return new Num(value);
    }

    static JsonRpcId of(String value) {
        return new Str(value);
    }

    final class Deserializer extends JsonDeserializer<JsonRpcId> {
        @Override
        public JsonRpcId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            return switch (token) {
                case VALUE_NUMBER_INT -> new Num(p.getLongValue());
                case VALUE_STRING -> new Str(p.getText());
                case VALUE_NULL -> Null.INSTANCE;
                default -> throw ctxt.wrongTokenException(p, JsonRpcId.class, token,
                    "Expected id as number, string, or null");
            };
        }

        @Override
        public JsonRpcId getNullValue(DeserializationContext ctxt) {
            return Null.INSTANCE;
        }
    }

    final class Serializer extends JsonSerializer<JsonRpcId> {
        @Override
        public void serialize(JsonRpcId value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
            switch (value) {
                case Num n -> gen.writeNumber(n.value);
                case Str s -> gen.writeString(s.value);
                case Null n -> gen.writeNull();
            }
        }
    }
}
