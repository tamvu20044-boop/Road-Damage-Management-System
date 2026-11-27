import { Column, CreateDateColumn, Entity, Index, ManyToOne, PrimaryGeneratedColumn, UpdateDateColumn } from "typeorm";
import { User } from "./User";
export type DamageType = "D10" | "D20" | "D40" | string;
export type ReportStatus = "moi_phat_hien" | "dang_xu_ly" | "da_sua";
@Entity("reports")
export class Report {
  @PrimaryGeneratedColumn("uuid") id!: string;
  @ManyToOne(() => User, { nullable: false }) user!: User;
  @Column({ type: "varchar", length: 100, name: "damage_type" }) damageType!: string;
  @Column("double precision") confidence!: number;
  @Index("IDX_reports_location_gist", { spatial: true })
  @Column({ type: "geography", spatialFeatureType: "Point", srid: 4326 }) location!: { type: "Point"; coordinates: [number, number] };
  @Column("double precision", { nullable: true }) heading!: number | null;
  @Column({ name: "image_url" }) imageUrl!: string;
  @Column({ type: "enum", enum: ["moi_phat_hien", "dang_xu_ly", "da_sua"], default: "moi_phat_hien" }) status!: ReportStatus;
  @Column({ name: "occurrence_count", default: 1 }) occurrenceCount!: number;
  @CreateDateColumn({ name: "created_at" }) createdAt!: Date;
  @UpdateDateColumn({ name: "updated_at" }) updatedAt!: Date;
}
