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
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

public class DeleteUserHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

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
            // Check if user exists
            GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(userId).build()))
                    .build());

            if (!response.hasItem() || response.item().isEmpty()) {
                return ApiResponse.notFound(gson.toJson(Map.of(
                        "error", "could not find user with provided 'userId': " + userId
                )));
            }

            // Delete the user
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(userId).build()))
                    .build());

            return ApiResponse.noContent();
        } catch (Exception e) {
            context.getLogger().log("Error deleting user: " + e.getMessage());
            return ApiResponse.error(gson.toJson(Map.of(
                    "error", "could not delete user with provided 'userId': " + userId
            )));
        }
    }
}
