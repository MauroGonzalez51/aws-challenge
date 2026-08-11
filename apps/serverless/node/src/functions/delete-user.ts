import { DeleteCommand, GetCommand } from "@aws-sdk/lib-dynamodb";
import { swaggerUI } from "@hono/swagger-ui";
import { Hono } from "hono";
import { describeRoute, openAPIRouteHandler, resolver } from "hono-openapi";
import { env } from "hono/adapter";
import { handle } from "hono/aws-lambda";
import { cors } from "hono/cors";
import { dynamoDBClient } from "@/lib/client";
import { consola } from "@/lib/logger";
import { ErrorSchema } from "@/models";

export const app = new Hono();
const { docClient } = dynamoDBClient();

app.use("/*", cors());

app.get(
    "/docs/delete-user/openapi",
    openAPIRouteHandler(app, {
        documentation: {
            info: {
                title: "Get User",
                version: "1.0.0",
                description: "Get User information based on userId",
            },
            servers: [
                {
                    url: "http://localhost:3000/",
                    description: "Local Server",
                },
            ],
        },
    }),
);

app.get("/docs/delete-user/swagger", swaggerUI({ url: "/docs/delete-user/openapi" }));

app.delete(
    "/users/:userId",
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
            consola.error(error);

            context.status(500);
            return context.json({
                error: `could not delete user with provided 'userId': ${userId}`,
            });
        }
    },
);

export const handler = handle(app);
