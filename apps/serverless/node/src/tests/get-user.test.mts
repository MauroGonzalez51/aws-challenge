import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { beforeEach, describe, expect, it, vi } from "vitest";

const docClientSend = vi.spyOn(DynamoDBDocumentClient.prototype, "send").mockResolvedValue({} as any);

const { app } = await import("../functions/get-user");

describe("get-user", () => {
    beforeEach(() => {
        docClientSend.mockReset().mockResolvedValue({} as any);
    });

    it("should return 200 with user data when user is found", async () => {
        docClientSend.mockResolvedValue({
            Item: { id: "user-1", name: "John Doe", email: "john@example.com" },
        } as any);

        const res = await app.request("/users/user-1");

        expect(res.status).toBe(200);
        const body = await res.json();
        expect(body.id).toBe("user-1");
        expect(body.name).toBe("John Doe");
        expect(body.email).toBe("john@example.com");
    });

    it("should return 404 when user is not found", async () => {
        docClientSend.mockResolvedValue({ Item: undefined } as any);

        const res = await app.request("/users/user-999");

        expect(res.status).toBe(404);
        const body = await res.json();
        expect(body.error).toContain("could not find user");
    });

    it("should return 500 when DynamoDB throws", async () => {
        docClientSend.mockRejectedValue(new Error("DynamoDB error"));

        const res = await app.request("/users/user-1");

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toContain("could not find user");
    });

    it("should return 400 when item has invalid schema", async () => {
        docClientSend.mockResolvedValue({
            Item: { id: "user-1", name: "John", email: "not-a-valid-email" },
        } as any);

        const res = await app.request("/users/user-1");

        expect(res.status).toBe(400);
        const body = await res.json();
        expect(body.error).toBe("validation errors");
    });
});
