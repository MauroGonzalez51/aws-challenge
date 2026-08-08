import type { InferdiHonoEnv } from "@inferdi/hono";
import { swaggerUI } from "@hono/swagger-ui";
import { inferdiHono } from "@inferdi/hono";
import { Hono } from "hono";
import { openAPIRouteHandler } from "hono-openapi";
import { handle } from "hono/aws-lambda";
import { cors } from "hono/cors";
import { buildRootContainer } from "@/container";
import { userRouter } from "@/routes/user";

const root = buildRootContainer();

export type AppEnvironment = InferdiHonoEnv<typeof root, "container">;
const app = new Hono<AppEnvironment>();

app.use(
    "*",
    inferdiHono({
        container: root,
        key: "container",
        setupScope(scope, _) {
            const request = scope.get("request");
            request.requestId = crypto.randomUUID();
        },
    }),
);

app.use("/*", cors());

app.get(
    "/docs/openapi",
    openAPIRouteHandler(app, {
        documentation: {
            info: {
                title: "Users CRUD",
                version: "1.0.0",
                description: "CRUD using dynamodb and serverless framework",
            },
            servers: [
                {
                    url: "http://localhost:3000",
                    description: "Local Server",
                },
            ],
        },
    }),
);

app.get("/docs/swagger", swaggerUI({ url: "/docs/openapi" }));

app.route("/users", userRouter);

app.notFound((context) => {
    return context.json({ error: "not found" });
});

export const handler = handle(app);
