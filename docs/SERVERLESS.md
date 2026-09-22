# Serverless stack (HU7, HU8, HU9)

4 Lambdas (create/get/update/delete user) detrás de un API Gateway HTTP, DynamoDB (`UsersTable`), SQS (`UserCreatedQueue`) y SNS (`UserNotificationTopic`). Dos implementaciones equivalentes: `apps/serverless/node` (Node 24 + Hono) y `apps/serverless/java` (Java 21). Todo se corre con las tareas de moon (`serverless-node:sls` / `serverless-java:sls`), que ya invocan el `serverless` local instalado como devDependency de cada app.

⚠️ **Node y java no se pueden desplegar al mismo tiempo en el mismo stage/región**: ambos usan nombres físicos fijos e iguales (`users-table-${stage}`, `user-created-queue-${stage}`, `user-notification-topic-${stage}`) en `serverless.ts`. El segundo `deploy` falla por colisión de nombre. Para probar el otro stack, primero hacer `remove` del que esté arriba.

## Prerrequisitos

- AWS CLI con credenciales en el profile `default` (ya configurado, región `us-east-1`).
- `pnpm install` desde la raíz del repo.
- Login de Serverless Framework (una sola vez, por el campo `org` en `serverless.ts`):
  ```bash
  moon run serverless-node:sls -- login
  ```
- Stack java: JDK 21 para el build de gradle (se dispara solo, ver abajo).

## Deploy

```bash
moon run serverless-node:sls -- deploy --stage dev
# o
moon run serverless-java:sls -- deploy --stage dev   # compila el jar (gradle build) automático antes de desplegar
```

Imprime los endpoints y nombres de función al terminar — copiar el host del endpoint (`https://<api-id>.execute-api.us-east-1.amazonaws.com`) para lo de abajo; **cambia en cada deploy**, no es fijo.

## Probar los endpoints (curl / Insomnia)

Insomnia: **Import → From Clipboard**, pegar cualquiera de estos bloques (reemplazar `$API` por el host real que imprimió el `deploy`).

```bash
API="https://<api-id>.execute-api.us-east-1.amazonaws.com"

curl -X POST "$API/users" \
  -H "Content-Type: application/json" \
  -d '{"id":"u1","name":"Mauro","email":"mauro@example.com"}'
```

```bash
curl "$API/users/u1"
```

```bash
curl -X PUT "$API/users/u1" \
  -H "Content-Type: application/json" \
  -d '{"id":"u1","name":"Mauro Gonzalez","email":"mauro@example.com"}'
```

```bash
curl -X DELETE "$API/users/u1"
```

Swagger por función (solo stack node, generado con `hono-openapi`): `$API/docs/{create-user,get-user,update-user,delete-user}/swagger`.

## Validar usuarios en DynamoDB

No hay cliente GUI instalado en la máquina. Opciones:

- **AWS CLI** (ya alcanza para validar, y es lo que deja la HU8 en vez de exponer la tabla sin auth a internet):
  ```bash
  aws dynamodb get-item --table-name users-table-dev --key '{"id": {"S": "u1"}}'
  aws dynamodb scan --table-name users-table-dev
  aws dynamodb query --table-name users-table-dev --index-name email-index \
    --key-condition-expression "email = :e" \
    --expression-attribute-values '{":e": {"S": "mauro@example.com"}}'
  ```
- **AWS Console** → DynamoDB → Tables → `users-table-dev` → Explore table items (misma cuenta/región `us-east-1`).
- **NoSQL Workbench** (app de escritorio de AWS) si querés un cliente gráfico local, se conecta con el mismo profile `default`.

Sobre el requisito "accesible por cualquier persona en cualquier parte del mundo": DynamoDB no expone un modo de acceso público no autenticado nativo, y contradice el punto 4 de la misma HU ("privada, solo accesible vía las 4 lambdas"). Se resuelve dejando los comandos AWS CLI de arriba — cualquiera con credenciales IAM del proyecto puede leer, sin necesidad de exponer la tabla a internet sin auth.

## SQS / SNS manual

```bash
QUEUE_URL=$(aws sqs get-queue-url --queue-name user-created-queue-dev --output text)
aws sqs send-message --queue-url "$QUEUE_URL" \
  --message-body '{"id":"u2","name":"Prueba","email":"prueba@example.com"}'
```

Dispara `sendEmail` automático (event source mapping ya configurado), que publica en SNS. Para recibir la notificación hay que suscribirse (SNS no manda nada sin subscriptores):

```bash
TOPIC_ARN=$(aws sns list-topics --query "Topics[?contains(TopicArn, 'user-notification-topic-dev')].TopicArn" --output text)
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol email --notification-endpoint tu-correo@example.com
```

AWS manda un correo de confirmación; hay que confirmarlo antes de que lleguen las publicaciones.

## Logs

```bash
moon run serverless-node:sls -- logs -f sendEmail --stage dev --tail
# o directo CloudWatch:
aws logs tail /aws/lambda/aws-challenge-serverless-node-dev-sendEmail --follow
```

## Cleanup

```bash
moon run serverless-node:sls -- remove --stage dev
moon run serverless-java:sls -- remove --stage dev
```

## Pendientes detectados

- SNS/SQS restringen acceso por IAM role por función, no hay resource policy en el tópico SNS — revisar si HU9 exige esa policy explícita además del IAM.
- DynamoDB no está en VPC ni tiene VPC endpoint — el aislamiento "solo vía las 4 lambdas" hoy es solo IAM, no de red.
