import type { AppEnvironment } from "@/index";
import { GetCommand, PutCommand, TransactWriteCommand } from "@aws-sdk/lib-dynamodb";
import { Hono } from "hono";
import { describeRoute, resolver, validator } from "hono-openapi";
import { env } from "hono/adapter";
import { ErrorSchema, UpdateUserSchema } from "@/models";

const router = new Hono<AppEnvironment>();

router.put(
    "/:userId",
    describeRoute({
        description:
            "update an existing user record. If a new id is provided and differs from the path parameter, the record is relocated (old deleted, new created via transaction)",
        responses: {
            204: {
                description: "user updated successfully",
            },
            400: {
                description: "validation errors or target userId already exists",
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
    validator("json", UpdateUserSchema),
    async (context) => {
        const { USERS_TABLE } = env<{ USERS_TABLE: string | undefined }>(context);
        const { docClient } = context.var.container.get("client");
        const logger = context.var.container.get("logger");

        const updateId = context.req.param("userId");
        const body = await context.req.json();
        const payload = UpdateUserSchema.safeParse(body);

        if (!payload.success) {
            return context.json({ error: "validation errors", data: payload.error.issues }, 400);
        }

        /**
         * payload.data.id = string | undefined
         *
         * 1. if payload.data.id is not provided, then the given userId (path parameter)
         * is used to update the resource (PutCommand)
         *
         * 2. if payload.data.id exists and payload.data.id equals updateId,
         * a simple update command will update the record
         */
        if (!payload.data.id || payload.data.id === updateId) {
            try {
                await docClient.send(
                    new PutCommand({
                        TableName: USERS_TABLE,
                        Item: { ...payload.data, id: updateId },
                    }),
                );

                return context.body(null, 204);
            } catch (error) {
                logger.error(error);

                return context.json({ error: "internal server error" }, 500);
            }
        }

        /**
         * payload.data.id !== updateId
         * payload.data.id record already exists?
         *
         * 1. if exists -> abort request (400)
         * 2. if not exists -> continue
         */
        try {
            const { Item } = await docClient.send(
                new GetCommand({ TableName: USERS_TABLE, Key: { id: payload.data.id } }),
            );

            if (Item) {
                return context.json(
                    {
                        error: `user with given 'userId': ${payload.data.id} already exists`,
                    },
                    400,
                );
            }
        } catch (error) {
            logger.error(error);

            return context.json(
                {
                    error: `error retrieving user to update, 'userId': ${payload.data.id}`,
                },
                500,
            );
        }

        /**
         * transaction
         *
         * actions:
         *  - insert new record based on provided data (payload.data)
         *  - remove old data, based on updateId
         */
        try {
            await docClient.send(
                new TransactWriteCommand({
                    TransactItems: [
                        {
                            Put: {
                                TableName: USERS_TABLE,
                                Item: payload.data,
                                ConditionExpression: "attribute_not_exists(id)",
                            },
                        },
                        {
                            Delete: {
                                TableName: USERS_TABLE,
                                Key: {
                                    id: updateId,
                                },
                            },
                        },
                    ],
                }),
            );

            return context.body(null, 204);
        } catch (error) {
            logger.error(error);

            context.status(500);
            return context.json({
                error: `error retrieving user to update, 'userId': ${payload.data.id}`,
            });
        }
    },
);

export { router };
