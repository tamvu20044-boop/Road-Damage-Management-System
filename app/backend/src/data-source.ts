import { DataSource } from "typeorm";
import { config } from "./config";
import { User } from "./models/User";
import { Report } from "./models/Report";
export const appDataSource = new DataSource({ type: "postgres", url: config.databaseUrl, entities: [User, Report], synchronize: false, logging: false });
