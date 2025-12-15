package com.vicopol.academy.aws;

import com.jayway.jsonpath.JsonPath;
import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.readString;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
@SpringBootTest
@Slf4j
class ApplicationTest {
    static final Path TEST_RESOURCES = Paths.get("src", "test", "resources");
    static final Duration AWAIT_POLL_INTERVAL = ofMillis(50);
    static final Duration AWAIT_TIMEOUT = ofSeconds(60);
    static final String S3_BUCKET_NAME = UUID.randomUUID().toString();
    static final String SQS_QUEUE_NAME = UUID.randomUUID().toString();
    @Container
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.3"));
    @Container
    static LocalStackContainer localStackContainer =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.12"))
                    .withEnv("LOCALSTACK_HOST", "localhost.localstack.cloud")
                    .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS);
    static String queueUrl;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        dynamicPropertyRegistry.add("spring.flyway.user", postgreSQLContainer::getUsername);
        dynamicPropertyRegistry.add("spring.flyway.password", postgreSQLContainer::getPassword);

        dynamicPropertyRegistry.add("spring.cloud.aws.credentials.access-key", localStackContainer::getAccessKey);
        dynamicPropertyRegistry.add("spring.cloud.aws.credentials.secret-key", localStackContainer::getSecretKey);
        dynamicPropertyRegistry.add("spring.cloud.aws.region.static", localStackContainer::getRegion);
        dynamicPropertyRegistry.add("spring.cloud.aws.s3.endpoint",
                () -> localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3));
        dynamicPropertyRegistry.add("spring.cloud.aws.sqs.endpoint",
                () -> localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS));

        dynamicPropertyRegistry.add("app.s3.bucket", () -> S3_BUCKET_NAME);
        dynamicPropertyRegistry.add("app.sqs.queue-name", () -> SQS_QUEUE_NAME);
    }

    @BeforeAll
    static void prepareAws() throws Exception {
        org.testcontainers.containers.Container.ExecResult execResult =
                localStackContainer.execInContainer("awslocal", "sqs", "create-queue",
                        "--queue-name", SQS_QUEUE_NAME);

        log.info("SQS create-queue command result: [{}]", execResult);

        queueUrl = JsonPath.read(execResult.getStdout(), "$.QueueUrl");

        execResult = localStackContainer.execInContainer("awslocal", "sqs", "get-queue-attributes",
                "--attribute-name", "QueueArn",
                "--queue-url", queueUrl);

        log.info("SQS get-queue-attributes command result: [{}]", execResult);

        String queueArn = JsonPath.read(execResult.getStdout(), "$.Attributes.QueueArn");

        execResult = localStackContainer.execInContainer("awslocal", "s3api", "create-bucket",
                "--bucket", S3_BUCKET_NAME);

        log.info("S3 create-bucket command result: [{}]", execResult);

        execResult = localStackContainer.execInContainer("awslocal", "s3api", "put-bucket-notification-configuration",
                "--bucket", S3_BUCKET_NAME,
                "--notification-configuration", String.format("""
                        {
                            "QueueConfigurations": [
                                {
                                    "QueueArn": "%s",
                                    "Events": ["s3:ObjectCreated:*"]
                                }
                            ]
                        }""", queueArn));

        log.info("S3 put-bucket-notification-configuration command result: [{}]", execResult);
    }

    @Autowired
    S3Template s3Template;
    @Autowired
    JdbcClient jdbcClient;

    @Test
    void shouldHandle() throws Exception {
        String key = "transactions.csv";
        Path path = TEST_RESOURCES.resolve(key);
        String contents = readString(path);

        uploadToS3(s3Template, key, path);

        await()
                .pollInterval(AWAIT_POLL_INTERVAL)
                .atMost(AWAIT_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    assertEmptyQueue();
                    assertSaved(contents, key);
                });
    }

    void assertSaved(String contents, String path) {
        List<Map<String, Object>> rows = jdbcClient
                .sql("SELECT * FROM file WHERE path LIKE ?")
                .param(1, "%" + path + "%")
                .query()
                .listOfRows();

        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals(contents, rows.getFirst().get("contents"));
    }

    static void assertEmptyQueue() {
        String peekIntoQueueUrl = localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS).toString() +
                "/_aws/sqs/messages?QueueUrl=" + queueUrl;

        String response = RestClient.create()
                .get()
                .uri(URI.create(peekIntoQueueUrl))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        Object messages = JsonPath.read(response, "$.ReceiveMessageResponse.ReceiveMessageResult");
        assertNull(messages);
    }

    static void uploadToS3(S3Template s3Template, String key, Path file) throws IOException {
        try (InputStream is = newInputStream(file)) {
            s3Template.upload(S3_BUCKET_NAME, key, is);
        }
    }
}
