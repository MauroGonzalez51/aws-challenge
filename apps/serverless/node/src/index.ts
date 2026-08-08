import process from "node:process";
import { dynamoDBClient } from "@/lib/client";
import { GetCommand, PutCommand } from "@aws-sdk/lib-dynamodb";
import { Hono } from "hono";
import { consola } from "@/lib/logger";
import { UserSchema, CreateUserSchema } from "@/models/user";
import { handle } from "hono/aws-lambda";

const app = new Hono();

const USERS_TABLE = process.env.USERS_TABLE;
const { docClient } = dynamoDBClient();

app.get("/users/:userId", async (context) => {
    const userId = context.req.param("userId");

    try {
        const command = new GetCommand({ TableName: USERS_TABLE, Key: { userId } });
        const { Item } = await docClient.send(command);

        if (!Item) {
            context.status(404);
            return context.json({ error: `could not find user with provided 'userId': ${userId}` });
        }

        const record = UserSchema.safeParse(Item);

        if (record.success) {
            return context.json(record.data);
        }

        if (record.error.issues.length > 0) {
            context.status(400);
            return context.json({ error: "validation errors", data: record.error.issues });
        }
    } catch (error) {
        consola.error(error);

        context.status(500);
        return context.json({ error: `could not find user with provided 'userId': ${userId}` });
    }
});

app.post("/users", async (context) => {
    const body = await context.req.json();
    const payload = CreateUserSchema.safeParse(body);

    if (!payload.success) {
        context.status(400);
        return context.json({ error: "validation errors", data: payload.error.issues });
    }

    const { userId, name } = payload.data;

    try {
        const command = new PutCommand({ TableName: USERS_TABLE, Item: { userId, name } });
        await docClient.send(command);
        return context.json({ userId, name });
    } catch (error) {
        consola.error(error);

        context.status(500);
        return context.json({ error: "could not create user" });
    }
});

app.notFound((context) => {
    return context.json({ error: "not found" });
});

export const handler = handle(app);
