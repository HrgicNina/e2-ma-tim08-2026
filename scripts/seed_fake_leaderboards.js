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

function integer(value) {
  return { integerValue: String(value) };
}

function string(value) {
  return { stringValue: value };
}

function bool(value) {
  return { booleanValue: value };
}

function millis(value) {
  return new Date(value).getTime();
}

function documentWrite(pathSuffix, fields) {
  return {
    update: {
      name: `projects/${PROJECT_ID}/databases/(default)/documents/${pathSuffix}`,
      fields,
    },
  };
}

function cycleWrites(cycle) {
  const players = [
    { id: "demo_ivona", username: "ivona", league: 4, stars: cycle.monthly ? 88 : 42, matches: 8 },
    { id: "demo_nina", username: "nina", league: 3, stars: cycle.monthly ? 73 : 35, matches: 7 },
    { id: "demo_raandom", username: "raandom", league: 2, stars: cycle.monthly ? 61 : 29, matches: 6 },
    { id: "demo_luka", username: "luka", league: 2, stars: cycle.monthly ? 47 : 21, matches: 5 },
    { id: "demo_milica", username: "milica", league: 1, stars: cycle.monthly ? 32 : 14, matches: 4 },
  ];

  const writes = [documentWrite(`leaderboardCycles/${cycle.id}`, {
    monthly: bool(cycle.monthly),
    startMs: integer(millis(cycle.start)),
    endMs: integer(millis(cycle.end)),
    processed: bool(true),
    processing: bool(false),
    participantCount: integer(players.length),
    demoData: bool(true),
  })];

  for (const player of players) {
    writes.push(documentWrite(
      `leaderboardCycles/${cycle.id}/entries/${player.id}`,
      {
        username: string(player.username),
        league: integer(player.league),
        cycleStars: integer(player.stars),
        cycleMatches: integer(player.matches),
        updatedAtMillis: integer(Date.now()),
        demoData: bool(true),
      }
    ));
  }
  return writes;
}

async function main() {
  const account = auth.getGlobalDefaultAccount();
  if (!account) {
    throw new Error("Firebase CLI nije prijavljen. Pokreni firebase login.");
  }
  auth.setActiveAccount({}, account);

  const cycles = [
    {
      id: "W_20260608",
      monthly: false,
      start: "2026-06-08T00:00:00+02:00",
      end: "2026-06-14T23:59:59.999+02:00",
    },
    {
      id: "M_202605",
      monthly: true,
      start: "2026-05-01T00:00:00+02:00",
      end: "2026-05-31T23:59:59.999+02:00",
    },
  ];
  const writes = cycles.flatMap(cycleWrites);
  const client = new Client({
    urlPrefix: "https://firestore.googleapis.com",
    apiVersion: "v1",
  });

  await client.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );

  console.log(`Upisano ${cycles.length} demo ciklusa i ${writes.length - cycles.length} rezultata.`);
  console.log(cycles.map((cycle) => cycle.id).join(", "));
  for (const cycle of cycles) {
    const response = await client.get(
      `/projects/${PROJECT_ID}/databases/(default)/documents/leaderboardCycles/${cycle.id}/entries`,
      { queryParams: { pageSize: 20 } }
    );
    const count = Array.isArray(response.body && response.body.documents)
      ? response.body.documents.length
      : 0;
    if (count !== 5) {
      throw new Error(`Provera ciklusa ${cycle.id} nije uspela: pronadjeno ${count} rezultata.`);
    }
    console.log(`Provereno ${cycle.id}: ${count} igraca.`);
  }
}

main().catch((error) => {
  console.error(error && error.message ? error.message : error);
  if (error && error.status) console.error(`HTTP status: ${error.status}`);
  if (error && error.context && error.context.response && error.context.response.body) {
    console.error(JSON.stringify(error.context.response.body));
  }
  process.exitCode = 1;
});
