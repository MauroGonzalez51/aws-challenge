import { Container } from "@inferdi/inferdi";
import { consola } from "@/lib/logger";
import { dynamoDBClient } from "@/lib/client";

export class RequestContext {
    requestId: string = "";
}

export function buildRootContainer() {
    return new Container()
        .registerClass("request", RequestContext, [], "scoped")
        .registerFactory("logger", () => consola, "singleton")
        .registerFactory("client", dynamoDBClient, "singleton");
}
