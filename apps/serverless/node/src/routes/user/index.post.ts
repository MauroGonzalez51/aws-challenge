import type { AppEnvironment } from "@/index";
import { PutCommand } from "@aws-sdk/lib-dynamodb";
import { Hono } from "hono";
import { describeRoute, resolver, validator } from "hono-openapi";
import { env } from "hono/adapter";
import { ErrorSchema } from "@/models/error";
import { CreateUserSchema, UserSchema } from "@/models/user";

const router = new Hono<AppEnvironment>();

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
        const logger = context.var.container.get("logger");

        const body = await context.req.json();
        const payload = CreateUserSchema.safeParse(body);

        if (!payload.success) {
            context.status(400);
            return context.json({ error: "validation errors", data: payload.error.issues });
        }

        const { id, name, email } = payload.data;

        try {
            const command = new PutCommand({ TableName: USERS_TABLE, Item: { id, name, email } });
            await docClient.send(command);

            context.status(201);
            return context.json({ id, name, email });
        } catch (error) {
            logger.error(error);

            context.status(500);
            return context.json({ error: "could not create user" });
        }
    },
);

export { router };
