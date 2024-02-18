package com.vicopol.academy.aws;

import com.vicopol.academy.aws.handler.Aws;
import com.vicopol.academy.aws.handler.S3RecordHandler;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

@Component
@RequiredArgsConstructor
class ApplicationListener {
    private final Logger logger = getLogger(ApplicationListener.class);
    private final Supplier<S3RecordHandler> s3RecordHandlerSupplier;

    @SqsListener(queueNames = "${app.sqs.queue-name}")
    void onMessage(Aws.Notification notification) {
        logger.info("Received notification [{}]", notification);

        Optional.ofNullable(notification.Records()).ifPresentOrElse(
                list -> list.forEach(r -> s3RecordHandlerSupplier.get().handle(r)),
                () -> logger.info("No records were found in notification"));
    }
}
