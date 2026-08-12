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
class DeleteUserHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private DeleteUserHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new DeleteUserHandler(dynamoDb);
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
    void shouldReturn404_whenUserDoesNotExist() {
        GetItemResponse getResponse = GetItemResponse.builder().item(Map.of()).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-999"))
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("could not find user"));
    }

    @Test
    void shouldReturn204_whenUserDeletedSuccessfully() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("user-1").build(),
                "name", AttributeValue.builder().s("John").build(),
                "email", AttributeValue.builder().s("john@example.com").build()
        );
        GetItemResponse getResponse = GetItemResponse.builder().item(item).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);
        when(dynamoDb.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(204, response.getStatusCode());
        verify(dynamoDb).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void shouldReturn500_whenDynamoDbFails() {
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenThrow(new RuntimeException("DB error"));

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("userId", "user-1"))
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("could not delete user"));
    }
}
