import { AWS } from "@serverless/typescript";

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
        api: {
            handler: "src/index.handler",
            events: [
                {
                    httpApi: "*",
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
                            AttributeName: "userId",
                            AttributeType: "S",
                        },
                    ],
                    KeySchema: [
                        {
                            AttributeName: "userId",
                            KeyType: "HASH",
                        },
                    ],
                    BillingMode: "PAY_PER_REQUEST",
                    TableName: "${param:tableName}",
                },
            },
        },
    },
    build: { esbuild: { bundle: true, minify: true } },
    plugins: ["serverless-offline"],
} satisfies AWS;
