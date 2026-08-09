import type { SQSHandler } from "aws-lambda";

export const handler: SQSHandler = (event) => {
    for (const record of event.Records) {
        const user = JSON.parse(record.body);
        console.warn(`send email to: ${user}`);
    }
};
