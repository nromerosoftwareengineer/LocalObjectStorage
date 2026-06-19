package com.example.files;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

final class UploadValidator {

    void validate(long offset, byte[] existing, byte[] incoming, Long declaredSize) {
        if (offset < 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "offset must be zero or greater");
        }

        if (offset > existing.length) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "offset cannot skip ahead of currently stored bytes"
            );
        }

        long maxFileSize = declaredSize == null ? Long.MAX_VALUE : declaredSize;
        long newLogicalSize = Math.max(existing.length, offset + incoming.length);
        if (newLogicalSize > maxFileSize) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "upload exceeds the declared fileSize"
            );
        }
    }
}
