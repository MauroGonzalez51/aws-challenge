/* eslint-disable no-template-curly-in-string */
import type {
    AttributeDefinition,
    GlobalSecondaryIndex,
    KeySchemaElement,
} from "@aws-sdk/client-dynamodb";
import type { AWS } from "@serverless/typescript";
import {
    BillingMode,
    KeyType,
    ProjectionType,
    ScalarAttributeType,
} from "@aws-sdk/client-dynamodb";

export default {
    org: "maurogonzalez51",
    service: "aws-challenge-serverless-node",
    stages: {
        default: {
            params: {
                tableName: "users-table-${sls:stage}",
            },
        },
    },
    provider: {
        name: "aws",
        runtime: "nodejs24.x",
        architecture: "arm64",
        iam: {
            role: {
                statements: [
                    {
                        Effect: "Allow",
                        Action: [
                            "dynamodb:Query",
                            "dynamodb:Scan",
                            "dynamodb:GetItem",
                            "dynamodb:PutItem",
                            "dynamodb:UpdateItem",
                            "dynamodb:DeleteItem",
                        ],
                        Resource: [
                            {
                                "Fn::GetAtt": ["UsersTable", "Arn"],
                            },
                        ],
                    },
                ],
            },
        },
        environment: {
            USERS_TABLE: "${param:tableName}",
        },
    },
    functions: {
        createUser: {
            handler: "src/functions/create-user.handler",
            events: [
                {
                    httpApi: {
                        method: "POST",
                        path: "/users",
                    },
                },
                {
                    httpApi: {
                        method: "GET",
                        path: "/docs/create-user/{proxy+}",
                    },
                },
            ],
            iamRoleStatements: [
                {
                    Effect: "Allow",
                    Action: ["dynamodb::PutItem"],
                    Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                },
            ],
        },
        getUser: {
            handler: "src/functions/get-user.handler",
            events: [
                {
                    httpApi: {
                        method: "GET",
                        path: "/users/{userId}",
                    },
                },
                {
                    httpApi: {
                        method: "GET",
                        path: "/docs/get-user/{proxy+}",
                    },
                },
            ],
            iamRoleStatements: [
                {
                    Effect: "Allow",
                    Action: ["dynamodb:GetItem"],
                    Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                },
            ],
        },
        updateUser: {
            handler: "src/functions/update-user.handler",
            events: [
                {
                    httpApi: {
                        method: "PUT",
                        path: "/users/{userId}",
                    },
                },
                {
                    httpApi: {
                        method: "GET",
                        path: "/docs/update-user/{proxy+}",
                    },
                },
            ],
            iamRoleStatements: [
                {
                    Effect: "Allow",
                    Action: ["dynamodb::PutItem", "dynamodb::GetItem"],
                    Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                },
            ],
        },
        deleteUser: {
            handler: "src/functions/delete-user.handler",
            events: [
                {
                    httpApi: {
                        method: "DELETE",
                        path: "/users/{userId}",
                    },
                },
                {
                    httpApi: {
                        method: "GET",
                        path: "/docs/delete-user/{proxy+}",
                    },
                },
            ],
            iamRoleStatements: [
                {
                    Effect: "Allow",
                    Action: ["dynamodb::DeleteItem", "dynamodb::GetItem"],
                    Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                },
            ],
        },
    },
    resources: {
        Resources: {
            UsersTable: {
                Type: "AWS::DynamoDB::Table",
                Properties: {
                    AttributeDefinitions: [
                        {
                            AttributeName: "id",
                            AttributeType: ScalarAttributeType.S,
                        },
                        {
                            AttributeName: "email",
                            AttributeType: ScalarAttributeType.S,
                        },
                    ] satisfies AttributeDefinition[],
                    KeySchema: [
                        {
                            AttributeName: "id",
                            KeyType: KeyType.HASH,
                        },
                    ] satisfies KeySchemaElement[],
                    GlobalSecondaryIndexes: [
                        {
                            IndexName: "email-index",
                            KeySchema: [
                                {
                                    AttributeName: "email",
                                    KeyType: KeyType.HASH,
                                },
                            ],
                            Projection: {
                                ProjectionType: ProjectionType.ALL,
                            },
                        },
                    ] satisfies GlobalSecondaryIndex[],
                    BillingMode: BillingMode.PAY_PER_REQUEST,
                    TableName: "${param:tableName}",
                },
            },
        },
    },
    build: { esbuild: { bundle: true, minify: true } },
    plugins: ["serverless-offline"],
} satisfies AWS;
