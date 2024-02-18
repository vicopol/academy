package com.vicopol.academy.aws.domain.entity;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class File {
    Long id;
    int version;
    Instant createdAt;
    String path;
    Long size;
    String contents;
}
