package com.vicopol.academy.aws;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
class ApplicationConfigProperties {
    private S3ConfigProperties s3 = new S3ConfigProperties();
    private SqsConfigProperties sqs = new SqsConfigProperties();

    @Getter
    @Setter
    static class S3ConfigProperties {
        private String bucket;
    }

    @Getter
    @Setter
    static class SqsConfigProperties {
        private String queueName;
    }
}
