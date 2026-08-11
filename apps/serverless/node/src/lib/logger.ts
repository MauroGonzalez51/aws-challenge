import type { ConsolaInstance } from "consola";
import process from "node:process";
import { createConsola } from "consola";

function setupLogger(): ConsolaInstance {
    // eslint-disable-next-line dot-notation
    if (process.env["NODE_ENV"] && process.env["NODE_ENV"] === "test") {
        return createConsola({ level: -999 });
    }

    return createConsola({ level: 4 });
}

export const consola = setupLogger();
