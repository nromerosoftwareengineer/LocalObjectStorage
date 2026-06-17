package com.example.files;

import java.time.Instant;
import java.util.UUID;

public interface StoredFileSummaryView {
    UUID getId();
    String getFileName();
    Long getFileSize();
    String getFileType();
    String getStatus();
    String getSha256();
    Long getStoredSize();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}

