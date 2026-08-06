package myproject.resources;

import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.apigatewayv2.ApiArgs;
import com.pulumi.aws.apigatewayv2.Integration;
import com.pulumi.aws.apigatewayv2.IntegrationArgs;
import com.pulumi.aws.apigatewayv2.Route;
import com.pulumi.aws.apigatewayv2.RouteArgs;
import com.pulumi.aws.apigatewayv2.Stage;
import com.pulumi.aws.apigatewayv2.StageArgs;
import com.pulumi.aws.lb.LoadBalancer;
import com.pulumi.core.Output;

public class ApiGateway {
    public record ApiGatewayResult(Api api, Stage stage) {
    }

    public record ApiGatewayConfig(String apiName, String stageName) {
        public static final ApiGatewayConfig DEFAULT = new ApiGatewayConfig(
                "users-service-api",
                "prod");
    }

    public static ApiGatewayResult setup(LoadBalancer loadBalancer) {
        var config = ApiGatewayConfig.DEFAULT;

        // HTTP API Gateway — lightweight proxy that routes requests to the ALB
        Api api = new Api(config.apiName(),
                ApiArgs.builder()
                        .name(config.apiName())
                        .protocolType("HTTP")
                        .build());

        // Base URL of the ALB
        Output<String> albBaseUrl = loadBalancer.dnsName()
                .applyValue(dns -> "http://" + dns);

        // Integration for POST /users — forwards to ALB's /users endpoint
        Integration postIntegration = new Integration("users-post-integration",
                IntegrationArgs.builder()
                        .apiId(api.id())
                        .integrationType("HTTP_PROXY")
                        .integrationMethod("POST")
                        .integrationUri(albBaseUrl.applyValue(url -> url + "/users"))
                        .connectionType("INTERNET")
                        .build());

        // Integration for GET /users/{id} — forwards to ALB's /users/{id} endpoint
        Integration getIntegration = new Integration("users-get-integration",
                IntegrationArgs.builder()
                        .apiId(api.id())
                        .integrationType("HTTP_PROXY")
                        .integrationMethod("GET")
                        .integrationUri(albBaseUrl.applyValue(url -> url + "/users/{id}"))
                        .connectionType("INTERNET")
                        .build());

        // Route: POST /users — maps to createUser endpoint
        new Route("users-post-route",
                RouteArgs.builder()
                        .apiId(api.id())
                        .routeKey("POST /users")
                        .target(postIntegration.id().applyValue(id -> "integrations/" + id))
                        .build());

        // Route: GET /users/{id} — maps to getUser endpoint
        new Route("users-get-route",
                RouteArgs.builder()
                        .apiId(api.id())
                        .routeKey("GET /users/{id}")
                        .target(getIntegration.id().applyValue(id -> "integrations/" + id))
                        .build());

        // Stage — auto-deploy enabled so routes are immediately available
        Stage stage = new Stage(config.stageName(),
                StageArgs.builder()
                        .apiId(api.id())
                        .name(config.stageName())
                        .autoDeploy(true)
                        .build());

        return new ApiGatewayResult(api, stage);
    }
}
