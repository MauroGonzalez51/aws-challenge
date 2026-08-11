import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { beforeEach, describe, expect, it, vi } from "vitest";

const docClientSend = vi.spyOn(DynamoDBDocumentClient.prototype, "send").mockResolvedValue({} as any);

const { app } = await import("../functions/delete-user");

describe("delete-user", () => {
    beforeEach(() => {
        docClientSend.mockReset().mockResolvedValue({} as any);
    });

    it("should return 204 when user is deleted successfully", async () => {
        let callIndex = 0;
        docClientSend.mockImplementation(async () => {
            callIndex++;
            if (callIndex === 1) {
                return { Item: { id: "user-1", name: "John", email: "john@example.com" } };
            }
            return {};
        });

        const res = await app.request("/users/user-1", { method: "DELETE" });

        expect(res.status).toBe(204);
    });

    it("should return 404 when user does not exist", async () => {
        docClientSend.mockResolvedValue({ Item: undefined } as any);

        const res = await app.request("/users/user-999", { method: "DELETE" });

        expect(res.status).toBe(404);
        const body = await res.json();
        expect(body.error).toContain("could not find user");
    });

    it("should return 500 when DynamoDB throws", async () => {
        docClientSend.mockRejectedValue(new Error("DynamoDB error"));

        const res = await app.request("/users/user-1", { method: "DELETE" });

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toContain("could not delete user");
    });

    it("should call DynamoDB twice (get + delete) on success", async () => {
        let callIndex = 0;
        docClientSend.mockImplementation(async () => {
            callIndex++;
            if (callIndex === 1) {
                return { Item: { id: "user-1", name: "John", email: "john@example.com" } };
            }
            return {};
        });

        const res = await app.request("/users/user-1", { method: "DELETE" });

        expect(res.status).toBe(204);
        expect(docClientSend).toHaveBeenCalledTimes(2);
    });
});
