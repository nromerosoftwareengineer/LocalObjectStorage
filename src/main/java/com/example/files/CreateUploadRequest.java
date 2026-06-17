package com.example.files;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Serdeable
public record CreateUploadRequest(
    @NotBlank String fileName,
    @NotNull @PositiveOrZero Long fileSize,
    @NotBlank String fileType
) {
}

