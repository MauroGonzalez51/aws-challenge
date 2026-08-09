package com.pragma.utils;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

public class DynamoDBClientProvider {

    private static DynamoDbClient client;

    private DynamoDBClientProvider() {
    }

    public static DynamoDbClient getClient() {
        if (client == null) {
            String isOffline = System.getenv("IS_OFFLINE");

            if ("true".equals(isOffline)) {
                client = DynamoDbClient.builder()
                        .endpointOverride(URI.create("http://localhost:8000"))
                        .build();

                return client;
            }

            client = DynamoDbClient.create();
            return client;
        }

        return client;
    }
}
