package myproject.resources;

import java.util.Map;

import com.pulumi.aws.cloudwatch.MetricAlarm;
import com.pulumi.aws.cloudwatch.MetricAlarmArgs;
import com.pulumi.aws.ecs.Cluster;
import com.pulumi.aws.ecs.Service;
import com.pulumi.aws.lb.LoadBalancer;
import com.pulumi.core.Output;

public class Alarms {
    public record AlarmsResult(MetricAlarm ecsCpuAlarm, MetricAlarm alb5xxAlarm) {
    }

    public static AlarmsResult setup(Cluster cluster, Service service, LoadBalancer loadBalancer) {
        // Alarm 1: ECS CPU Utilization > 80%
        MetricAlarm ecsCpuAlarm = new MetricAlarm("ecs-cpu-high-alarm",
                MetricAlarmArgs.builder()
                        .alarmDescription("ECS service CPU utilization exceeds 80%")
                        .metricName("CPUUtilization")
                        .namespace("AWS/ECS")
                        .statistic("Average")
                        .period(60)
                        .evaluationPeriods(1)
                        .threshold(1.0)
                        .comparisonOperator("GreaterThanThreshold")
                        .dimensions(Output.all(cluster.name(), service.name())
                                .applyValue(values -> Map.of(
                                        "ClusterName", values.get(0),
                                        "ServiceName", values.get(1))))
                        .build());

        // Alarm 2: ALB Target 5XX errors > 0
        MetricAlarm alb5xxAlarm = new MetricAlarm("alb-5xx-alarm",
                MetricAlarmArgs.builder()
                        .alarmDescription("ALB is receiving 5XX errors from targets")
                        .metricName("HTTPCode_Target_5XX_Count")
                        .namespace("AWS/ApplicationELB")
                        .statistic("Sum")
                        .period(60)
                        .evaluationPeriods(1)
                        .threshold(0.0)
                        .comparisonOperator("GreaterThanThreshold")
                        .treatMissingData("notBreaching")
                        .dimensions(loadBalancer.arnSuffix()
                                .applyValue(arn -> Map.of("LoadBalancer", arn)))
                        .build());

        return new AlarmsResult(ecsCpuAlarm, alb5xxAlarm);
    }
}
