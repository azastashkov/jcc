package io.clawcode.api;

import io.clawcode.core.ContentBlock;
import io.clawcode.core.Usage;

import java.util.List;

public record MessageResponse(
    String id,
    String type,
    String role,
    List<ContentBlock> content,
    String model,
    String stopReason,
    String stopSequence,
    Usage usage,
    String requestId
) {}
