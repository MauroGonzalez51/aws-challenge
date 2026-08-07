package myproject;

import java.util.List;

import com.pulumi.Pulumi;
import com.pulumi.Context;
import com.pulumi.core.Output;
import myproject.resources.*;

public class App {
    public static void main(String[] args) {
        Pulumi.run(App::stack);
    }

    public static void stack(Context context) {
        // Task 2: Create ECR repository and build + push the Docker image
        var ecr = ECR.setup();

        // Task 4-5: Configure networking, security groups, and load balancer
        var alb = ALB.setup();

        // Task 7: RDS PostgreSQL database (only accessible from ECS tasks)
        var rds = RDS.setup(alb.vpcId(), alb.ecsSecurityGroup().id().applyValue(List::of));

        var parameters = Parameters.setup(rds.instance(), rds.dbPassword());

        // Task 3: Provision ECS cluster, task definition, and service
        var ecs = ECS.setup(
                ecr.image(),
                parameters,
                alb.targetGroup().arn(),
                alb.subnetIds(),
                alb.ecsSecurityGroup().id().applyValue(List::of));

        // CloudWatch Alarms — ECS CPU and ALB 5XX monitoring
        Alarms.setup(ecs.cluster(), ecs.service(), alb.loadBalancer());

        var cognito = Cognito.setup();

        // Task 6: Configure API Gateway endpoints (POST /users, GET /users/{id})
        var apiGw = ApiGateway.setup(alb.loadBalancer(), cognito.userPool(), cognito.userPoolClient());

        // Exports
        context.export("ECR_REPOSITORY_URL", ecr.repository().repositoryUrl());
        context.export("IMAGE_URI", ecr.image().imageName());
        context.export("ALB_DNS_NAME", alb.loadBalancer().dnsName());
        context.export("ECS_CLUSTER_NAME", ecs.cluster().name());
        context.export("API_GATEWAY_URL", Output.all(apiGw.api().apiEndpoint(), apiGw.stage().name())
                .applyValue(values -> String.format("%s/%s", values.get(0), values.get(1))));
        context.export("DB_HOST", rds.instance().address());
        context.export("DB_NAME", rds.instance().dbName());
        context.export("USER_POOL_ID", cognito.userPool().id());
        context.export("USER_POOL_ENDPOINT", cognito.userPool().endpoint());
        context.export("USER_POOL_CLIENT_ID", cognito.userPoolClient().id());
    }
}
