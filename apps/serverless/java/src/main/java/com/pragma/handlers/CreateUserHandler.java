package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.pragma.models.User;
import com.pragma.utils.ApiResponse;
import com.pragma.utils.DynamoDBClientProvider;
import com.pragma.utils.ValidationHelper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Map;

public class CreateUserHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb = DynamoDBClientProvider.getClient();
    private final SqsClient sqs = SqsClient.create();
    private final Gson gson = new Gson();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String body = event.getBody();

        if (body == null || body.isBlank()) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "request body is required")));
        }

        User user;
        try {
            user = gson.fromJson(body, User.class);
        } catch (Exception e) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "invalid JSON")));
        }

        List<String> errors = ValidationHelper.validate(user);
        if (!errors.isEmpty()) {
            return ApiResponse.badRequest(gson.toJson(Map.of("error", "validation errors", "data", errors)));
        }

        String tableName = System.getenv("USERS_TABLE");

        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "id", AttributeValue.builder().s(user.getId()).build(),
                            "name", AttributeValue.builder().s(user.getName()).build(),
                            "email", AttributeValue.builder().s(user.getEmail()).build()
                    ))
                    .build());
        } catch (Exception e) {
            context.getLogger().log("Error creating user: " + e.getMessage());
            return ApiResponse.error(gson.toJson(Map.of("error", "could not create user")));
        }

        // Send message to SQS (fire and forget)
        try {
            String queueUrl = System.getenv("SQS_QUEUE_URL");
            if (queueUrl != null && !queueUrl.isBlank()) {
                sqs.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(gson.toJson(user))
                        .build());
            }
        } catch (Exception e) {
            context.getLogger().log("Failed to send SQS message: " + e.getMessage());
        }

        return ApiResponse.created(gson.toJson(user));
    }
}
