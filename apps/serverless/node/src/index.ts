import process from "node:process";
import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
import { DynamoDBDocumentClient, GetCommand, PutCommand } from "@aws-sdk/lib-dynamodb";

const express = require("express");
const serverless = require("serverless-http");

const app = express();

function dynamoDBClient() {
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

const USERS_TABLE = process.env.USERS_TABLE;
const { docClient } = dynamoDBClient();

app.use(express.json());

app.get("/users/:userId", async (req, res) => {
    const params = {
        TableName: USERS_TABLE,
        Key: {
            userId: req.params.userId,
        },
    };

    try {
        const command = new GetCommand(params);
        const { Item } = await docClient.send(command);
        if (Item) {
            const { userId, name } = Item;
            res.json({ userId, name });
        } else {
            res.status(404).json({ error: 'Could not find user with provided "userId"' });
        }
    } catch (error) {
        console.log(error);
        res.status(500).json({ error: "Could not retrieve user" });
    }
});

app.post("/users", async (req, res) => {
    const { userId, name } = req.body;
    console.warn(USERS_TABLE);
    if (typeof userId !== "string") {
        res.status(400).json({ error: '"userId" must be a string' });
    } else if (typeof name !== "string") {
        res.status(400).json({ error: '"name" must be a string' });
    }

    const params = {
        TableName: USERS_TABLE,
        Item: { userId, name },
    };

    try {
        const command = new PutCommand(params);
        await docClient.send(command);
        res.json({ userId, name });
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Could not create user" });
    }
});

app.use((req, res, next) => {
    return res.status(404).json({
        error: "Not Found",
    });
});

exports.handler = serverless(app);
