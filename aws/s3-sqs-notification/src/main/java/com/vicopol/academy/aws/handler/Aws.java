package com.vicopol.academy.aws.handler;

import java.time.Instant;
import java.util.List;

public interface Aws {
    record Notification(
            List<Record> Records) {
    }

    record Record(
            String eventVersion,
            String eventSource,
            String awsRegion,
            Instant eventTime,
            String eventName,
            S3Event s3) {
    }

    record S3Event(
            String s3SchemaVersion,
            String configurationId,
            S3Object object) {
    }

    record S3Object(
            String key,
            Long size,
            String eTag,
            String sequencer) {
    }
}
