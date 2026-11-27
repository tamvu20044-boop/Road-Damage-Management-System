import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";
import { config } from "../config";
import { UserRole } from "../models/User";
export function requireAuth(req: Request, res: Response, next: NextFunction) {
  const token = req.header("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return res.status(401).json({ error: "Authentication required" });
  try { req.auth = jwt.verify(token, config.jwtSecret) as { userId: string; role: UserRole }; next(); }
  catch { return res.status(401).json({ error: "Invalid or expired token" }); }
}
export function requireRole(role: UserRole) { return (req: Request, res: Response, next: NextFunction) => req.auth?.role === role ? next() : res.status(403).json({ error: "Insufficient permission" }); }
