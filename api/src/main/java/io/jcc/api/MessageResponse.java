package io.jcc.api;

import io.jcc.core.ContentBlock;
import io.jcc.core.Usage;

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
