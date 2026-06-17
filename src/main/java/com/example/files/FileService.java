package com.example.files;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Singleton
public class FileService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETE = "COMPLETE";

    private final FileRepository fileRepository;

    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Transactional
    public FileMetadataResponse createUpload(CreateUploadRequest request) {
        Instant now = Instant.now();
        StoredFile storedFile = new StoredFile();
        storedFile.setId(UUID.randomUUID());
        storedFile.setFileName(request.fileName());
        storedFile.setFileSize(request.fileSize());
        storedFile.setFileType(request.fileType());
        storedFile.setStatus(STATUS_PENDING);
        storedFile.setSha256(sha256Hex(new byte[0]));
        storedFile.setContent(new byte[0]);
        storedFile.setCreatedAt(now);
        storedFile.setUpdatedAt(now);

        fileRepository.save(storedFile);
        return toMetadataResponse(storedFile);
    }

    @Transactional
    public UploadResponse upload(UUID fileId, long offset, byte[] chunk) {
        if (offset < 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "offset must be zero or greater");
        }

        StoredFile storedFile = getRequiredFile(fileId);
        byte[] existing = storedFile.getContent() == null ? new byte[0] : storedFile.getContent();
        byte[] incoming = chunk == null ? new byte[0] : chunk;

        if (offset > existing.length) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "offset cannot skip ahead of currently stored bytes"
            );
        }

        long declaredSize = storedFile.getFileSize() == null ? Long.MAX_VALUE : storedFile.getFileSize();
        long newLogicalSize = Math.max(existing.length, offset + incoming.length);
        if (newLogicalSize > declaredSize) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "upload exceeds the declared fileSize"
            );
        }

        byte[] merged = merge(existing, incoming, (int) offset);
        storedFile.setContent(merged);
        storedFile.setSha256(sha256Hex(merged));
        storedFile.setStatus(merged.length == storedFile.getFileSize() ? STATUS_COMPLETE : STATUS_IN_PROGRESS);
        storedFile.setUpdatedAt(Instant.now());

        fileRepository.update(storedFile);
        return new UploadResponse(
            storedFile.getId(),
            offset,
            merged.length,
            storedFile.getStatus(),
            storedFile.getSha256()
        );
    }

    @Transactional
    public FileDownload download(UUID fileId, long offset) {
        if (offset < 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "offset must be zero or greater");
        }

        StoredFile storedFile = getRequiredFile(fileId);
        byte[] bytes = storedFile.getContent() == null ? new byte[0] : storedFile.getContent();
        if (offset > bytes.length) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "offset cannot be greater than the number of stored bytes"
            );
        }

        byte[] slice = Arrays.copyOfRange(bytes, (int) offset, bytes.length);
        return new FileDownload(
            storedFile.getId(),
            storedFile.getFileName(),
            storedFile.getFileType(),
            offset,
            storedFile.getSha256(),
            slice
        );
    }

    @Transactional
    public List<FileMetadataResponse> listFiles() {
        return fileRepository.listSummaries()
            .stream()
            .map(summary -> new FileMetadataResponse(
                summary.getId(),
                summary.getFileName(),
                summary.getFileSize(),
                summary.getFileType(),
                summary.getStoredSize(),
                summary.getStatus(),
                summary.getSha256(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
            ))
            .toList();
    }

    private StoredFile getRequiredFile(UUID fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "fileId not found"));
    }

    private FileMetadataResponse toMetadataResponse(StoredFile storedFile) {
        return new FileMetadataResponse(
            storedFile.getId(),
            storedFile.getFileName(),
            storedFile.getFileSize(),
            storedFile.getFileType(),
            storedFile.getContent() == null ? 0 : storedFile.getContent().length,
            storedFile.getStatus(),
            storedFile.getSha256(),
            storedFile.getCreatedAt(),
            storedFile.getUpdatedAt()
        );
    }

    private byte[] merge(byte[] existing, byte[] incoming, int offset) {
        int mergedLength = Math.max(existing.length, offset + incoming.length);
        byte[] merged = Arrays.copyOf(existing, mergedLength);
        System.arraycopy(incoming, 0, merged, offset, incoming.length);
        return merged;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

