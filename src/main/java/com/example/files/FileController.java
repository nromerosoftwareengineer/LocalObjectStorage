package com.example.files;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Post("/actions/createUpload")
    public HttpResponse<FileMetadataResponse> createUpload(@Valid @Body CreateUploadRequest request) {
        FileMetadataResponse response = fileService.createUpload(request);
        return HttpResponse.status(HttpStatus.ACCEPTED)
            .body(response)
            .header(HttpHeaders.LOCATION, URI.create("/v1/files/" + response.fileId()).toString());
    }

    @Put(value = "/{fileId}", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<UploadResponse> upload(
        UUID fileId,
        @QueryValue(defaultValue = "0") long offset,
        @Body byte[] bytes
    ) {
        return HttpResponse.ok(fileService.upload(fileId, offset, bytes));
    }

    @Get("/{fileId}")
    public HttpResponse<byte[]> download(UUID fileId, @QueryValue(defaultValue = "0") long offset) {
        FileDownload download = fileService.download(fileId, offset);
        return HttpResponse.ok(download.bytes())
            .header(HttpHeaders.CONTENT_TYPE, download.fileType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
            .header("X-File-Id", download.fileId().toString())
            .header("X-File-Offset", Long.toString(download.offset()))
            .header("X-File-Sha256", download.sha256());
    }

    @Get
    public HttpResponse<List<FileMetadataResponse>> listFiles() {
        return HttpResponse.ok(fileService.listFiles());
    }
}
