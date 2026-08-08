import { z } from "zod";

export const UserSchema = z.object({
    id: z.string(),
    name: z.string(),
    email: z.email(),
});

export const CreateUserSchema = UserSchema;

export const UpdateUserSchema = UserSchema.omit({ id: true }).safeExtend({
    id: z.string().optional(),
});
