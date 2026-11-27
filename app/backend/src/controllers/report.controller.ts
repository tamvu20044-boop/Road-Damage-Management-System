import { Request, Response } from "express";
import { randomUUID } from "node:crypto";
import { appDataSource } from "../data-source";
import { Report } from "../models/Report";
import { uploadImage, deleteImage } from "../services/storage.service";
import { findNearbyDuplicate, listReports } from "../services/report.service";

export function formatGeoPoint(loc: any): { type: "Point"; coordinates: [number, number] } | null {
  if (!loc) return null;
  if (typeof loc === "object" && Array.isArray(loc.coordinates) && loc.coordinates.length >= 2) {
    return { type: "Point", coordinates: [Number(loc.coordinates[0]), Number(loc.coordinates[1])] };
  }
  if (typeof loc === "string") {
    if (loc.startsWith("{")) {
      try {
        const parsed = JSON.parse(loc);
        if (Array.isArray(parsed.coordinates) && parsed.coordinates.length >= 2) {
          return { type: "Point", coordinates: [Number(parsed.coordinates[0]), Number(parsed.coordinates[1])] };
        }
      } catch {}
    }
    // Parse WKB / EWKB hex string from PostgreSQL/PostGIS
    try {
      const buf = Buffer.from(loc, "hex");
      if (buf.length >= 21) {
        const isLittleEndian = buf.readUInt8(0) === 1;
        const type = isLittleEndian ? buf.readUInt32LE(1) : buf.readUInt32BE(1);
        let offset = 5;
        const hasSRID = (type & 0x20000000) !== 0;
        if (hasSRID) offset += 4;
        const x = isLittleEndian ? buf.readDoubleLE(offset) : buf.readDoubleBE(offset);
        const y = isLittleEndian ? buf.readDoubleLE(offset + 8) : buf.readDoubleBE(offset + 8);
        return { type: "Point", coordinates: [x, y] };
      }
    } catch {}
  }
  return null;
}

const reportView = (report: Report) => ({
  id: report.id,
  damageType: report.damageType,
  confidence: report.confidence,
  status: report.status,
  occurrenceCount: report.occurrenceCount,
  createdAt: report.createdAt,
  imageUrl: report.imageUrl,
  location: formatGeoPoint(report.location),
  heading: report.heading,
  user: report.user ? { id: report.user.id, email: report.user.email } : undefined
});

export async function createReport(req: Request, res: Response) {
  if (!req.file) return res.status(400).json({ error: "Image file is required" });
  const { lat, lng, heading, damageType, confidence } = req.body;

  // 1. Kiểm tra báo cáo trùng lặp (Dedup 15m trong 24h)
  const duplicate = await findNearbyDuplicate(Number(lng), Number(lat), damageType);
  if (duplicate) {
    duplicate.occurrenceCount++;
    await appDataSource.getRepository(Report).save(duplicate);
    return res.status(200).json({ report: reportView(duplicate), deduplicated: true });
  }

  // 2. Sinh ID trước để quản lý vòng đời tệp
  const reportId = randomUUID();
  const objectName = `${reportId}_${Date.now()}.jpg`;

  // 3. Tải ảnh lên MinIO trước
  let imageUrl: string;
  try {
    imageUrl = await uploadImage(objectName, req.file.buffer, req.file.mimetype);
  } catch (uploadErr) {
    console.error("Failed to upload image to storage service", uploadErr);
    return res.status(502).json({ error: "Could not upload image to storage service" });
  }

  // 4. Lưu vào Database (1 lần duy nhất). Nếu lưu DB lỗi, chạy compensating cleanup xóa ảnh khỏi MinIO
  try {
    const repo = appDataSource.getRepository(Report);
    const report = repo.create({
      id: reportId,
      user: { id: req.auth!.userId } as any,
      damageType,
      confidence: Number(confidence),
      heading: heading === undefined || heading === null ? null : Number(heading),
      location: { type: "Point", coordinates: [Number(lng), Number(lat)] },
      imageUrl
    });
    const saved = await repo.save(report);
    return res.status(201).json({ report: reportView(saved), deduplicated: false });
  } catch (dbErr) {
    console.error("Database save failed, rolling back MinIO object", dbErr);
    await deleteImage(objectName);
    return res.status(500).json({ error: "Could not save report to database" });
  }
}

export async function getReports(req: Request, res: Response) {
  try {
    const result = await listReports(req.query as any);
    return res.json({ ...result, items: result.items.map(reportView) });
  } catch (error) {
    console.error("Could not list reports", error);
    return res.status(500).json({ error: "Could not load reports" });
  }
}

export async function getReport(req: Request, res: Response) {
  const report = await appDataSource.getRepository(Report).findOne({
    where: { id: String(req.params.id) },
    relations: { user: true }
  });
  return report ? res.json({ report: reportView(report) }) : res.status(404).json({ error: "Report not found" });
}

export async function updateStatus(req: Request, res: Response) {
  const repo = appDataSource.getRepository(Report);
  const report = await repo.findOneBy({ id: String(req.params.id) });
  if (!report) return res.status(404).json({ error: "Report not found" });
  if (req.body.status) report.status = req.body.status;
  if (req.body.damageType) report.damageType = req.body.damageType;
  const updated = await repo.save(report);
  return res.json({ report: reportView(updated) });
}

export async function deleteReport(req: Request, res: Response) {
  const repo = appDataSource.getRepository(Report);
  const report = await repo.findOne({
    where: { id: String(req.params.id) },
    relations: { user: true }
  });
  if (!report) return res.status(404).json({ error: "Report not found" });

  if (req.auth?.role !== "manager" && req.auth?.userId !== report.user?.id) {
    return res.status(403).json({ error: "Permission denied" });
  }

  if (report.imageUrl) {
    try {
      const parts = report.imageUrl.split("/");
      const objectName = parts[parts.length - 1];
      if (objectName) {
        await deleteImage(objectName);
      }
    } catch (err) {
      console.error("Could not delete image from MinIO:", err);
    }
  }

  await repo.remove(report);
  return res.json({ success: true, message: "Report deleted" });
}

