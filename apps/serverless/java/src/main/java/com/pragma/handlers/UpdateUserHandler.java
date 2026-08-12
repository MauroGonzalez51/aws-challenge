package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.pragma.models.UpdateUser;
import com.pragma.utils.ApiResponse;
import com.pragma.utils.DynamoDBClientProvider;
import com.pragma.utils.ValidationHelper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateUserHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb;
    private final Gson gson = new Gson();

    public UpdateUserHandler() {
        this.dynamoDb = DynamoDBClientProvider.getClient();
    }

    UpdateUserHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        Map<String, String> pathParams = event.getPathParameters();

        if (pathParams == null || !pathParams.containsKey("userId")) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "userId path parameter is required")));
        }

        String updateId = pathParams.get("userId");
        String body = event.getBody();

        if (body == null || body.isBlank()) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "request body is required")));
        }

        UpdateUser user;
        try {
            user = gson.fromJson(body, UpdateUser.class);
        } catch (Exception e) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "invalid JSON")));
        }

        List<String> errors = ValidationHelper.validate(user);
        if (!errors.isEmpty()) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "validation errors", "data", errors)));
        }

        String tableName = System.getenv("USERS_TABLE");

        // If no id provided or same as path param, simple update
        if (user.getId() == null || user.getId().equals(updateId)) {
            try {
                dynamoDb.putItem(PutItemRequest.builder()
                        .tableName(tableName)
                        .item(Map.of(
                                "id", AttributeValue.builder().s(updateId).build(),
                                "name", AttributeValue.builder().s(user.getName()).build(),
                                "email", AttributeValue.builder().s(user.getEmail()).build()
                        ))
                        .build());

                return ApiResponse.noContent();
            } catch (Exception e) {
                context.getLogger().log("Error updating user: " + e.getMessage());
                return ApiResponse.error(gson.toJson(Map.of("error", "internal server error")));
            }
        }

        // Different id: check if target already exists
        try {
            GetItemResponse existing = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(user.getId()).build()))
                    .build());

            if (existing.hasItem() && !existing.item().isEmpty()) {
                return ApiResponse.badRequest(gson.toJson(Map.of(
                        "error", "user with given 'userId': " + user.getId() + " already exists"
                )));
            }
        } catch (Exception e) {
            context.getLogger().log("Error checking existing user: " + e.getMessage());
            return ApiResponse.error(gson.toJson(Map.of(
                    "error", "error retrieving user to update, 'userId': " + user.getId()
            )));
        }

        // Transaction: create new record + delete old
        try {
            Map<String, AttributeValue> newItem = new HashMap<>();
            newItem.put("id", AttributeValue.builder().s(user.getId()).build());
            newItem.put("name", AttributeValue.builder().s(user.getName()).build());
            newItem.put("email", AttributeValue.builder().s(user.getEmail()).build());

            TransactWriteItem putItem = TransactWriteItem.builder()
                    .put(Put.builder()
                            .tableName(tableName)
                            .item(newItem)
                            .conditionExpression("attribute_not_exists(id)")
                            .build())
                    .build();

            TransactWriteItem deleteItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                            .tableName(tableName)
                            .key(Map.of("id", AttributeValue.builder().s(updateId).build()))
                            .build())
                    .build();

            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(List.of(putItem, deleteItem))
                    .build());

            return ApiResponse.noContent();
        } catch (Exception e) {
            context.getLogger().log("Error in transaction: " + e.getMessage());
            return ApiResponse.error(gson.toJson(Map.of(
                    "error", "error retrieving user to update, 'userId': " + user.getId()
            )));
        }
    }
}
