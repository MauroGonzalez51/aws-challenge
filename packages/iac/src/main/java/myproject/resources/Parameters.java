package myproject.resources;

import com.pulumi.aws.ssm.Parameter;
import com.pulumi.aws.ssm.ParameterArgs;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.secretsmanager.Secret;
import com.pulumi.aws.secretsmanager.SecretArgs;
import com.pulumi.aws.secretsmanager.SecretVersion;
import com.pulumi.aws.secretsmanager.SecretVersionArgs;
import com.pulumi.random.RandomPassword;

public class Parameters {
    public record ParametersResult(Parameter dbHost, Parameter dbPort, Parameter dbUsername, Secret dbPassword,
            Parameter dbName) {
    }

    public static ParametersResult setup(Instance dbInstance, RandomPassword dbPassword) {
        var dbHost = new Parameter("DB_HOST",
                ParameterArgs
                        .builder()
                        .name("DB_HOST")
                        .type("String")
                        .dataType("text")
                        .value(dbInstance.address())
                        .build());

        var dbPort = new Parameter("DB_PORT",
                ParameterArgs
                        .builder()
                        .name("DB_PORT")
                        .type("String")
                        .dataType("text")
                        .value(dbInstance.port().applyValue((port) -> port.toString()))
                        .build());

        var dbUsername = new Parameter("DB_USERNAME",
                ParameterArgs
                        .builder()
                        .name("DB_USERNAME")
                        .type("String")
                        .dataType("text")
                        .value(dbInstance.username())
                        .build());

        var dbPasswordSecret = new Secret("DB_PASSWORD",
                SecretArgs
                        .builder()
                        .name("DB_PASSWORD")
                        .build());

        new SecretVersion("DB_PASSWORD_VERSION",
                SecretVersionArgs
                        .builder()
                        .secretId(dbPasswordSecret.id())
                        .secretString(dbPassword.result())
                        .build());

        var dbName = new Parameter("DB_NAME",
                ParameterArgs
                        .builder()
                        .name("DB_NAME")
                        .type("String")
                        .dataType("text")
                        .value(dbInstance.dbName())
                        .build());

        return new ParametersResult(dbHost, dbPort, dbUsername, dbPasswordSecret, dbName);
    }
}
