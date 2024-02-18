package com.vicopol.academy.aws.handler;

import com.vicopol.academy.aws.domain.entity.File;
import com.vicopol.academy.aws.domain.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

@RequiredArgsConstructor
public class S3RecordHandlerImpl implements S3RecordHandler {
    private final Logger logger = getLogger(S3RecordHandlerImpl.class);
    private final String bucket;
    private final ResourceLoader resourceLoader;
    private final DomainRepository domainRepository;

    @Override
    public void handle(Aws.Record record) {
        logger.info("Handling S3 record [{}]", record);

        String key = record.s3().object().key();
        String location = location(bucket, key);

        getResourceContents(location)
                .ifPresentOrElse(str -> saveFile(location, record.eventTime(), record.s3().object().size(), str),
                        () -> logger.warn("No resource content"));
    }

    private void saveFile(String location, Instant createdAt, Long size, String contents) {
        File file = File.builder()
                .path(location)
                .createdAt(createdAt)
                .size(size)
                .contents(contents)
                .build();
        domainRepository.insert(file);
    }

    private Optional<String> getResourceContents(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            logger.warn("S3 resource [{}] does not exist", resource);

            return Optional.empty();
        }

        try {
            return Optional.of(resource.getContentAsString(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Cannot read resource contents from location [" + location + "]", e);
        }
    }

    static String location(String bucket, String key) {
        return "s3://" + bucket + "/" + key;
    }
}
