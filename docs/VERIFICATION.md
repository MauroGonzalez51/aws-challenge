# Verification

Region: `us-east-1`. Stack: `local`.

## Outputs (Pulumi)

## Get Outputs

```bash
export AWS_PROFILE=default
export AWS_REGION=us-east-1

export ECS_CLUSTER="$(pulumi stack output ECS_CLUSTER_NAME)"
export API_URL="$(pulumi stack output API_GATEWAY_URL)"
export ALB_DNS="$(pulumi stack output ALB_DNS_NAME)"
export USER_POOL_ID="$(pulumi stack output USER_POOL_ID)"
export CLIENT_ID="$(pulumi stack output USER_POOL_CLIENT_ID)"
```

## ECS and Load Balancer

```bash
export ECS_SERVICE="$(
  aws ecs list-services \
    --cluster "$ECS_CLUSTER" \
    --query 'serviceArns[0]' \
    --output text
)"

echo $ECS_SERVICE

aws ecs describe-services \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount,taskDefinition:taskDefinition}' \
  --output table
```

## Debug Session — 2026-09-22

Symptom: ECS service had `running=0`, `desired=1`. Tasks kept exiting with code 1.

### 1. Inspect stack state

```bash
pulumi stack --show-name
pulumi stack output
```

### 2. Check ECS service and events

```bash
C=$(pulumi stack output ECS_CLUSTER_NAME)
S=$(aws ecs list-services --cluster "$C" --query 'serviceArns[0]' --output text)

aws ecs describe-services --cluster "$C" --services "$S" \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount,pending:pendingCount,td:taskDefinition}' \
  --output json

aws ecs describe-services --cluster "$C" --services "$S" \
  --query 'services[0].events[0:3].message' --output json
```

### 3. Inspect the stopped task

```bash
T=$(aws ecs list-tasks --cluster "$C" --desired-status STOPPED --query 'taskArns[0]' --output text)

aws ecs describe-tasks --cluster "$C" --tasks "$T" \
  --query 'tasks[0].{stopCode:stopCode,stoppedReason:stoppedReason,lastStatus:lastStatus,containers:containers[].{name:name,exitCode:exitCode,reason:reason}}' \
  --output json
```

Result: `stopCode=EssentialContainerExited`, container `exitCode=1`.

### 4. Read container logs

```bash
aws ecs describe-task-definition --task-definition users-service-task:6 \
  --query 'taskDefinition.containerDefinitions[0].logConfiguration' --output json

aws logs tail users-service-logs-a3ad231 --since 30m --format short | tail -40
```

Root cause found in logs:

```
java.net.UnknownHostException: users-service-dbe1df488.cqfc0ewqwthe.us-east-1.rds.amazonaws.com
```

The application could not resolve the RDS endpoint. The Pulumi state referenced an
RDS instance that no longer existed in AWS (state drift). Confirmed:

```bash
aws rds describe-db-instances --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output text
# returned empty
```

### 5. Rebuild the stack

```bash
pulumi destroy --yes
pulumi up --yes
```

The first `up` failed on one resource:

```
InvalidRequestException: You can't create this secret because a secret with this name is already scheduled for deletion.
```

Fix (see note below), then re-run `up`:

```bash
aws secretsmanager list-secrets --include-planned-deletion \
  --query 'SecretList[?starts_with(Name,`DB_PASSWORD`)].[Name,ARN,DeletedDate]' --output json

aws secretsmanager delete-secret --secret-id DB_PASSWORD \
  --force-delete-without-recovery

pulumi up --yes
```

### 6. Verify the service is healthy

```bash
C=$(pulumi stack output ECS_CLUSTER_NAME)
S=$(aws ecs list-services --cluster "$C" --query 'serviceArns[0]' --output text)

aws ecs describe-services --cluster "$C" --services "$S" \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount,pending:pendingCount}' \
  --output json
# -> running=1, desired=1
```

Confirm DB connectivity and app startup in logs:

```bash
LG=$(aws logs describe-log-groups \
  --query 'logGroups[?contains(logGroupName,`users-service-logs`)].logGroupName|[0]' --output text)

aws logs tail "$LG" --since 4m --format short \
  | grep -i -E "started|Tomcat|error|exception|Unknown|Hikari|listen"
# -> "HikariPool-1 - Added connection ... PgConnection"
# -> "Tomcat started on port 8080"
# -> "Started AwsApplication in 76.097 seconds"
```

### 7. Verify target health and endpoints

```bash
TG=$(aws elbv2 describe-target-groups \
  --query 'TargetGroups[?contains(TargetGroupName,`users-service-tg`)].TargetGroupArn|[0]' --output text)

# health check path configured on the target group
aws elbv2 describe-target-groups --target-group-arns "$TG" \
  --query 'TargetGroups[0].HealthCheckPath' --output text
# -> /health

aws elbv2 describe-target-health --target-group-arn "$TG" \
  --query 'TargetHealthDescriptions[].{state:TargetHealth.State,reason:TargetHealth.Reason}' --output json
# -> healthy

ALB_DNS=$(pulumi stack output ALB_DNS_NAME)
curl -s -o /dev/null -w "%{http_code}\n" "http://${ALB_DNS}/health"
# -> 200
```

## Delete DB_PASSWORD

Secrets Manager does not delete a secret immediately. On `pulumi destroy`, the secret
enters a recovery window (7–30 days) where it is only *scheduled for deletion*, not
gone. The name stays reserved during that window. The next `pulumi up` tried to
create a new `DB_PASSWORD` with the same name and AWS rejected it with
`InvalidRequestException: ... scheduled for deletion`.

Fix: force-delete the secret without a recovery window
(`aws secretsmanager delete-secret --force-delete-without-recovery`), then re-run `up`.

To avoid this on future destroy/up cycles, either give the secret a unique/suffixed
name or force-delete it as part of teardown.

## End-to-End Flow — Auth (Cognito) + Users API

The API Gateway routes (`POST /users`, `GET /users/{id}`) both require a JWT
(`authorizationType=JWT`) validated against the Cognito user pool. The `users` app
itself only exposes the CRUD endpoints — authentication is handled entirely by
Cognito + the API Gateway JWT authorizer.

Flow: create a Cognito user -> log in to obtain an `IdToken` -> call the API with the
token in the `Authorization` header.

### 1. Setup vars

```bash
export AWS_PROFILE=default
export AWS_REGION=us-east-1

UP=$(pulumi stack output USER_POOL_ID)
CID=$(pulumi stack output USER_POOL_CLIENT_ID)
API=$(pulumi stack output API_GATEWAY_URL)

EMAIL="testuser@example.com"
PASS='Test1234!pass'
```

### 2. Create a Cognito user and set a permanent password

```bash
aws cognito-idp admin-create-user \
  --user-pool-id "$UP" --username "$EMAIL" \
  --message-action SUPPRESS \
  --user-attributes Name=email,Value="$EMAIL"

aws cognito-idp admin-set-user-password \
  --user-pool-id "$UP" --username "$EMAIL" \
  --password "$PASS" --permanent
```

### 3. Log in and capture the IdToken

The pool client enables `ALLOW_USER_PASSWORD_AUTH`, so `USER_PASSWORD_AUTH` works
without a client secret.

```bash
TOK=$(aws cognito-idp initiate-auth \
  --client-id "$CID" \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME="$EMAIL",PASSWORD="$PASS" \
  --query 'AuthenticationResult.IdToken' --output text)

echo "token-len=${#TOK}"   # -> ~1031
```

### 4. Call the API

```bash
# POST /users WITHOUT token -> 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API/users" \
  -H 'Content-Type: application/json' \
  -d '{"noIdentification":"CC123","name":"Ana","email":"ana@example.com"}'
# -> 401

# POST /users WITH token -> 201
curl -s -w "\n%{http_code}\n" -X POST "$API/users" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"noIdentification":"CC123","name":"Ana","email":"ana@example.com"}'
# -> {"id":1,"noIdentification":"CC123","name":"Ana","email":"ana@example.com"}  201

# GET /users/{id} WITH token -> 200
curl -s -w "\n%{http_code}\n" -X GET "$API/users/1" -H "Authorization: Bearer $TOK"
# -> {"id":1,...}  200

# GET /users/{id} WITHOUT token -> 401
curl -s -o /dev/null -w "%{http_code}\n" -X GET "$API/users/1"
# -> 401
```

### 5. Edge cases

```bash
# GET non-existent id -> 404
curl -s -o /dev/null -w "%{http_code}\n" -X GET "$API/users/999999" -H "Authorization: Bearer $TOK"
# -> 404

# POST duplicate email -> 500 (see finding below)
curl -s -w "\n%{http_code}\n" -X POST "$API/users" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"noIdentification":"CC999","name":"Ana2","email":"ana@example.com"}'
# -> {"status":500,"error":"Internal Server Error","path":"/users"}  500
```

### Results

| Case | Expected | Actual |
|------|----------|--------|
| POST /users no token | 401 | 401 |
| POST /users with token | 201 | 201 |
| GET /users/{id} with token | 200 | 200 |
| GET /users/{id} no token | 401 | 401 |
| GET non-existent id | 404 | 404 |
| POST duplicate email | 409 (ideal) | 500 |

### Finding: duplicate email returns 500 instead of 409

The domain throws `EmailAlreadyInUse` (see
`apps/users/src/main/java/com/pragma/aws/domain/exceptions/EmailAlreadyInUse.java`),
but there is no `@ExceptionHandler` / `@ControllerAdvice` mapping it to a 4xx
response, so Spring falls back to a generic 500. Not an infra problem — an
application-level gap. Consider adding an exception handler that maps
`EmailAlreadyInUse` / `NoIdentificationAlreadyInUse` to `409 Conflict`.

## Connect to RDS with psql (temporary, for manual verification)

The RDS instance is created with `publiclyAccessible=false` and lives in the default
VPC. Its subnets route `0.0.0.0/0` to an Internet Gateway, so making the instance
public + opening the security group to your IP is enough for a temporary direct
connection. This is a temporary override — it is NOT in the Pulumi code, so the next
`pulumi up` reverts it.

### Open access

```bash
export AWS_REGION=us-east-1
MYIP=$(curl -s https://checkip.amazonaws.com)
SG=sg-0114a97845feb678c   # the RDS security group

# allow 5432 from your IP only (never 0.0.0.0/0)
aws ec2 authorize-security-group-ingress --group-id "$SG" \
  --protocol tcp --port 5432 --cidr "${MYIP}/32"

# make the instance publicly reachable
aws rds modify-db-instance --db-instance-identifier users-service-dbb902258 \
  --publicly-accessible --apply-immediately

# wait until public=True and status=available
aws rds describe-db-instances --db-instance-identifier users-service-dbb902258 \
  --query 'DBInstances[0].{public:PubliclyAccessible,status:DBInstanceStatus}' --output json
```

### Connect

```bash
export AWS_REGION=us-east-1
HOST=$(aws rds describe-db-instances --db-instance-identifier users-service-dbb902258 \
  --query 'DBInstances[0].Endpoint.Address' --output text)
PASS=$(aws secretsmanager get-secret-value --secret-id DB_PASSWORD \
  --query 'SecretString' --output text)

PGPASSWORD="$PASS" psql -h "$HOST" -U users_admin -d aws_users -p 5432

# quick checks
#   \dt
#   SELECT * FROM users;
```

Verified: table `users` exists and holds the rows created via the API (e.g. id=1
`ana@example.com`), confirming persistence.

### Revert (do this after the review)

```bash
aws rds modify-db-instance --db-instance-identifier users-service-dbb902258 \
  --no-publicly-accessible --apply-immediately
aws ec2 revoke-security-group-ingress --group-id sg-0114a97845feb678c \
  --protocol tcp --port 5432 --cidr "$(curl -s https://checkip.amazonaws.com)/32"
```

(Or just run `pulumi up` — neither change is in code, so it reverts them.)

## Trigger CloudWatch Alarms

Two alarms are defined (`packages/iac/.../resources/Alarms.java`):

| Alarm | Metric | Condition |
|-------|--------|-----------|
| `ecs-cpu-high-alarm-*` | `AWS/ECS CPUUtilization` (Average, 60s) | `> 1%` |
| `alb-5xx-alarm-*` | `AWS/ApplicationELB HTTPCode_Target_5XX_Count` (Sum, 60s) | `> 0` |

### List alarms and current state

```bash
export AWS_REGION=us-east-1
aws cloudwatch describe-alarms \
  --query 'MetricAlarms[].{name:AlarmName,state:StateValue,metric:MetricName}' --output json
```

### Force both into ALARM (manual test)

```bash
aws cloudwatch set-alarm-state --alarm-name alb-5xx-alarm-5b5dfef \
  --state-value ALARM --state-reason "Manual test"
aws cloudwatch set-alarm-state --alarm-name ecs-cpu-high-alarm-83e150c \
  --state-value ALARM --state-reason "Manual test"

aws cloudwatch describe-alarms \
  --query 'MetricAlarms[].{name:AlarmName,state:StateValue,reason:StateReason}' --output json
# -> both StateValue = ALARM
```

### Trigger the 5XX alarm with real traffic (optional)

A duplicate email causes the app to return 500 (see the earlier finding), which
increments `HTTPCode_Target_5XX_Count`. The IdToken expires after ~1h, so refresh it
first (see the auth flow section), then:

```bash
API=$(pulumi stack output API_GATEWAY_URL)
for i in 1 2 3; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API/users" \
    -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
    -d '{"noIdentification":"CC123","name":"Ana","email":"ana@example.com"}'
done
# within ~1 min alb-5xx-alarm transitions to ALARM on its own
```

### Note: alarms have no actions

`Alarms.java` sets no `alarmActions` (no SNS topic), so the alarms change state but
send no notification. To actually notify (email/Slack), add an SNS topic and wire it
into `alarmActions` on both alarms.
