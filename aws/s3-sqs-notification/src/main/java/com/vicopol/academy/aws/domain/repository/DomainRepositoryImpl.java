package com.vicopol.academy.aws.domain.repository;

import com.vicopol.academy.aws.domain.entity.File;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RequiredArgsConstructor
@Transactional
public class DomainRepositoryImpl implements DomainRepository {
    private static final String SQL_INSERT_FILE = "INSERT INTO file (path, created_at, contents) VALUES (?, ?, ?)";
    private final JdbcClient jdbcClient;

    @Override
    public Long insert(File file) {
        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql(SQL_INSERT_FILE)
                .param(1, file.getPath())
                .param(2, toUTCDateTime(file.getCreatedAt()))
                .param(3, file.getContents())
                .update(generatedKeyHolder, "id");

        return generatedKeyHolder.getKeyAs(Long.class);
    }

    static OffsetDateTime toUTCDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
