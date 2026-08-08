import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
import { DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";

export function dynamoDBClient() {
    console.warn(`offline: ${process.env.IS_OFFLINE}`);

    if (process.env.IS_OFFLINE) {
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
