package myproject;

import com.pulumi.Pulumi;

import java.util.List;

import com.pulumi.Context;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.rds.InstanceArgs;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupEgressArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupIngressArgs;
import com.pulumi.random.RandomPassword;
import com.pulumi.random.RandomPasswordArgs;

public class App {
    public static void main(String[] args) {
        Pulumi.run(App::stack);
    }

    public static void stack(Context ctx) {
        SecurityGroup securityGroup = new SecurityGroup("db-security-group",
                SecurityGroupArgs.builder()
                        .description("allow public access to db")
                        .ingress(
                                SecurityGroupIngressArgs
                                        .builder()
                                        .protocol("tcp")
                                        .fromPort(5432)
                                        .toPort(5432)
                                        .cidrBlocks("0.0.0.0/0").build())
                        .egress(
                                SecurityGroupEgressArgs
                                        .builder()
                                        .protocol("-1")
                                        .fromPort(0)
                                        .toPort(0)
                                        .cidrBlocks("0.0.0.0/0")
                                        .build())
                        .build());

        Instance dbInstance = new Instance("db-instance",
                InstanceArgs.builder()
                        .engine("postgresql")
                        .instanceClass("db.t3.micro")
                        .allocatedStorage(20)
                        .dbName("aws-users")
                        .username("users_admin")
                        .password(new RandomPassword("db-password",
                                RandomPasswordArgs
                                        .builder()
                                        .length(16)
                                        .special(true)
                                        .overrideSpecial("_%@")
                                        .build())
                                .result())
                        .publiclyAccessible(true)
                        .skipFinalSnapshot(true)
                        .vpcSecurityGroupIds(securityGroup.id().applyValue(List::of))
                        .build());

        ctx.export("dbHost", dbInstance.address());
        ctx.export("dbPort", dbInstance.port());
        ctx.export("dbUsername", dbInstance.username());
        ctx.export("dbPassword", dbInstance.password());
        ctx.export("dbName", dbInstance.dbName());
    }
}
