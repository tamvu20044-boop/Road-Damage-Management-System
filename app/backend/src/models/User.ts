import { Column, CreateDateColumn, Entity, PrimaryGeneratedColumn } from "typeorm";
export type UserRole = "user" | "manager";
@Entity("users")
export class User {
  @PrimaryGeneratedColumn("uuid") id!: string;
  @Column({ unique: true }) email!: string;
  @Column({ name: "password_hash" }) passwordHash!: string;
  @Column({ type: "enum", enum: ["user", "manager"], default: "user" }) role!: UserRole;
  @CreateDateColumn({ name: "created_at" }) createdAt!: Date;
}
