import { z } from "zod";

export const UserSchema = z.object({
    userId: z.string(),
    name: z.string(),
});

export const CreateUserSchema = UserSchema;
