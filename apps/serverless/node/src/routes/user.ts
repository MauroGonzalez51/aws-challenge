import type { AppEnvironment } from "@/index";
import { Hono } from "hono";
import { UserSchema, CreateUserSchema } from "@/models/user";
import { ErrorSchema } from "@/models/error";
import { GetCommand, PutCommand } from "@aws-sdk/lib-dynamodb";
import { consola } from "@/lib/logger";
import { env } from "hono/adapter";
import { describeRoute, resolver, validator } from "hono-openapi";

const router = new Hono<AppEnvironment>();

router.get(
    "/:userId",
    describeRoute({
        description: "retrieve information based on userId",
        responses: {
            200: {
                description: "user found",
                content: {
                    "application/json": { schema: resolver(UserSchema) },
                },
            },
            400: {
                description: "validation errors",
                content: {
                    "application/json": { schema: resolver(ErrorSchema) },
                },
            },
            404: {
                description: "user not found",
                content: {
                    "application/json": { schema: resolver(ErrorSchema) },
                },
            },
            500: {
                description: "internal server error",
                content: {
                    "application/json": { schema: resolver(ErrorSchema) },
                },
            },
        },
        tags: ["user"],
    }),
    async (context) => {
        const { USERS_TABLE } = env<{ USERS_TABLE: string | undefined }>(context);
        const { docClient } = context.var.container.get("client");

        const userId = context.req.param("userId");

        try {
            const command = new GetCommand({ TableName: USERS_TABLE, Key: { userId } });
            const { Item } = await docClient.send(command);

            if (!Item) {
                context.status(404);
                return context.json({
                    error: `could not find user with provided 'userId': ${userId}`,
                });
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
    },
);

router.post(
    "/",
    describeRoute({
        description: "create a new user record",
        responses: {
            201: {
                description: "user created succesfully",
                content: {
                    "application/json": { schema: resolver(UserSchema) },
                },
            },
            400: {
                description: "validation errors",
                content: {
                    "application/json": { schema: resolver(ErrorSchema) },
                },
            },
            500: {
                description: "internal server error",
                content: {
                    "application/json": { schema: resolver(ErrorSchema) },
                },
            },
        },
        tags: ["user"],
    }),
    validator("json", CreateUserSchema),
    async (context) => {
        const { USERS_TABLE } = env<{ USERS_TABLE: string | undefined }>(context);
        const { docClient } = context.var.container.get("client");

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

            context.status(201);
            return context.json({ userId, name });
        } catch (error) {
            consola.error(error);

            context.status(500);
            return context.json({ error: "could not create user" });
        }
    },
);

export { router };
