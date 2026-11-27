import "reflect-metadata";
import cors from "cors";
import express from "express";
import { authRouter } from "./routes/auth.routes";
import { reportRouter } from "./routes/report.routes";
import { errorHandler } from "./middlewares/error";
export const app = express();
app.use(cors());
app.use((req, res, next) => {
  const start = Date.now();
  res.on("finish", () => {
    console.log(`[HTTP] ${req.method} ${req.originalUrl} -> ${res.statusCode} (${Date.now() - start}ms)`);
  });
  next();
});
app.use(express.json());
app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.use("/auth", authRouter);
app.use("/reports", reportRouter);
app.use(errorHandler);
