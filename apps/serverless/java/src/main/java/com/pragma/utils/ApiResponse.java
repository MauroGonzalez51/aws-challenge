package com.pragma.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.util.Map;

public class ApiResponse {

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"
    );

    private ApiResponse() {}

    public static APIGatewayV2HTTPResponse ok(String body) {
        return build(200, body);
    }

    public static APIGatewayV2HTTPResponse created(String body) {
        return build(201, body);
    }

    public static APIGatewayV2HTTPResponse noContent() {
        return build(204, "");
    }

    public static APIGatewayV2HTTPResponse badRequest(String body) {
        return build(400, body);
    }

    public static APIGatewayV2HTTPResponse notFound(String body) {
        return build(404, body);
    }

    public static APIGatewayV2HTTPResponse error(String body) {
        return build(500, body);
    }

    private static APIGatewayV2HTTPResponse build(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(body)
                .build();
    }
}
