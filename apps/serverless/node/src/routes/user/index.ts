import type { AppEnvironment } from "@/index";
import { Hono } from "hono";
import { router as deleteRouter } from "./index.delete";
import { router as getRouter } from "./index.get";
import { router as postRouter } from "./index.post";
import { router as putRouter } from "./index.put";

const userRouter = new Hono<AppEnvironment>();

userRouter.route("/", getRouter);
userRouter.route("/", postRouter);
userRouter.route("/", putRouter);
userRouter.route("/", deleteRouter);

export { userRouter };
