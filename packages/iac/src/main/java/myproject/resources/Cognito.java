package myproject.resources;

import com.pulumi.aws.cognito.UserPool;
import com.pulumi.aws.cognito.UserPoolArgs;
import com.pulumi.aws.cognito.UserPoolClient;
import com.pulumi.aws.cognito.UserPoolClientArgs;

public class Cognito {
    public record CognitoResult(UserPool userPool, UserPoolClient userPoolClient) {
    }

    public record CognitoConfig(String userPoolName, String userPoolClientName) {
        public static final CognitoConfig DEFAULT = new CognitoConfig(
                "users-service-user-pool",
                "users-service-user-pool-client");
    }

    public static CognitoResult setup() {
        var config = CognitoConfig.DEFAULT;

        var userPool = new UserPool(config.userPoolName(), UserPoolArgs.Empty);

        var userPoolClient = new UserPoolClient(config.userPoolClientName(),
                UserPoolClientArgs.builder().userPoolId(userPool.id())
                        .explicitAuthFlows("ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH")
                        .build());

        return new CognitoResult(userPool, userPoolClient);
    }
}
