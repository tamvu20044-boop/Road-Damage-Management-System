import { appDataSource } from "./data-source";
import { config } from "./config";
import { ensureBucket } from "./services/storage.service";
import { app } from "./app";
async function main() { await appDataSource.initialize(); await appDataSource.query("CREATE EXTENSION IF NOT EXISTS postgis"); await appDataSource.synchronize(); await ensureBucket(); app.listen(config.port, () => console.log(`API listening on :${config.port}`)); }
main().catch(error => { console.error("Startup failed", error); process.exit(1); });
