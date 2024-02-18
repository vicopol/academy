package com.vicopol.academy.aws.handler;

public interface S3RecordHandler {
    void handle(Aws.Record record);
}
