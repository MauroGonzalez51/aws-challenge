import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { beforeEach, describe, expect, it, vi } from "vitest";

const docClientSend = vi.spyOn(DynamoDBDocumentClient.prototype, "send").mockResolvedValue({} as any);

const { app } = await import("../functions/update-user");

describe("update-user", () => {
    beforeEach(() => {
        docClientSend.mockReset().mockResolvedValue({} as any);
    });

    it("should return 204 for simple update (no id change)", async () => {
        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(204);
        expect(docClientSend).toHaveBeenCalledTimes(1);
    });

    it("should return 204 for update with same id", async () => {
        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-1", name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(204);
        expect(docClientSend).toHaveBeenCalledTimes(1);
    });

    it("should return 400 when validation fails", async () => {
        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: "", email: "not-email" }),
        });

        expect(res.status).toBe(400);
    });

    it("should return 400 when new id already exists", async () => {
        docClientSend.mockResolvedValue({
            Item: { id: "user-2", name: "Existing", email: "existing@example.com" },
        } as any);

        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-2", name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(400);
        const body = await res.json();
        expect(body.error).toContain("already exists");
    });

    it("should return 204 when id change transaction succeeds", async () => {
        let callIndex = 0;
        docClientSend.mockImplementation(async () => {
            callIndex++;
            if (callIndex === 1) {
                return { Item: undefined };
            }
            return {};
        });

        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-new", name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(204);
        expect(docClientSend).toHaveBeenCalledTimes(2);
    });

    it("should return 500 when simple update fails", async () => {
        docClientSend.mockRejectedValue(new Error("DynamoDB error"));

        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toBe("internal server error");
    });

    it("should return 500 when get for id check fails", async () => {
        docClientSend.mockRejectedValue(new Error("DynamoDB error"));

        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-new", name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toContain("error retrieving user");
    });

    it("should return 500 when transaction fails", async () => {
        let callIndex = 0;
        docClientSend.mockImplementation(async () => {
            callIndex++;
            if (callIndex === 1) {
                return { Item: undefined };
            }
            throw new Error("Transaction failed");
        });

        const res = await app.request("/users/user-1", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: "user-new", name: "Updated", email: "updated@example.com" }),
        });

        expect(res.status).toBe(500);
        const body = await res.json();
        expect(body.error).toContain("error retrieving user");
    });
});
