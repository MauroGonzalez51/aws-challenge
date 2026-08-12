package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private UpdateUserHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new UpdateUserHandler(dynamoDb);
    }

    @Test
    void shouldReturn400_whenPathParametersIsNull() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(null)
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("userId path parameter is required"));
    }

    @Test
    void shouldReturn400_whenBodyIsNull() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody(null)
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("request body is required"));
    }

    @Test
    void shouldReturn400_whenInvalidJson() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{broken json")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("invalid JSON"));
    }

    @Test
    void shouldReturn400_whenValidationFails() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"name\":\"\", \"email\":\"not-an-email\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("validation errors"));
    }

    @Test
    void shouldReturn204_whenSimpleUpdateSucceeds() {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"name\":\"Updated\", \"email\":\"updated@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(204, response.getStatusCode());
        verify(dynamoDb).putItem(any(PutItemRequest.class));
    }

    @Test
    void shouldReturn204_whenSameIdUpdate() {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"id\":\"user-1\", \"name\":\"Updated\", \"email\":\"updated@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(204, response.getStatusCode());
        verify(dynamoDb).putItem(any(PutItemRequest.class));
    }

    @Test
    void shouldReturn400_whenNewIdAlreadyExists() {
        Map<String, AttributeValue> existingItem = Map.of(
                "id", AttributeValue.builder().s("user-2").build(),
                "name", AttributeValue.builder().s("Existing").build(),
                "email", AttributeValue.builder().s("existing@example.com").build()
        );
        GetItemResponse getResponse = GetItemResponse.builder().item(existingItem).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"id\":\"user-2\", \"name\":\"Updated\", \"email\":\"updated@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("already exists"));
    }

    @Test
    void shouldReturn204_whenTransactionUpdateSucceeds() {
        GetItemResponse getResponse = GetItemResponse.builder().item(Map.of()).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"id\":\"user-new\", \"name\":\"Updated\", \"email\":\"updated@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(204, response.getStatusCode());
        verify(dynamoDb).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void shouldReturn500_whenSimpleUpdateFails() {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenThrow(new RuntimeException("DB error"));

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .withBody("{\"name\":\"Updated\", \"email\":\"updated@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("internal server error"));
    }
}
