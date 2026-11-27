import { Router } from "express";
import { z } from "zod";
import * as controller from "../controllers/auth.controller";
import { validate } from "../middlewares/validate";
const schema = z.object({ email: z.string().email(), password: z.string().min(8).max(128) });
export const authRouter = Router(); authRouter.post("/register", validate(schema), controller.register); authRouter.post("/login", validate(schema), controller.login);
