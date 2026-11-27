import * as Minio from "minio";
import { config } from "../config";
const client = new Minio.Client(config.minio);
export async function ensureBucket() {
  if (!(await client.bucketExists(config.minio.bucket))) await client.makeBucket(config.minio.bucket, "us-east-1");
  await client.setBucketPolicy(config.minio.bucket, JSON.stringify({ Version: "2012-10-17", Statement: [{ Effect: "Allow", Principal: { AWS: ["*"] }, Action: ["s3:GetObject"], Resource: [`arn:aws:s3:::${config.minio.bucket}/*`] }] }));
}
export async function uploadImage(objectName: string, data: Buffer, mimeType: string) { await client.putObject(config.minio.bucket, objectName, data, data.length, { "Content-Type": mimeType }); return `${config.minio.publicUrl}/${config.minio.bucket}/${objectName}`; }
export async function deleteImage(objectName: string) { try { await client.removeObject(config.minio.bucket, objectName); } catch (err) { console.error(`Failed to cleanup MinIO object: ${objectName}`, err); } }
