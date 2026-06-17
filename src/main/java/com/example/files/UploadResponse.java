package com.example.files;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record UploadResponse(
    UUID fileId,
    long offset,
    long storedSize,
    String status,
    String sha256
) {
}

