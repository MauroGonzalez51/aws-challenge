import process from "node:process";
import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { consola } from "@/lib/logger";

export function dynamoDBClient() {
    // eslint-disable-next-line dot-notation
    consola.warn(`offline: ${process.env["IS_OFFLINE"]}`);

    // eslint-disable-next-line dot-notation
    if (process.env["IS_OFFLINE"]) {
        const client = new DynamoDBClient({
            endpoint: "http://localhost:8000",
        });

        const docClient = DynamoDBDocumentClient.from(client);

        return { client, docClient };
    }

    const client = new DynamoDBClient();
    const docClient = DynamoDBDocumentClient.from(client);

    return { client, docClient };
}
