package com.pragma.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import com.pragma.models.User;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class SendEmailHandler implements RequestHandler<SQSEvent, Void> {

    private final SnsClient snsClient;
    private final Gson gson = new Gson();
    private final String topicArn;

    public SendEmailHandler() {
        this.snsClient = SnsClient.create();
        this.topicArn = System.getenv("SNS_TOPIC_ARN");
    }

    SendEmailHandler(SnsClient snsClient, String topicArn) {
        this.snsClient = snsClient;
        this.topicArn = topicArn;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            User user = gson.fromJson(record.getBody(), User.class);

            if (user == null || user.getEmail() == null) {
                context.getLogger().log("Invalid user payload, skipping");
                continue;
            }

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject("Welcome " + user.getName())
                    .message("Hello " + user.getName() + ", your account with email " + user.getEmail() + " has been created")
                    .build();

            snsClient.publish(request);
            context.getLogger().log("SNS notification sent for user: " + user.getEmail());
        }

        return null;
    }
}
