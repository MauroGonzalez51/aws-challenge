import type { SQSHandler } from "aws-lambda";
import process from "node:process";
import { PublishCommand, SNSClient } from "@aws-sdk/client-sns";
import { UserSchema } from "@/models/user";

const snsClient = new SNSClient();

const TOPIC_ARN = process.env.SNS_TOPIC_ARN;

export const handler: SQSHandler = async (event) => {
    for (const record of event.Records) {
        const payload = UserSchema.safeParse(JSON.parse(record.body));

        if (!payload.success) {
            continue;
        }

        await snsClient.send(
            new PublishCommand({
                TopicArn: TOPIC_ARN,
                Subject: `Welcome ${payload.data.name}`,
                Message: `Hello ${payload.data.name}, your account with email ${payload.data.email} has been created`,
            }),
        );
    }
};
