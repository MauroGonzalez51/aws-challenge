package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.pragma.utils.ApiResponse;
import com.pragma.utils.DynamoDBClientProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

public class GetUserHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb = DynamoDBClientProvider.getClient();
    private final Gson gson = new Gson();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        Map<String, String> pathParams = event.getPathParameters();

        if (pathParams == null || !pathParams.containsKey("userId")) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "userId path parameter is required")));
        }

        String userId = pathParams.get("userId");
        String tableName = System.getenv("USERS_TABLE");

        try {
            GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(userId).build()))
                    .build());

            if (!response.hasItem() || response.item().isEmpty()) {
                return ApiResponse.notFound(gson.toJson(Map.of(
                        "error", "could not find user with provided 'userId': " + userId
                )));
            }

            Map<String, AttributeValue> item = response.item();
            Map<String, String> user = Map.of(
                    "id", item.get("id").s(),
                    "name", item.get("name").s(),
                    "email", item.get("email").s()
            );

            return ApiResponse.ok(gson.toJson(user));
        } catch (Exception e) {
            context.getLogger().log("Error getting user: " + e.getMessage());
            return ApiResponse.error(gson.toJson(Map.of(
                    "error", "could not find user with provided 'userId': " + userId
            )));
        }
    }
}
