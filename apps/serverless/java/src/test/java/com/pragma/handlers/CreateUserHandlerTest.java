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
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private SqsClient sqs;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private CreateUserHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(logger);
        handler = new CreateUserHandler(dynamoDb, sqs);
    }

    @Test
    void shouldReturn400_whenBodyIsNull() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody(null)
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("request body is required"));
    }

    @Test
    void shouldReturn400_whenBodyIsBlank() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody("   ")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("request body is required"));
    }

    @Test
    void shouldReturn400_whenInvalidJson() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody("{invalid json")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("invalid JSON"));
    }

    @Test
    void shouldReturn400_whenValidationFails() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody("{\"id\":\"\", \"name\":\"\", \"email\":\"not-an-email\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("validation errors"));
    }

    @Test
    void shouldReturn201_whenUserCreatedSuccessfully() {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody("{\"id\":\"user-1\", \"name\":\"John\", \"email\":\"john@example.com\"}")
                .build();

        try {
            var field = System.class.getDeclaredField("env");
        } catch (Exception ignored) {
        }

        // Set env var via system property workaround — use reflection or just test
        // without SQS
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("john@example.com"));
        verify(dynamoDb).putItem(any(PutItemRequest.class));
    }

    @Test
    void shouldReturn500_whenDynamoDbFails() {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenThrow(new RuntimeException("DynamoDB error"));

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withBody("{\"id\":\"user-1\", \"name\":\"John\", \"email\":\"john@example.com\"}")
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("could not create user"));
    }
}
