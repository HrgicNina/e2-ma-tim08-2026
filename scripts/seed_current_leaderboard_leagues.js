const path = require("path");

const PROJECT_ID = "slagalica-30ba3";
const firebaseToolsRoot = path.join(
  process.env.APPDATA,
  "npm",
  "node_modules",
  "firebase-tools",
  "lib"
);
const auth = require(path.join(firebaseToolsRoot, "auth"));
const { Client } = require(path.join(firebaseToolsRoot, "apiv2"));

function currentCycleIds() {
  const now = new Date();
  const local = new Date(now.toLocaleString("en-US", { timeZone: "Europe/Belgrade" }));
  const monday = new Date(local);
  const day = monday.getDay() || 7;
  monday.setDate(monday.getDate() - day + 1);
  const yyyy = monday.getFullYear();
  const mm = String(monday.getMonth() + 1).padStart(2, "0");
  const dd = String(monday.getDate()).padStart(2, "0");
  const monthNow = String(local.getMonth() + 1).padStart(2, "0");
  return [`W_${yyyy}${mm}${dd}`, `M_${local.getFullYear()}${monthNow}`];
}

async function main() {
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error("Firebase CLI nije prijavljen.");
  auth.setActiveAccount({}, account);

  const client = new Client({
    urlPrefix: "https://firestore.googleapis.com",
    apiVersion: "v1",
  });
  const writes = [];

  for (const cycleId of currentCycleIds()) {
    const response = await client.get(
      `/projects/${PROJECT_ID}/databases/(default)/documents/leaderboardCycles/${cycleId}/entries`,
      { queryParams: { pageSize: 100 } }
    );
    const documents = Array.isArray(response.body && response.body.documents)
      ? response.body.documents
      : [];
    for (const document of documents) {
      const uid = document.name.split("/").pop();
      let user;
      try {
        const userResponse = await client.get(
          `/projects/${PROJECT_ID}/databases/(default)/documents/users/${uid}`
        );
        user = userResponse.body;
      } catch (error) {
        if (error && error.status === 404) continue;
        throw error;
      }
      const actualLeague = Number(
        user && user.fields && user.fields.league && user.fields.league.integerValue || 0
      );
      writes.push({
        update: {
          name: document.name,
          fields: { league: { integerValue: String(actualLeague) } },
        },
        updateMask: { fieldPaths: ["league"] },
      });
    }
  }

  if (writes.length === 0) {
    throw new Error("Nema igraca u trenutnim rang-ciklusima.");
  }
  await client.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );
  console.log(`Sinhronizovano ${writes.length} stvarnih liga u trenutnim ciklusima.`);
}

main().catch((error) => {
  console.error(error && error.message ? error.message : error);
  process.exitCode = 1;
});
