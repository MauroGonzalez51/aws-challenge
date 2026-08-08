import { z } from "zod";

export const ErrorSchema = z.object({ error: z.string(), data: z.unknown().optional() });
