#!/bin/bash

echo "Initializing DynamoDB tables..."

until awslocal dynamodb list-tables > /dev/null 2>&1; do
    echo "Waiting for DynamoDB to be ready..."
    sleep 2
done

awslocal dynamodb create-table \
    --table-name AuditLogs \
    --attribute-definitions \
        AttributeName=pk,AttributeType=S \
        AttributeName=sk,AttributeType=S \
        AttributeName=entityId,AttributeType=S \
    --key-schema \
        AttributeName=pk,KeyType=HASH \
        AttributeName=sk,KeyType=RANGE \
    --global-secondary-indexes \
        '[{
            "IndexName": "entityId-index",
            "KeySchema": [{"AttributeName": "entityId", "KeyType": "HASH"}],
            "Projection": {"ProjectionType": "ALL"}
        }]' \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

echo "DynamoDB tables created successfully!"

awslocal dynamodb list-tables
