package com.example.files;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadValidatorTest {

    private final UploadValidator uploadValidator = new UploadValidator();

    @Test
    void validateRejectsNegativeOffset() {
        HttpStatusException exception = assertThrows(
            HttpStatusException.class,
            () -> uploadValidator.validate(-1, new byte[0], new byte[0], 10L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("offset must be zero or greater", exception.getMessage());
    }

    @Test
    void validateRejectsGapOffset() {
        HttpStatusException exception = assertThrows(
            HttpStatusException.class,
            () -> uploadValidator.validate(5, "abc".getBytes(StandardCharsets.UTF_8), new byte[0], 10L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("offset cannot skip ahead of currently stored bytes", exception.getMessage());
    }

    @Test
    void validateRejectsUploadThatExceedsDeclaredSize() {
        HttpStatusException exception = assertThrows(
            HttpStatusException.class,
            () -> uploadValidator.validate(
                3,
                "abc".getBytes(StandardCharsets.UTF_8),
                "defghijk".getBytes(StandardCharsets.UTF_8),
                5L
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("upload exceeds the declared fileSize", exception.getMessage());
    }

    @Test
    void validateAllowsUploadWithinDeclaredSize() {
        assertDoesNotThrow(
            () -> uploadValidator.validate(
                3,
                "abc".getBytes(StandardCharsets.UTF_8),
                "de".getBytes(StandardCharsets.UTF_8),
                5L
            )
        );
    }
}
