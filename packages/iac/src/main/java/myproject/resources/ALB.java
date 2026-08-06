package myproject.resources;

import java.util.List;

import com.pulumi.aws.ec2.Ec2Functions;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.ec2.inputs.GetSubnetsArgs;
import com.pulumi.aws.ec2.inputs.GetSubnetsFilterArgs;
import com.pulumi.aws.ec2.inputs.GetVpcArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupEgressArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupIngressArgs;
import com.pulumi.aws.lb.Listener;
import com.pulumi.aws.lb.ListenerArgs;
import com.pulumi.aws.lb.LoadBalancer;
import com.pulumi.aws.lb.LoadBalancerArgs;
import com.pulumi.aws.lb.TargetGroup;
import com.pulumi.aws.lb.TargetGroupArgs;
import com.pulumi.aws.lb.inputs.ListenerDefaultActionArgs;
import com.pulumi.aws.lb.inputs.TargetGroupHealthCheckArgs;
import com.pulumi.core.Output;

public class ALB {
    public record ALBResult(LoadBalancer loadBalancer, TargetGroup targetGroup, Listener listener,
            SecurityGroup albSecurityGroup, SecurityGroup ecsSecurityGroup, Output<List<String>> subnetIds,
            Output<String> vpcId) {
    }

    public record ALBConfig(String loadBalancerName, String targetGroupName, String listenerName,
            String albSgName, String ecsSgName, int listenerPort, int containerPort) {
        public static final ALBConfig DEFAULT = new ALBConfig(
                "users-service-alb",
                "users-service-tg",
                "users-service-listener",
                "users-service-alb-sg",
                "users-service-ecs-sg",
                80,
                8080);
    }

    public static ALBResult setup() {
        var config = ALBConfig.DEFAULT;

        // Fetch the default VPC — we use it to place the ALB and ECS tasks
        var defaultVpc = Ec2Functions.getVpc(GetVpcArgs.builder()
                .default_(true)
                .build());

        Output<String> vpcId = defaultVpc.applyValue(vpc -> vpc.id());

        // Fetch subnets in the default VPC, excluding AZs where API GW VPC Link is
        // unavailable
        var subnets = Ec2Functions.getSubnets(GetSubnetsArgs.builder()
                .filters(
                        GetSubnetsFilterArgs.builder()
                                .name("vpc-id")
                                .values(defaultVpc.applyValue(vpc -> List.of(vpc.id())))
                                .build(),
                        GetSubnetsFilterArgs.builder()
                                .name("availability-zone")
                                .values("us-east-1a", "us-east-1b", "us-east-1c",
                                        "us-east-1d")
                                .build())
                .build());

        Output<List<String>> subnetIds = subnets.applyValue(s -> s.ids());

        // Security Group for ALB — allows inbound HTTP (port 80) from anywhere
        SecurityGroup albSecurityGroup = new SecurityGroup(config.albSgName(),
                SecurityGroupArgs.builder()
                        .description("Allow HTTP inbound traffic to ALB")
                        .vpcId(vpcId)
                        .ingress(SecurityGroupIngressArgs.builder()
                                .protocol("tcp")
                                .fromPort(config.listenerPort())
                                .toPort(config.listenerPort())
                                .cidrBlocks("0.0.0.0/0")
                                .build())
                        .egress(SecurityGroupEgressArgs.builder()
                                .protocol("-1")
                                .fromPort(0)
                                .toPort(0)
                                .cidrBlocks("0.0.0.0/0")
                                .build())
                        .build());

        // Security Group for ECS tasks — only allows traffic from the ALB on the
        // container port
        SecurityGroup ecsSecurityGroup = new SecurityGroup(config.ecsSgName(),
                SecurityGroupArgs.builder()
                        .description("Allow traffic from ALB to ECS tasks")
                        .vpcId(vpcId)
                        .ingress(SecurityGroupIngressArgs.builder()
                                .protocol("tcp")
                                .fromPort(config.containerPort())
                                .toPort(config.containerPort())
                                .securityGroups(albSecurityGroup.id()
                                        .applyValue(List::of))
                                .build())
                        .egress(SecurityGroupEgressArgs.builder()
                                .protocol("-1")
                                .fromPort(0)
                                .toPort(0)
                                .cidrBlocks("0.0.0.0/0")
                                .build())
                        .build());

        // Application Load Balancer — distributes HTTP traffic across ECS tasks
        LoadBalancer loadBalancer = new LoadBalancer(config.loadBalancerName(),
                LoadBalancerArgs.builder()
                        .internal(false)
                        .loadBalancerType("application")
                        .securityGroups(albSecurityGroup.id().applyValue(List::of))
                        .subnets(subnetIds)
                        .build());

        // Target Group — routes requests to ECS tasks by IP (required for awsvpc
        // network mode)
        TargetGroup targetGroup = new TargetGroup(config.targetGroupName(),
                TargetGroupArgs.builder()
                        .port(config.containerPort())
                        .protocol("HTTP")
                        .targetType("ip")
                        .vpcId(vpcId)
                        .healthCheck(TargetGroupHealthCheckArgs
                                .builder()
                                .path("/health")
                                .protocol("HTTP")
                                .matcher("200")
                                .interval(30)
                                .timeout(5)
                                .healthyThreshold(2)
                                .unhealthyThreshold(3)
                                .build())
                        .build());

        // Listener — listens on port 80 and forwards traffic to the target group
        Listener listener = new Listener(config.listenerName(),
                ListenerArgs.builder()
                        .loadBalancerArn(loadBalancer.arn())
                        .port(config.listenerPort())
                        .protocol("HTTP")
                        .defaultActions(ListenerDefaultActionArgs.builder()
                                .type("forward")
                                .targetGroupArn(targetGroup.arn())
                                .build())
                        .build());

        return new ALBResult(loadBalancer, targetGroup, listener, albSecurityGroup, ecsSecurityGroup, subnetIds,
                vpcId);
    }
}
