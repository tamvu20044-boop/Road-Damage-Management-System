import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";
import { appDataSource } from "../data-source";
import { config } from "../config";
import { User } from "../models/User";
export async function register(email: string, password: string) { const repo = appDataSource.getRepository(User); if (await repo.existsBy({ email })) throw new Error("EMAIL_EXISTS"); return repo.save(repo.create({ email, passwordHash: await bcrypt.hash(password, 12), role: "user" })); }
export async function login(email: string, password: string) { const user = await appDataSource.getRepository(User).findOneBy({ email }); if (!user || !(await bcrypt.compare(password, user.passwordHash))) throw new Error("INVALID_CREDENTIALS"); const token = jwt.sign({ userId: user.id, role: user.role }, config.jwtSecret, { expiresIn: config.jwtExpiresIn as jwt.SignOptions["expiresIn"] }); return { user, token }; }
