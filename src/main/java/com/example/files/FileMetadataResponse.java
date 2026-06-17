package com.example.files;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record FileMetadataResponse(
    UUID fileId,
    String fileName,
    long fileSize,
    String fileType,
    long storedSize,
    String status,
    String sha256,
    Instant createdAt,
    Instant updatedAt
) {
}

