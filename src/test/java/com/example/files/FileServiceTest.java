package com.example.files;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileService fileService;

    @Test
    void createUploadInitializesPendingFile() {
        when(fileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.createUpload(
            new CreateUploadRequest("hello.txt", 11L, "text/plain")
        );

        assertEquals("hello.txt", response.fileName());
        assertEquals(11L, response.fileSize());
        assertEquals("text/plain", response.fileType());
        assertEquals("PENDING", response.status());
        assertEquals(0L, response.storedSize());
    }

    @Test
    void uploadAppendsBytesAndCalculatesHash() {
        UUID fileId = UUID.randomUUID();
        StoredFile storedFile = storedFile(fileId, "hello ".getBytes(StandardCharsets.UTF_8), 11L);
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(storedFile));
        when(fileRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = fileService.upload(
            fileId,
            6,
            "world".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(fileId, response.fileId());
        assertEquals(11L, response.storedSize());
        assertEquals("COMPLETE", response.status());
        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            response.sha256()
        );
    }

    @Test
    void uploadRejectsGapOffset() {
        UUID fileId = UUID.randomUUID();
        StoredFile storedFile = storedFile(fileId, "abc".getBytes(StandardCharsets.UTF_8), 10L);
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(storedFile));

        HttpStatusException exception = assertThrows(
            HttpStatusException.class,
            () -> fileService.upload(fileId, 5, "more".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void downloadReturnsSliceFromOffset() {
        UUID fileId = UUID.randomUUID();
        StoredFile storedFile = storedFile(fileId, "abcdef".getBytes(StandardCharsets.UTF_8), 6L);
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(storedFile));

        FileDownload download = fileService.download(fileId, 2);

        assertArrayEquals("cdef".getBytes(StandardCharsets.UTF_8), download.bytes());
    }

    @Test
    void listFilesMapsRepositoryProjection() {
        StoredFileSummary summary = new StoredFileSummary(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "report.pdf",
            1024L,
            "application/pdf",
            "IN_PROGRESS",
            "abc123",
            800L,
            Instant.parse("2026-06-17T05:00:00Z"),
            Instant.parse("2026-06-17T05:10:00Z")
        );
        when(fileRepository.listSummaries()).thenReturn(List.of(summary));

        List<FileMetadataResponse> responses = fileService.listFiles();

        assertEquals(1, responses.size());
        assertEquals("report.pdf", responses.get(0).fileName());
        assertEquals(800L, responses.get(0).storedSize());
    }

    private StoredFile storedFile(UUID fileId, byte[] content, long fileSize) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        storedFile.setFileName("test.bin");
        storedFile.setFileSize(fileSize);
        storedFile.setFileType("application/octet-stream");
        storedFile.setStatus("PENDING");
        storedFile.setSha256("seed");
        storedFile.setContent(content);
        storedFile.setCreatedAt(Instant.now());
        storedFile.setUpdatedAt(Instant.now());
        return storedFile;
    }
}
