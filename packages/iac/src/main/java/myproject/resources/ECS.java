package myproject.resources;

import java.util.List;

import com.pulumi.aws.cloudwatch.LogGroup;
import com.pulumi.aws.cloudwatch.LogGroupArgs;
import com.pulumi.aws.ecs.Cluster;
import com.pulumi.aws.ecs.ClusterArgs;
import com.pulumi.aws.ecs.Service;
import com.pulumi.aws.ecs.ServiceArgs;
import com.pulumi.aws.ecs.TaskDefinition;
import com.pulumi.aws.ecs.TaskDefinitionArgs;
import com.pulumi.aws.ecs.inputs.ServiceLoadBalancerArgs;
import com.pulumi.aws.ecs.inputs.ServiceNetworkConfigurationArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.RolePolicy;
import com.pulumi.aws.iam.RolePolicyArgs;
import com.pulumi.aws.iam.RolePolicyAttachment;
import com.pulumi.aws.iam.RolePolicyAttachmentArgs;
import com.pulumi.core.Either;
import com.pulumi.core.Output;
import com.pulumi.docker.Image;

public class ECS {
    public record ECSResult(Cluster cluster, TaskDefinition taskDefinition, Service service, LogGroup logGroup) {
    }

    public record ECSConfig(String clusterName, String taskFamilyName, String serviceName, String logGroupName,
            String executionRoleName, String containerName, int containerPort, String cpu, String memory) {
        public static final ECSConfig DEFAULT = new ECSConfig(
                "users-service-cluster",
                "users-service-task",
                "users-service",
                "users-service-logs",
                "users-service-execution-role",
                "users-service-container",
                8080,
                "256",
                "1024");
    }

    public static ECSResult setup(Image image, Parameters.ParametersResult parameters, Output<String> targetGroupArn,
            Output<List<String>> subnetIds, Output<List<String>> securityGroupIds) {
        var config = ECSConfig.DEFAULT;

        // ECS Cluster — logical grouping for Fargate tasks
        Cluster cluster = new Cluster(config.clusterName(), ClusterArgs.Empty);

        // CloudWatch Log Group — stores container stdout/stderr logs
        LogGroup logGroup = new LogGroup(config.logGroupName(),
                LogGroupArgs.builder()
                        .retentionInDays(7)
                        .build());

        // IAM Execution Role — allows ECS to pull images from ECR and write logs
        Role executionRole = new Role(config.executionRoleName(),
                RoleArgs.builder()
                        .assumeRolePolicy("""
                                {
                                    "Version": "2012-10-17",
                                    "Statement": [{
                                        "Effect": "Allow",
                                        "Principal": {
                                            "Service": "ecs-tasks.amazonaws.com"
                                        },
                                        "Action": "sts:AssumeRole"
                                    }]
                                }
                                """)
                        .build());

        // Attach the AWS managed policy for ECS task execution (ECR pull + CloudWatch
        // logs)
        new RolePolicyAttachment("ecs-execution-role-policy",
                RolePolicyAttachmentArgs.builder()
                        .role(executionRole.name())
                        .policyArn("arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy")
                        .build());

        Output<String> ssmSecretsPolicy = Output
                .all(parameters.dbHost().arn(), parameters.dbPort().arn(), parameters.dbUsername().arn(),
                        parameters.dbPassword().arn(), parameters.dbName().arn())
                .applyValue(arns -> String.format("""
                             {
                              "Version": "2012-10-17",
                              "Statement": [
                                  {
                                      "Effect": "Allow",
                                      "Action": ["ssm:GetParameters"],
                                      "Resource": ["%s", "%s", "%s", "%s"]
                                  },
                                  {
                                      "Effect": "Allow",
                                      "Action": ["secretsmanager:GetSecretValue"],
                                      "Resource": "%s"
                                  }
                              ]
                          }
                        """, arns.get(0), arns.get(1), arns.get(2), arns.get(4), arns.get(3)));

        new RolePolicy("ecs-ssm-secrets-poliy",
                RolePolicyArgs.builder().role(executionRole.name()).policy(ssmSecretsPolicy.applyValue(Either::ofLeft))
                        .build());

        // Build the container definition JSON with image URI, DB credentials, and log
        // config
        Output<String> containerDefinition = Output
                .all(image.imageName(), logGroup.name(), parameters.dbHost().arn(), parameters.dbPort().arn(),
                        parameters.dbUsername().arn(), parameters.dbPassword().arn(), parameters.dbName().arn())
                .applyValue(values -> {
                    String imageUri = (String) values.get(0);
                    String logGroupName = (String) values.get(1);
                    String dbHost = (String) values.get(2);
                    String dbPort = (String) values.get(3);
                    String dbUser = (String) values.get(4);
                    String dbPassword = (String) values.get(5);
                    String dbName = (String) values.get(6);

                    return String.format("""
                            [{
                                "name": "%s",
                                "image": "%s",
                                "essential": true,
                                "portMappings": [{
                                    "containerPort": %d,
                                    "hostPort": %d,
                                    "protocol": "tcp"
                                }],
                                "secrets": [
                                    {"name": "DB_HOST", "valueFrom": "%s"},
                                    {"name": "DB_PORT", "valueFrom": "%s"},
                                    {"name": "DB_USER", "valueFrom": "%s"},
                                    {"name": "DB_PASSWORD", "valueFrom": "%s"},
                                    {"name": "DB_NAME", "valueFrom": "%s"}
                                ],
                                "logConfiguration": {
                                    "logDriver": "awslogs",
                                    "options": {
                                        "awslogs-group": "%s",
                                        "awslogs-region": "us-east-1",
                                        "awslogs-stream-prefix": "ecs"
                                    }
                                }
                            }]
                            """, config.containerName(), imageUri, config.containerPort(),
                            config.containerPort(), dbHost, dbPort, dbUser,
                            dbPassword,
                            dbName,
                            logGroupName);
                });

        // Task Definition — defines how the container runs on Fargate
        TaskDefinition taskDefinition = new TaskDefinition(config.taskFamilyName(),
                TaskDefinitionArgs.builder()
                        .family(config.taskFamilyName())
                        .cpu(config.cpu())
                        .memory(config.memory())
                        .networkMode("awsvpc")
                        .requiresCompatibilities("FARGATE")
                        .executionRoleArn(executionRole.arn())
                        .containerDefinitions(containerDefinition)
                        .build());

        // ECS Service — runs and maintains the desired count of tasks behind the load
        // balancer
        Service service = new Service(config.serviceName(),
                ServiceArgs.builder()
                        .cluster(cluster.arn())
                        .taskDefinition(taskDefinition.arn())
                        .launchType("FARGATE")
                        .desiredCount(1)
                        .networkConfiguration(ServiceNetworkConfigurationArgs.builder()
                                .assignPublicIp(true)
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .build())
                        .loadBalancers(ServiceLoadBalancerArgs.builder()
                                .targetGroupArn(targetGroupArn)
                                .containerName(config.containerName())
                                .containerPort(config.containerPort())
                                .build())
                        .build());

        return new ECSResult(cluster, taskDefinition, service, logGroup);
    }
}
