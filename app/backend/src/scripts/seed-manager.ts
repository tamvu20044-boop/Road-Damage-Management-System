import "dotenv/config";
import bcrypt from "bcrypt";
import { appDataSource } from "../data-source";
import { User } from "../models/User";

async function main() {
  const email = process.env.MANAGER_EMAIL?.trim().toLowerCase();
  const password = process.env.MANAGER_PASSWORD;
  if (!email || !password || password.length < 8) {
    throw new Error("Set MANAGER_EMAIL and MANAGER_PASSWORD (minimum 8 characters) in .env before creating a manager.");
  }

  await appDataSource.initialize();
  const users = appDataSource.getRepository(User);
  const passwordHash = await bcrypt.hash(password, 12);
  const existing = await users.findOneBy({ email });
  if (existing) {
    existing.role = "manager";
    existing.passwordHash = passwordHash;
    await users.save(existing);
    console.log(`Updated manager account: ${email}`);
  } else {
    await users.save(users.create({ email, passwordHash, role: "manager" }));
    console.log(`Created manager account: ${email}`);
  }
  await appDataSource.destroy();
}

main().catch(async (error) => {
  console.error(error);
  if (appDataSource.isInitialized) await appDataSource.destroy();
  process.exit(1);
});
