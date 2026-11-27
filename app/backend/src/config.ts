import "dotenv/config";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value || value.startsWith("CHANGE_ME")) {
    throw new Error(`Missing configuration: ${name} (check backend/.env)`);
  }
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 3000),
  databaseUrl: requiredEnv("DATABASE_URL"),
  jwtSecret: requiredEnv("JWT_SECRET"),
  jwtExpiresIn: process.env.JWT_EXPIRES_IN ?? "180d",
  minio: {
    endPoint: process.env.MINIO_ENDPOINT ?? "localhost",
    port: Number(process.env.MINIO_PORT ?? 9000),
    useSSL: process.env.MINIO_USE_SSL === "true",
    accessKey: requiredEnv("MINIO_ACCESS_KEY"),
    secretKey: requiredEnv("MINIO_SECRET_KEY"),
    bucket: process.env.MINIO_BUCKET ?? "report-images",
    publicUrl: process.env.MINIO_PUBLIC_URL ?? "http://localhost:9000"
  }
};
