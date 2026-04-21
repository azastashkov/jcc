package io.clawcode.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageTest {

    private final ObjectMapper mapper = JsonMapper.build();

    @Test
    void totalIncludesCacheTokens() {
        Usage usage = new Usage(10, 2, 3, 4);
        assertThat(usage.totalTokens()).isEqualTo(19);
    }

    @Test
    void plusAccumulates() {
        Usage a = new Usage(1, 2, 3, 4);
        Usage b = new Usage(10, 20, 30, 40);
        assertThat(a.plus(b)).isEqualTo(new Usage(11, 22, 33, 44));
    }

    @Test
    void deserializesSnakeCaseFromWire() throws Exception {
        String json = """
            {
              "input_tokens": 100,
              "cache_creation_input_tokens": 10,
              "cache_read_input_tokens": 20,
              "output_tokens": 50
            }
            """;
        Usage usage = mapper.readValue(json, Usage.class);
        assertThat(usage).isEqualTo(new Usage(100, 10, 20, 50));
    }

    @Test
    void deserializesPartialPayload() throws Exception {
        String json = """
            { "input_tokens": 5, "output_tokens": 2 }
            """;
        Usage usage = mapper.readValue(json, Usage.class);
        assertThat(usage).isEqualTo(new Usage(5, 0, 0, 2));
    }
}
