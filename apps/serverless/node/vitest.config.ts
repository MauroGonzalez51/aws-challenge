import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
    resolve: {
        alias: {
            "@": path.resolve(import.meta.dirname, "./src"),
        },
    },
    test: {
        include: ["src/tests/**/*.test.mts"],
        env: {
            NODE_ENV: "test",
            SNS_TOPIC_ARN: "arn:aws:sns:us-east-1:123456789:test-topic",
        },
    },
});
