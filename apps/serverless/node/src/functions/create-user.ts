import { PutCommand } from "@aws-sdk/lib-dynamodb";
import { swaggerUI } from "@hono/swagger-ui";
import { Hono } from "hono";
import { describeRoute, openAPIRouteHandler, resolver, validator } from "hono-openapi";
import { env } from "hono/adapter";
import { handle } from "hono/aws-lambda";
import { cors } from "hono/cors";
import { dynamoDBClient } from "@/lib/client";
import { consola } from "@/lib/logger";
import { CreateUserSchema, ErrorSchema, UserSchema } from "@/models";

const app = new Hono();
const { docClient } = dynamoDBClient();

app.use("/*", cors());

app.get(
    "/docs/create-user/openapi",
    openAPIRouteHandler(app, {
        documentation: {
            info: {
                title: "Create User",
                version: "1.0.0",
                description: "Create a new user record",
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

app.get("/docs/create-user/swagger", swaggerUI({ url: "/docs/create-user/openapi" }));

app.post(
    "/users",
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
            consola.error(error);

            context.status(500);
            return context.json({ error: "could not create user" });
        }
    },
);

export const handler = handle(app);
