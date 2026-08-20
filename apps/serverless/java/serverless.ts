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
    service: "aws-challenge-serverless-java",
    stages: {
        default: {
            params: {
                tableName: "users-table-${sls:stage}",
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
    package: {
        individually: false,
        artifact: "build/libs/serverless-java-all.jar",
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
            handler: "com.pragma.handlers.GetUserHandler",
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
            handler: "com.pragma.handlers.UpdateUserHandler",
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
            handler: "com.pragma.handlers.DeleteUserHandler",
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
            handler: "com.pragma.handlers.SendEmailHandler",
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
} satisfies AWS;
