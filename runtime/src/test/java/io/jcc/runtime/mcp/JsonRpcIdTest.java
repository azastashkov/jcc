package io.jcc.runtime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jcc.core.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcIdTest {

    private final ObjectMapper mapper = JsonMapper.shared();

    @Test
    void parsesNumericId() throws Exception {
        JsonRpcId id = mapper.readValue("42", JsonRpcId.class);
        assertThat(id).isEqualTo(new JsonRpcId.Num(42));
        assertThat(mapper.writeValueAsString(id)).isEqualTo("42");
    }

    @Test
    void parsesStringId() throws Exception {
        JsonRpcId id = mapper.readValue("\"abc\"", JsonRpcId.class);
        assertThat(id).isEqualTo(new JsonRpcId.Str("abc"));
        assertThat(mapper.writeValueAsString(id)).isEqualTo("\"abc\"");
    }

    @Test
    void parsesNullId() throws Exception {
        JsonRpcId id = mapper.readValue("null", JsonRpcId.class);
        assertThat(id).isEqualTo(JsonRpcId.Null.INSTANCE);
        assertThat(mapper.writeValueAsString(id)).isEqualTo("null");
    }
}
