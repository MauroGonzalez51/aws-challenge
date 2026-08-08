import type { AppEnvironment } from "@/index";
import { DeleteCommand, GetCommand } from "@aws-sdk/lib-dynamodb";
import { Hono } from "hono";
import { describeRoute, resolver } from "hono-openapi";
import { env } from "hono/adapter";
import { ErrorSchema } from "@/models";

const router = new Hono<AppEnvironment>();

router.delete(
    "/:userId",
    describeRoute({
        description: "delete a user record by userId",
        responses: {
            204: {
                description: "user deleted successfully",
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
            const { Item } = await docClient.send(
                new GetCommand({ TableName: USERS_TABLE, Key: { id: userId } }),
            );

            if (!Item) {
                context.status(404);
                return context.json({
                    error: `could not find user with provided 'userId': ${userId}`,
                });
            }

            await docClient.send(
                new DeleteCommand({ TableName: USERS_TABLE, Key: { id: userId } }),
            );

            return context.body(null, 204);
        } catch (error) {
            logger.error(error);

            context.status(500);
            return context.json({
                error: `could not delete user with provided 'userId': ${userId}`,
            });
        }
    },
);

export { router };
