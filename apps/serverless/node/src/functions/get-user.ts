import { GetCommand } from "@aws-sdk/lib-dynamodb";
import { swaggerUI } from "@hono/swagger-ui";
import { Hono } from "hono";
import { describeRoute, openAPIRouteHandler, resolver } from "hono-openapi";
import { env } from "hono/adapter";
import { handle } from "hono/aws-lambda";
import { cors } from "hono/cors";
import { dynamoDBClient } from "@/lib/client";
import { consola } from "@/lib/logger";
import { ErrorSchema, UserSchema } from "@/models";

export const app = new Hono();
const { docClient } = dynamoDBClient();

app.use("/*", cors());

app.get(
    "/docs/get-user/openapi",
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

app.get("/docs/get-user/swagger", swaggerUI({ url: "/docs/get-user/openapi" }));

app.get(
    "/users/:userId",
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

            throw new Error("could not find user");
        } catch (error) {
            consola.error(error);

            context.status(500);
            return context.json({ error: `could not find user with provided 'userId': ${userId}` });
        }
    },
);

export const handler = handle(app);
