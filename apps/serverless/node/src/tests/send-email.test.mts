import type { SQSEvent } from "aws-lambda";
import { SNSClient } from "@aws-sdk/client-sns";
import { beforeEach, describe, expect, it, vi } from "vitest";

const snsClientSend = vi.spyOn(SNSClient.prototype, "send").mockResolvedValue({} as any);

const { handler } = await import("@/functions/send-email");

function createSQSEvent(records: Array<{ body: string }>): SQSEvent {
    return {
        Records: records.map((r) => ({
            messageId: "msg-1",
            receiptHandle: "handle",
            body: r.body,
            attributes: {} as any,
            messageAttributes: {},
            md5OfBody: "",
            eventSource: "aws:sqs",
            eventSourceARN: "arn:aws:sqs:us-east-1:123456789:queue",
            awsRegion: "us-east-1",
        })),
    };
}

describe("send-email", () => {
    beforeEach(() => {
        snsClientSend.mockReset().mockResolvedValue({} as any);
    });

    it("should publish SNS message for valid user", async () => {
        const event = createSQSEvent([
            { body: JSON.stringify({ id: "user-1", name: "John", email: "john@example.com" }) },
        ]);

        await handler(event, {} as any, () => {});

        expect(snsClientSend).toHaveBeenCalledTimes(1);
    });

    it("should skip invalid user payloads", async () => {
        const event = createSQSEvent([{ body: JSON.stringify({ invalid: "data" }) }]);

        await handler(event, {} as any, () => {});

        expect(snsClientSend).not.toHaveBeenCalled();
    });

    it("should process multiple records", async () => {
        const event = createSQSEvent([
            { body: JSON.stringify({ id: "1", name: "John", email: "john@example.com" }) },
            { body: JSON.stringify({ id: "2", name: "Jane", email: "jane@example.com" }) },
        ]);

        await handler(event, {} as any, () => {});

        expect(snsClientSend).toHaveBeenCalledTimes(2);
    });

    it("should skip records with missing email", async () => {
        const event = createSQSEvent([{ body: JSON.stringify({ id: "1", name: "NoEmail" }) }]);

        await handler(event, {} as any, () => {});

        expect(snsClientSend).not.toHaveBeenCalled();
    });
});
