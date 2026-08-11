import { SQSClient } from "@aws-sdk/client-sqs";
import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { beforeEach, describe, expect, it, vi } from "vitest";

const docClientSend = vi.spyOn(DynamoDBDocumentClient.prototype, "send").mockResolvedValue({} as any);
const sqsClientSend = vi.spyOn(SQSClient.prototype, "send").mockResolvedValue({} as any);

const { app } = await import("../functions/create-user");

describe("create-user", () => {
    beforeEach(() => {
        docClientSend.mockReset().mockResolvedValue({} as any);
        sqsClientSend.mockReset().mockResolvedValue({} as any);
    });

    it("should return 201 when user is created successfully", async () => {
        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "John Doe", email: "john@example.com" }),
        });

        expect(res.status).toBe(201);
        const body = await res.json();
        expect(body.id).toBe("user-1");
        expect(body.name).toBe("John Doe");
        expect(body.email).toBe("john@example.com");
    });

    it("should return 400 when body validation fails (missing fields)", async () => {
        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: "John Doe" }),
        });

        expect(res.status).toBe(400);
    });

    it("should return 400 when email is invalid", async () => {
        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "John", email: "not-an-email" }),
        });

        expect(res.status).toBe(400);
    });

    it("should return 500 when DynamoDB put fails", async () => {
        docClientSend.mockRejectedValue(new Error("DynamoDB error"));

        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "John", email: "john@example.com" }),
        });

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toBe("could not create user");
    });

    it("should still return 201 if SQS send fails (fire and forget)", async () => {
        docClientSend.mockResolvedValue({} as any);
        sqsClientSend.mockRejectedValue(new Error("SQS error"));

        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "John", email: "john@example.com" }),
        });

        expect(res.status).toBe(201);
    });

    it("should call DynamoDB with correct parameters", async () => {
        const res = await app.request("/users", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "John", email: "john@example.com" }),
        });

        expect(res.status).toBe(201);
        expect(docClientSend).toHaveBeenCalledTimes(1);
    });
});
