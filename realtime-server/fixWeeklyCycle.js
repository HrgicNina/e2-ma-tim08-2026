const fs = require("fs");
const path = require("path");

const PROJECT_ID = "slagalica-30ba3";
const KEEP_CYCLE_ID = "W_20260615";
const TARGET_END_MS = 1782043946354;

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

function name(relativePath) {
  return `projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`;
}

function value(document, key) {
  const field = document && document.fields && document.fields[key];
  if (!field) return null;
  if (Object.prototype.hasOwnProperty.call(field, "stringValue")) return field.stringValue;
  if (Object.prototype.hasOwnProperty.call(field, "integerValue")) return Number(field.integerValue);
  if (Object.prototype.hasOwnProperty.call(field, "doubleValue")) return Number(field.doubleValue);
  if (Object.prototype.hasOwnProperty.call(field, "booleanValue")) return field.booleanValue;
  return null;
}

async function get(client, relativePath) {
  const response = await client.get(`/projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`);
  return response.body;
}

async function list(client, relativePath) {
  const response = await client.get(
    `/projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`,
    { queryParams: { pageSize: 500 } }
  );
  return (response.body && response.body.documents) || [];
}

function id(document) {
  return String(document.name || "").split("/").pop();
}

async function main() {
  const client = loadClient();
  const keep = await get(client, `leaderboardCycles/${KEEP_CYCLE_ID}`);
  const keepEntries = await list(client, `leaderboardCycles/${KEEP_CYCLE_ID}/entries`);
  const ivona = keepEntries.find((entry) => String(value(entry, "username") || "").toLowerCase() === "ivona");

  if (!keep) throw new Error("Nedostaje ciklus koji treba sacuvati.");
  if (!ivona || Number(value(ivona, "cycleStars")) <= 0) {
    throw new Error("Bezbednosna provera nije prosla: Ivonine zvezde nisu na ciklusu koji se cuva.");
  }
  if (TARGET_END_MS <= Date.now()) {
    throw new Error("Bezbednosna provera nije prosla: vreme 14:12 nije validno ili je vec proslo.");
  }

  const writes = [
    {
      update: {
        name: name(`leaderboardCycles/${KEEP_CYCLE_ID}`),
        fields: {
          endMs: { integerValue: String(TARGET_END_MS) },
          processed: { booleanValue: false },
          updatedAtMillis: { integerValue: String(Date.now()) },
        },
      },
      updateMask: { fieldPaths: ["endMs", "processed", "updatedAtMillis"] },
    },
  ];

  await client.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );

  console.log(JSON.stringify({
    kept: KEEP_CYCLE_ID,
    ivonaStars: value(ivona, "cycleStars"),
    endMs: TARGET_END_MS,
  }));
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
