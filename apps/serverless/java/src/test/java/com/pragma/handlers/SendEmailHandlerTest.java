package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendEmailHandlerTest {

    @Mock
    private SnsClient snsClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private SendEmailHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
        handler = new SendEmailHandler(snsClient, "arn:aws:sns:us-east-1:123456789:test-topic");
    }

    @Test
    void shouldPublishSnsMessage_forValidUser() {
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().build());

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody("{\"id\":\"user-1\", \"name\":\"John\", \"email\":\"john@example.com\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        Void result = handler.handleRequest(event, context);

        assertNull(result);
        verify(snsClient).publish(any(PublishRequest.class));
    }

    @Test
    void shouldSkip_whenUserPayloadIsInvalid() {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody("{\"invalid\":\"data\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        Void result = handler.handleRequest(event, context);

        assertNull(result);
        verify(snsClient, never()).publish(any(PublishRequest.class));
    }

    @Test
    void shouldProcessMultipleRecords() {
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().build());

        SQSEvent.SQSMessage msg1 = new SQSEvent.SQSMessage();
        msg1.setBody("{\"id\":\"1\", \"name\":\"John\", \"email\":\"john@example.com\"}");

        SQSEvent.SQSMessage msg2 = new SQSEvent.SQSMessage();
        msg2.setBody("{\"id\":\"2\", \"name\":\"Jane\", \"email\":\"jane@example.com\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(msg1, msg2));

        handler.handleRequest(event, context);

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void shouldSkipRecord_whenEmailIsNull() {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody("{\"id\":\"1\", \"name\":\"NoEmail\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        handler.handleRequest(event, context);

        verify(snsClient, never()).publish(any(PublishRequest.class));
    }
}
