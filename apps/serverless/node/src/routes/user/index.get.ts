import type { AppEnvironment } from "@/index";
import { GetCommand } from "@aws-sdk/lib-dynamodb";
import { Hono } from "hono";
import { describeRoute, resolver } from "hono-openapi";
import { env } from "hono/adapter";
import { ErrorSchema, UserSchema } from "@/models";

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
        const logger = context.var.container.get("logger");

        const userId = context.req.param("userId");

        try {
            const command = new GetCommand({ TableName: USERS_TABLE, Key: { id: userId } });
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
            logger.error(error);

            context.status(500);
            return context.json({ error: `could not find user with provided 'userId': ${userId}` });
        }
    },
);

export { router };
