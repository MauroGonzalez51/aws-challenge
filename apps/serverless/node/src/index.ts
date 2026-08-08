import type { InferdiHonoEnv } from "@inferdi/hono";
import { Hono } from "hono";
import { handle } from "hono/aws-lambda";
import { router } from "@/routes/user";
import { openAPIRouteHandler } from "hono-openapi";
import { cors } from "hono/cors";
import { swaggerUI } from "@hono/swagger-ui";
import { inferdiHono } from "@inferdi/hono";
import { buildRootContainer } from "@/container";

const root = buildRootContainer();

export type AppEnvironment = InferdiHonoEnv<typeof root, "container">;
const app = new Hono<AppEnvironment>();

app.use(
    "*",
    inferdiHono({
        container: root,
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

app.route("/users", router);

app.notFound((context) => {
    return context.json({ error: "not found" });
});

export const handler = handle(app);
