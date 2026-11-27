import { Request, Response } from "express";
import * as auth from "../services/auth.service";
const view = (user: { id: string; email: string; role: string }, token: string) => ({ accessToken: token, user: { id: user.id, email: user.email, role: user.role } });
export async function register(req: Request, res: Response) { try { const user = await auth.register(req.body.email, req.body.password); const { token } = await auth.login(req.body.email, req.body.password); return res.status(201).json(view(user, token)); } catch (e) { return res.status((e as Error).message === "EMAIL_EXISTS" ? 409 : 500).json({ error: (e as Error).message === "EMAIL_EXISTS" ? "Email already registered" : "Could not register" }); } }
export async function login(req: Request, res: Response) { try { const { user, token } = await auth.login(req.body.email, req.body.password); return res.json(view(user, token)); } catch { return res.status(401).json({ error: "Invalid email or password" }); } }
