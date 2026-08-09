/* eslint-disable no-template-curly-in-string */
import type { AWS } from "@serverless/typescript";

export default {
    org: "maurogonzalez51",
    service: "aws-challenge-serverless-java",
    stages: {
        default: {
            params: {
                tableName: "users-table-java-${sls:stage}",
            },
        },
    },
    provider: {
        name: "aws",
        runtime: "java21",
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
            handler: "com.pragma.handlers.CreateUserHandler",
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
            handler: "com.pragma.handlers.GetUserHandler",
            events: [
                {
                    httpApi: {
                        method: "GET",
                        path: "/users/{userId}",
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
            handler: "com.pragma.handlers.UpdateUserHandler",
            events: [
                {
                    httpApi: {
                        method: "PUT",
                        path: "/users/{userId}",
                    },
                },
            ],
            iam: {
                role: {
                    statements: [
                        {
                            Effect: "Allow",
                            Action: ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:DeleteItem", "dynamodb:ConditionCheckItem"],
                            Resource: { "Fn::GetAtt": ["UsersTable", "Arn"] },
                        },
                    ],
                },
            },
        },
        deleteUser: {
            handler: "com.pragma.handlers.DeleteUserHandler",
            events: [
                {
                    httpApi: {
                        method: "DELETE",
                        path: "/users/{userId}",
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
            handler: "com.pragma.handlers.SendEmailHandler",
            events: [
                {
                    sqs: {
                        arn: {
                            "Fn::GetAtt": ["UserCreatedQueue", "Arn"],
                        },
                    },
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
                            AttributeType: "S",
                        },
                        {
                            AttributeName: "email",
                            AttributeType: "S",
                        },
                    ],
                    KeySchema: [
                        {
                            AttributeName: "id",
                            KeyType: "HASH",
                        },
                    ],
                    GlobalSecondaryIndexes: [
                        {
                            IndexName: "email-index",
                            KeySchema: [
                                {
                                    AttributeName: "email",
                                    KeyType: "HASH",
                                },
                            ],
                            Projection: {
                                ProjectionType: "ALL",
                            },
                        },
                    ],
                    BillingMode: "PAY_PER_REQUEST",
                    TableName: "${param:tableName}",
                },
            },
            UserCreatedQueue: {
                Type: "AWS::SQS::Queue",
                Properties: {
                    QueueName: "user-created-queue-java-${sls:stage}",
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
                                        "Fn::GetAtt": [
                                            "CreateUserIamRoleLambdaExecution",
                                            "Arn",
                                        ],
                                    },
                                },
                                Action: "sqs:SendMessage",
                                Resource: { "Fn::GetAtt": ["UserCreatedQueue", "Arn"] },
                            },
                            {
                                Effect: "Allow",
                                Principal: {
                                    AWS: {
                                        "Fn::GetAtt": [
                                            "SendEmailIamRoleLambdaExecution",
                                            "Arn",
                                        ],
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
                    TopicName: "user-notification-topic-java-${sls:stage}",
                },
            },
        },
    },
    package: {
        artifact: "build/libs/serverless-java-all.jar",
    },
    plugins: ["serverless-offline"],
} satisfies AWS;
