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
                mode: "perFunction",
                statements: [
                    {
                        Effect: "Allow",
                        Action: "logs:CreateLogGroup",
                        Resource: "*",
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
            environment: {
                SQS_QUEUE_URL: { Ref: "UserCreatedQueue" },
            },
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
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["dynamodb:PutItem"],
                            Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                        },
                        {
                            Effect: "Allow",
                            Action: ["sqs:SendMessage"],
                            Resource: { "Fn::GetAtt": ["UserCreatedQueue", "Arn"] },
                        },
                    ],
                },
            },
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
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["dynamodb:GetItem"],
                            Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                        },
                    ],
                },
            },
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
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:DeleteItem"],
                            Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                        },
                    ],
                },
            },
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
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["dynamodb:DeleteItem", "dynamodb:GetItem"],
                            Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                        },
                    ],
                },
            },
        },
        sendEmail: {
            handler: "src/functions/send-email.handler",
            environment: {
                SNS_TOPIC_ARN: { Ref: "UserNotificationTopic" },
            },
            events: [
                {
                    sqs: {
                        arn: {
                            "Fn::GetAtt": ["UserCreatedQueue", "Arn"],
                        },
                    },
                },
            ],
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["sns:Publish"],
                            Resource: { Ref: "UserNotificationTopic" },
                        },
                    ],
                },
            },
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
            UserCreatedQueue: {
                Type: "AWS::SQS::Queue",
                Properties: {
                    QueueName: "user-created-queue-${sls:stage}",
                },
            },
            UserCreatedQueuePolicy: {
                Type: "AWS::SQS::QueuePolicy",
                Properties: {
                    Queues: [{ Ref: "UserCreatedQueue" }],
                    PolicyDocument: {
                        Statement: [
                            {
                                Effect: "Allow",
                                Principal: {
                                    AWS: {
                                        "Fn::GetAtt": ["CreateUserIamRoleLambdaExecution", "Arn"],
                                    },
                                },
                                Action: "sqs:SendMessage",
                                Resource: { "Fn::GetAtt": ["UserCreatedQueue", "Arn"] },
                            },
                            {
                                Effect: "Allow",
                                Principal: {
                                    AWS: {
                                        "Fn::GetAtt": ["SendEmailIamRoleLambdaExecution", "Arn"],
                                    },
                                },
                                Action: [
                                    "sqs:ReceiveMessage",
                                    "sqs:DeleteMessage",
                                    "sqs:GetQueueAttributes",
                                ],
                                Resource: { "Fn::GetAtt": ["UserCreatedQueue", "Arn"] },
                            },
                        ],
                    },
                },
            },
            UserNotificationTopic: {
                Type: "AWS::SNS::Topic",
                Properties: {
                    TopicName: "user-notification-topic-${sls:stage}",
                },
            },
        },
    },
    build: { esbuild: { bundle: true, minify: true } },
    plugins: ["serverless-offline"],
} satisfies AWS;
