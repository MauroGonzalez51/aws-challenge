package myproject.resources;

import java.util.List;

import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupEgressArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupIngressArgs;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.rds.InstanceArgs;
import com.pulumi.core.Output;
import com.pulumi.random.RandomPassword;
import com.pulumi.random.RandomPasswordArgs;

public class RDS {
    public record RDSResult(Instance instance, RandomPassword dbPassword, SecurityGroup securityGroup) {
    }

    public record RDSConfig(String instanceName, String securityGroupName, String passwordResourceName,
            String dbName, String username, String engine, String instanceClass, int allocatedStorage,
            int port) {
        public static final RDSConfig DEFAULT = new RDSConfig(
                "users-service-db",
                "users-service-db-sg",
                "users-service-db-password",
                "aws_users",
                "users_admin",
                "postgres",
                "db.t3.micro",
                20,
                5432);
    }

    public static RDSResult setup(Output<String> vpcId, Output<List<String>> ecsSecurityGroupIds) {
        var config = RDSConfig.DEFAULT;

        // Security Group for RDS — only allows inbound PostgreSQL traffic from ECS
        // tasks
        SecurityGroup dbSecurityGroup = new SecurityGroup(config.securityGroupName(),
                SecurityGroupArgs.builder()
                        .description("Allow PostgreSQL access from ECS tasks only")
                        .vpcId(vpcId)
                        .ingress(SecurityGroupIngressArgs.builder()
                                .protocol("tcp")
                                .fromPort(config.port())
                                .toPort(config.port())
                                .securityGroups(ecsSecurityGroupIds)
                                .build())
                        .egress(SecurityGroupEgressArgs.builder()
                                .protocol("-1")
                                .fromPort(0)
                                .toPort(0)
                                .cidrBlocks("0.0.0.0/0")
                                .build())
                        .build());

        // Generate a random password for the database (16 chars, safe special chars for
        // RDS)
        RandomPassword dbPassword = new RandomPassword(config.passwordResourceName(),
                RandomPasswordArgs.builder()
                        .length(16)
                        .special(true)
                        .overrideSpecial("_%-")
                        .build());

        // RDS PostgreSQL instance — single AZ, publicly accessible disabled for
        // security
        Instance dbInstance = new Instance(config.instanceName(),
                InstanceArgs.builder()
                        .engine(config.engine())
                        .instanceClass(config.instanceClass())
                        .allocatedStorage(config.allocatedStorage())
                        .dbName(config.dbName())
                        .username(config.username())
                        .password(dbPassword.result())
                        .skipFinalSnapshot(true) // Dev convenience — no snapshot on delete
                        .publiclyAccessible(false) // Only accessible from within the VPC
                        .vpcSecurityGroupIds(dbSecurityGroup.id().applyValue(List::of))
                        .build());

        return new RDSResult(dbInstance, dbPassword, dbSecurityGroup);
    }
}
