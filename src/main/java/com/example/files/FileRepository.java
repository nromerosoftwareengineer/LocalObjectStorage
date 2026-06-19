package com.example.files;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface FileRepository extends CrudRepository<StoredFile, UUID> {

    @Query("""
        SELECT
            id,
            file_name,
            file_size,
            file_type,
            status,
            sha256,
            COALESCE(octet_length(content), 0) AS stored_size,
            created_at,
            updated_at
        FROM stored_files
        ORDER BY created_at DESC
        """)
    List<StoredFileSummary> listSummaries();
}
