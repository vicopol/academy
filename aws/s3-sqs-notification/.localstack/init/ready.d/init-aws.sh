#!/bin/bash

awslocal sqs create-queue \
  --region ca-central-1 \
  --queue-name localstack-queue

SQS_QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --region ca-central-1 \
  --attribute-name QueueArn \
  --queue-url http://localhost:4566/000000000000/localstack-queue \
  | sed 's/"QueueArn"/\n"QueueArn"/g' \
  | grep '"QueueArn"' \
  | awk -F '"QueueArn":' '{print $2}' \
  | tr -d '"' \
  | xargs)

awslocal s3api create-bucket \
  --region ca-central-1 \
  --bucket localstack-bucket \
  --create-bucket-configuration LocationConstraint=ca-central-1

awslocal s3api put-bucket-notification-configuration \
  --region ca-central-1 \
  --bucket localstack-bucket \
  --notification-configuration '{
                                  "QueueConfigurations": [
                                    {
                                      "QueueArn": "'"$SQS_QUEUE_ARN"'",
                                      "Events": ["s3:ObjectCreated:*"]
                                    }
                                  ]
                                }'
