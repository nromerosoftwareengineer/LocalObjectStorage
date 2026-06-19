package com.example.files;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;
import java.util.UUID;

@Introspected
public record StoredFileSummary(
    UUID id,
    String fileName,
    Long fileSize,
    String fileType,
    String status,
    String sha256,
    Long storedSize,
    Instant createdAt,
    Instant updatedAt
) {
}
