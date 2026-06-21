const fs = require("fs");
const path = require("path");

const PROJECT_ID = "slagalica-30ba3";

function loadClient() {
  const lib = path.join(process.env.APPDATA, "npm", "node_modules", "firebase-tools", "lib");
  const auth = require(path.join(lib, "auth"));
  const { Client } = require(path.join(lib, "apiv2"));
  const account = auth.getProjectDefaultAccount(process.cwd()) || auth.getGlobalDefaultAccount();
  if (!account) throw new Error("Firebase CLI nije prijavljen.");
  auth.setActiveAccount({}, account);
  return new Client({
    urlPrefix: "https://firestore.googleapis.com",
    apiVersion: "v1",
  });
}

function value(document, key) {
  const field = document && document.fields && document.fields[key];
  if (!field) return null;
  if (Object.prototype.hasOwnProperty.call(field, "stringValue")) return field.stringValue;
  if (Object.prototype.hasOwnProperty.call(field, "integerValue")) return Number(field.integerValue);
  if (Object.prototype.hasOwnProperty.call(field, "doubleValue")) return Number(field.doubleValue);
  if (Object.prototype.hasOwnProperty.call(field, "booleanValue")) return field.booleanValue;
  if (Object.prototype.hasOwnProperty.call(field, "timestampValue")) return field.timestampValue;
  return null;
}

function id(document) {
  return String(document.name || "").split("/").pop();
}

async function list(client, relativePath) {
  const response = await client.get(
    `/projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`,
    { queryParams: { pageSize: 500 } }
  );
  return (response.body && response.body.documents) || [];
}

async function main() {
  const client = loadClient();
  const cycles = await list(client, "leaderboardCycles");
  const output = [];
  for (const cycle of cycles) {
    if (value(cycle, "monthly") === true) continue;
    const cycleId = id(cycle);
    const entries = await list(client, `leaderboardCycles/${cycleId}/entries`);
    output.push({
      id: cycleId,
      startMs: value(cycle, "startMs"),
      endMs: value(cycle, "endMs"),
      processed: value(cycle, "processed"),
      entries: entries.map((entry) => ({
        uid: id(entry),
        username: value(entry, "username"),
        stars: value(entry, "cycleStars"),
        matches: value(entry, "cycleMatches"),
      })),
    });
  }
  console.log(JSON.stringify(output, null, 2));
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
