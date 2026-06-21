const path = require("path");

const PROJECT_ID = "slagalica-30ba3";
const DEFAULT_DELAY_SECONDS = 45;
const firebaseToolsRoot = path.join(
  process.env.APPDATA,
  "npm",
  "node_modules",
  "firebase-tools",
  "lib"
);
const auth = require(path.join(firebaseToolsRoot, "auth"));
const { Client } = require(path.join(firebaseToolsRoot, "apiv2"));

function integer(value) {
  return { integerValue: String(Math.trunc(value)) };
}

function bool(value) {
  return { booleanValue: Boolean(value) };
}

function string(value) {
  return { stringValue: String(value == null ? "" : value) };
}

function documentWrite(relativePath, fields) {
  return {
    update: {
      name: `projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`,
      fields,
    },
  };
}

function currentWeeklyCycleId() {
  const now = new Date();
  const local = new Date(now.toLocaleString("en-US", { timeZone: "Europe/Belgrade" }));
  const monday = new Date(local);
  const day = monday.getDay() || 7;
  monday.setDate(monday.getDate() - day + 1);
  return `W_${monday.getFullYear()}${String(monday.getMonth() + 1).padStart(2, "0")}${String(monday.getDate()).padStart(2, "0")}`;
}

function field(document, key, type, fallback) {
  const value = document && document.fields && document.fields[key];
  return value && Object.prototype.hasOwnProperty.call(value, type) ? value[type] : fallback;
}

async function main() {
  const delaySeconds = Math.max(15, Number(process.argv[2] || DEFAULT_DELAY_SECONDS));
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error("Firebase CLI nije prijavljen. Pokreni firebase login --reauth.");
  auth.setActiveAccount({}, account);

  const client = new Client({
    urlPrefix: "https://firestore.googleapis.com",
    apiVersion: "v1",
  });
  const sourceCycleId = currentWeeklyCycleId();
  const response = await client.get(
    `/projects/${PROJECT_ID}/databases/(default)/documents/leaderboardCycles/${sourceCycleId}/entries`,
    { queryParams: { pageSize: 100 } }
  );
  const entries = Array.isArray(response.body && response.body.documents)
    ? response.body.documents
    : [];
  if (entries.length === 0) {
    throw new Error("Trenutna nedeljna rang-lista nema igraca. Prvo odigraj jednu rangiranu partiju.");
  }

  const now = Date.now();
  const endMs = now + delaySeconds * 1000;
  const cycleId = `W_DEMO_${now}`;
  const writes = [documentWrite(`leaderboardCycles/${cycleId}`, {
    monthly: bool(false),
    startMs: integer(now - 6 * 24 * 60 * 60 * 1000),
    endMs: integer(endMs),
    processed: bool(false),
    processing: bool(false),
    demoData: bool(true),
  })];

  for (const entry of entries) {
    const uid = String(entry.name || "").split("/").pop();
    if (!uid) continue;
    writes.push(documentWrite(`leaderboardCycles/${cycleId}/entries/${uid}`, {
      username: string(field(entry, "username", "stringValue", uid)),
      league: integer(Number(field(entry, "league", "integerValue", 0))),
      cycleStars: integer(Number(field(entry, "cycleStars", "integerValue", 0))),
      cycleMatches: integer(Math.max(1, Number(field(entry, "cycleMatches", "integerValue", 1)))),
      updatedAtMillis: integer(now),
      demoData: bool(true),
    }));
  }

  await client.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );

  console.log(`Demo ciklus: ${cycleId}`);
  console.log(`Igraca: ${writes.length - 1}`);
  console.log(`Zavrsava se za ${delaySeconds} sekundi, u ${new Date(endMs).toLocaleTimeString("sr-RS")}.`);
  console.log("Ostavi realtime-server pokrenut i aplikaciju primaoca u pozadini.");
}

main().catch((error) => {
  console.error(error && error.message ? error.message : error);
  process.exitCode = 1;
});
