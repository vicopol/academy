package com.vicopol.academy.aws;

import com.vicopol.academy.aws.domain.repository.DomainRepository;
import com.vicopol.academy.aws.domain.repository.DomainRepositoryImpl;
import com.vicopol.academy.aws.handler.S3RecordHandler;
import com.vicopol.academy.aws.handler.S3RecordHandlerImpl;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import javax.sql.DataSource;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(ApplicationConfigProperties.class)
@RequiredArgsConstructor
class ApplicationConfig {
    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }

    @Bean
    Supplier<S3RecordHandler> s3RecordHandlerSupplier(ApplicationConfigProperties applicationConfigProperties,
                                                      ResourceLoader resourceLoader,
                                                      DomainRepository domainRepository) {
        return () -> new S3RecordHandlerImpl(
                applicationConfigProperties.getS3().getBucket(),
                resourceLoader,
                domainRepository);
    }

    @Bean
    DomainRepositoryImpl domainRepository(DataSource dataSource) {
        return new DomainRepositoryImpl(JdbcClient.create(dataSource));
    }
}
