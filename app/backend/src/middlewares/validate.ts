import { NextFunction, Request, Response } from "express";
import { ZodTypeAny } from "zod";
export const validate = (schema: ZodTypeAny, source: "body" | "query" = "body") => (req: Request, res: Response, next: NextFunction) => {
  const result = schema.safeParse(req[source]);
  if (!result.success) return res.status(400).json({ error: "Validation failed", details: result.error.flatten() });
  (req as any)[source] = result.data; next();
};
