package com.example.files;

import java.util.UUID;

public record FileDownload(
    UUID fileId,
    String fileName,
    String fileType,
    long offset,
    String sha256,
    byte[] bytes
) {
}

