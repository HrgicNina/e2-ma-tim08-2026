const fs = require("fs");
const path = require("path");

const PROJECT_ID = "slagalica-30ba3";
const POLL_INTERVAL_MS = 2_000;
const CYCLE_INTERVAL_MS = 10_000;
const APP_ACTIVE_STALE_MS = 20_000;
const RECENT_ON_START_MS = 2 * 60_000;

const REGION_ROOMS = new Map([
  ["vojvodina", "Vojvodina"],
  ["podrinje_i_posavina", "Podrinje i Posavina"],
  ["_umadija", "Šumadija"],
  ["timok_i_brani_evo", "Timok i Braničevo"],
  ["ra_ka", "Raška"],
  ["rasina_i_toplica", "Rasina i Toplica"],
  ["_opluk", "Šopluk"],
  ["ju_no_pomoravlje", "Južno Pomoravlje"],
  ["kosovo_i_metohija", "Kosovo i Metohija"],
]);

const seenChatMessages = new Set();
const seenNotifications = new Set();
const bridgeStartedAt = Date.now();
let polling = false;
let processingCycles = false;
let activeFirestore = null;

function loadFirebaseTools() {
  const appData = process.env.APPDATA;
  if (!appData) throw new Error("APPDATA nije dostupan.");
  const lib = path.join(appData, "npm", "node_modules", "firebase-tools", "lib");
  if (!fs.existsSync(lib)) {
    throw new Error("Firebase CLI nije instaliran globalno.");
  }
  const auth = require(path.join(lib, "auth"));
  const { Client } = require(path.join(lib, "apiv2"));
  const account = auth.getProjectDefaultAccount(process.cwd()) || auth.getGlobalDefaultAccount();
  if (!account) throw new Error("Firebase CLI nije prijavljen. Pokreni firebase login.");
  auth.setActiveAccount({}, account);
  return {
    firestore: new Client({
      urlPrefix: "https://firestore.googleapis.com",
      apiVersion: "v1",
    }),
    fcm: new Client({
      urlPrefix: "https://fcm.googleapis.com",
      apiVersion: "v1",
    }),
  };
}

function collectionPath(relativePath) {
  return `/projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`;
}

function documentName(relativePath) {
  return `projects/${PROJECT_ID}/databases/(default)/documents/${relativePath}`;
}

function documentId(document) {
  const parts = String(document && document.name || "").split("/");
  return parts[parts.length - 1] || "";
}

function rawValue(document, key) {
  const value = document && document.fields && document.fields[key];
  if (!value) return undefined;
  if (Object.prototype.hasOwnProperty.call(value, "stringValue")) return value.stringValue;
  if (Object.prototype.hasOwnProperty.call(value, "integerValue")) return Number(value.integerValue);
  if (Object.prototype.hasOwnProperty.call(value, "doubleValue")) return Number(value.doubleValue);
  if (Object.prototype.hasOwnProperty.call(value, "booleanValue")) return Boolean(value.booleanValue);
  if (Object.prototype.hasOwnProperty.call(value, "timestampValue")) return value.timestampValue;
  return undefined;
}

function text(document, key) {
  const value = rawValue(document, key);
  return typeof value === "string" ? value : "";
}

function number(document, key) {
  const value = rawValue(document, key);
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function bool(document, key) {
  return rawValue(document, key) === true;
}

function millis(document, timestampKey, fallbackKey) {
  const timestamp = rawValue(document, timestampKey);
  if (typeof timestamp === "string") {
    const parsed = Date.parse(timestamp);
    if (Number.isFinite(parsed)) return parsed;
  }
  return number(document, fallbackKey);
}

function stringField(value) {
  return { stringValue: String(value == null ? "" : value) };
}

function integerField(value) {
  return { integerValue: String(Math.trunc(value || 0)) };
}

function booleanField(value) {
  return { booleanValue: Boolean(value) };
}

function timestampField(date = new Date()) {
  return { timestampValue: date.toISOString() };
}

function updateWrite(relativePath, fields, fieldPaths = null) {
  const write = {
    update: {
      name: documentName(relativePath),
      fields,
    },
  };
  if (fieldPaths) write.updateMask = { fieldPaths };
  return write;
}

async function listDocuments(client, relativePath, pageSize = 100, queryParams = {}) {
  const response = await client.get(collectionPath(relativePath), {
    queryParams: { pageSize, ...queryParams },
  });
  return Array.isArray(response.body && response.body.documents)
    ? response.body.documents
    : [];
}

async function getDocument(client, relativePath) {
  try {
    const response = await client.get(collectionPath(relativePath));
    return response.body || null;
  } catch (error) {
    if (error && error.status === 404) return null;
    throw error;
  }
}

function userFromDocument(document) {
  return {
    uid: documentId(document),
    username: text(document, "username"),
    region: text(document, "region"),
    appActive: bool(document, "appActive"),
    appLastSeenAtMillis: millis(document, "appLastSeenAt", "appLastSeenAtMillis"),
  };
}

function userIsInApp(user) {
  if (!user.appActive) return false;
  if (user.appLastSeenAtMillis <= 0) return true;
  const age = Date.now() - user.appLastSeenAtMillis;
  return age >= 0 && age <= APP_ACTIVE_STALE_MS;
}

function notificationFields(type, title, message, actionType, actionPayload, rewardTokens = 0) {
  const fields = {
    type: stringField(type),
    title: stringField(title),
    message: stringField(message),
    read: booleanField(false),
    localShown: booleanField(false),
    createdAt: timestampField(),
    actionType: stringField(actionType),
    actionPayload: stringField(actionPayload),
  };
  if (rewardTokens > 0) {
    fields.rewardTokens = integerField(rewardTokens);
    fields.rewardClaimed = booleanField(false);
  }
  return fields;
}

async function createChatNotifications(firestore, users, roomId, messageDocument) {
  const messageId = documentId(messageDocument);
  const senderUid = text(messageDocument, "senderUid");
  const senderName = text(messageDocument, "senderName") || "Igrac";
  const messageText = text(messageDocument, "text");
  const region = REGION_ROOMS.get(roomId);
  if (!messageId || !senderUid || !region) return;

  const targets = users.filter((user) =>
    user.uid !== senderUid && user.region === region && !userIsInApp(user)
  );
  if (targets.length === 0) return;

  const preview = messageText.length <= 90 ? messageText : `${messageText.slice(0, 90)}...`;
  const writes = targets.map((user) => updateWrite(
    `users/${user.uid}/notifications/chat_${messageId}`,
    notificationFields(
      "chat",
      "Nova poruka u cetu",
      `${senderName}: ${preview}`,
      "open_chat",
      roomId
    )
  ));
  await firestore.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );
}

async function pollChatMessages(firestore, users) {
  for (const roomId of REGION_ROOMS.keys()) {
    const messages = await listDocuments(
      firestore,
      `regionChats/${roomId}/messages`,
      100,
      { orderBy: "createdAt desc" }
    );
    for (const message of messages) {
      const key = `${roomId}/${documentId(message)}`;
      if (seenChatMessages.has(key)) continue;
      seenChatMessages.add(key);
      const createdAt = millis(message, "createdAt", "createdAtMillis");
      if (createdAt <= 0 || createdAt < bridgeStartedAt - 5_000) continue;
      await createChatNotifications(firestore, users, roomId, message);
    }
  }
}

async function fcmTokensForUser(firestore, uid) {
  const documents = await listDocuments(firestore, `users/${uid}/fcmTokens`, 20);
  return documents.map((document) => text(document, "token")).filter(Boolean);
}

async function sendNotificationFcm(fcm, firestore, user, notification) {
  const tokens = await fcmTokensForUser(firestore, user.uid);
  if (tokens.length === 0) return 0;
  const notificationId = documentId(notification);
  const data = {
    targetUid: user.uid,
    notificationId,
    type: text(notification, "type"),
    title: text(notification, "title") || "Novo obavestenje",
    message: text(notification, "message"),
    actionType: text(notification, "actionType"),
    actionPayload: text(notification, "actionPayload"),
  };
  let sent = 0;
  for (const token of tokens) {
    try {
      await fcm.post(`/projects/${PROJECT_ID}/messages:send`, {
        message: {
          token,
          data,
          android: { priority: "high" },
        },
      });
      sent++;
    } catch (error) {
    }
  }
  return sent;
}

async function pollNotifications(fcm, firestore, users) {
  for (const user of users) {
    const notifications = await listDocuments(
      firestore,
      `users/${user.uid}/notifications`,
      100,
      { orderBy: "createdAt desc" }
    );
    for (const notification of notifications) {
      const key = `${user.uid}/${documentId(notification)}`;
      if (seenNotifications.has(key)) continue;
      const createdAt = millis(notification, "createdAt", "createdAtMillis");
      if (createdAt <= 0 || createdAt < bridgeStartedAt - RECENT_ON_START_MS) {
        seenNotifications.add(key);
        continue;
      }
      if (bool(notification, "read") || bool(notification, "localShown") || userIsInApp(user)) {
        seenNotifications.add(key);
        continue;
      }
      const sent = await sendNotificationFcm(fcm, firestore, user, notification);
      if (sent > 0) {
        seenNotifications.add(key);
      }
    }
  }
}

function rewardForRank(rank, monthly) {
  const top = monthly ? [10, 6, 4] : [5, 3, 2];
  if (rank >= 1 && rank <= 3) return top[rank - 1];
  if (rank >= 4 && rank <= 10) return monthly ? 2 : 1;
  return 0;
}

function cycleLabel(startMs, endMs) {
  const format = new Intl.DateTimeFormat("sr-RS", {
    timeZone: "Europe/Belgrade",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
  return `${format.format(new Date(startMs))} - ${format.format(new Date(endMs))}`;
}

async function rewardCycleUser(firestore, cycle, entry, rank) {
  const user = await getDocument(firestore, `users/${entry.uid}`);
  if (!user) return;
  const monthly = bool(cycle, "monthly");
  const claimedField = monthly ? "monthlyRewardClaimedCycleId" : "weeklyRewardClaimedCycleId";
  const cycleId = documentId(cycle);
  if (text(user, claimedField) === cycleId) return;
  const tokens = rewardForRank(rank, monthly);
  if (tokens <= 0) return;

  const label = cycleLabel(number(cycle, "startMs"), number(cycle, "endMs"));
  const writes = [
    updateWrite(
      `users/${entry.uid}`,
      {
        [claimedField]: stringField(cycleId),
      },
      [claimedField]
    ),
    updateWrite(
      `users/${entry.uid}/notifications/${cycleId}_ranking`,
      notificationFields(
        "ranking",
        monthly ? "Mesecni plasman" : "Nedeljni plasman",
        `Zauzeli ste mesto #${rank} na ${monthly ? "mesecnoj" : "nedeljnoj"} rang listi.`,
        "open_rankings",
        cycleId
      )
    ),
    updateWrite(
      `users/${entry.uid}/notifications/${cycleId}_reward`,
      notificationFields(
        "rewards",
        monthly ? "Mesecna rang nagrada" : "Nedeljna rang nagrada",
        `Osvojeno mesto #${rank} (ciklus: ${label}). Dobijas ${tokens} tokena.`,
        "open_ranking_rewards",
        cycleId,
        tokens
      )
    ),
  ];
  await firestore.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );
}

async function processLeaderboardCycles(firestore) {
  if (processingCycles) return;
  processingCycles = true;
  try {
    const cycles = await listDocuments(firestore, "leaderboardCycles", 100);
    for (const cycle of cycles) {
      const endMs = number(cycle, "endMs");
      if (bool(cycle, "processed") || endMs <= 0 || endMs >= Date.now()) continue;
      const cycleId = documentId(cycle);
      const documents = await listDocuments(firestore, `leaderboardCycles/${cycleId}/entries`, 500);
      const ranked = documents
        .map((document) => ({
          uid: documentId(document),
          username: text(document, "username"),
          stars: number(document, "cycleStars"),
          matches: number(document, "cycleMatches"),
        }))
        .filter((entry) => entry.matches > 0)
        .sort((a, b) => (b.stars - a.stars) || a.username.localeCompare(b.username));

      for (let index = 0; index < Math.min(10, ranked.length); index++) {
        await rewardCycleUser(firestore, cycle, ranked[index], index + 1);
      }
      await firestore.post(
        `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
        {
          writes: [updateWrite(
            `leaderboardCycles/${cycleId}`,
            {
              processed: booleanField(true),
              processing: booleanField(false),
              processedAt: timestampField(),
              participantCount: integerField(ranked.length),
            },
            ["processed", "processing", "processedAt", "participantCount"]
          )],
        }
      );
    }
  } finally {
    processingCycles = false;
  }
}

async function createOfflineMatchInvite({
  inviteId,
  fromUid,
  fromUsername,
  targetIdentity,
  expiresAtMillis,
}) {
  if (!activeFirestore) {
    throw new Error("Firebase bridge nije spreman.");
  }
  const documents = await listDocuments(activeFirestore, "users", 100);
  const normalizedTarget = String(targetIdentity || "").trim().toLowerCase();
  const target = documents.find((document) =>
    documentId(document).toLowerCase() === normalizedTarget ||
    text(document, "username").toLowerCase() === normalizedTarget
  );
  if (!target) throw new Error("Prijatelj ne postoji.");
  const toUid = documentId(target);
  if (toUid === fromUid) throw new Error("Ne mozete pozvati sebe.");
  if (bool(target, "inMatch")) throw new Error("Prijatelj je vec u partiji.");
  const toUsername = text(target, "username") || toUid;
  const sender = fromUsername || fromUid;

  const writes = [
    updateWrite(`matchInvites/${inviteId}`, {
      inviteId: stringField(inviteId),
      fromUid: stringField(fromUid),
      fromUsername: stringField(sender),
      toUid: stringField(toUid),
      toUsername: stringField(toUsername),
      status: stringField("pending"),
      createdAt: timestampField(),
      expiresAtMillis: integerField(expiresAtMillis),
    }),
    updateWrite(
      `users/${toUid}/notifications/invite_${inviteId}`,
      notificationFields(
        "other",
        "Poziv za prijateljsku partiju",
        `${sender} vas poziva na prijateljsku partiju. Poziv vazi 10 sekundi.`,
        "respond_match_invite",
        inviteId
      )
    ),
  ];
  await activeFirestore.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    { writes }
  );
  return { uid: toUid, username: toUsername };
}

async function updateOfflineMatchInviteStatus(inviteId, status) {
  if (!activeFirestore || !inviteId) return;
  await activeFirestore.post(
    `/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
    {
      writes: [updateWrite(
        `matchInvites/${inviteId}`,
        {
          status: stringField(status),
          respondedAt: timestampField(),
        },
        ["status", "respondedAt"]
      )],
    }
  );
}

async function startFirebaseBridge() {
  if (process.env.FIREBASE_BRIDGE_ENABLED === "0") {
    return;
  }
  let clients;
  try {
    clients = loadFirebaseTools();
  } catch (error) {
    return;
  }
  const { firestore, fcm } = clients;
  activeFirestore = firestore;

  const poll = async () => {
    if (polling) return;
    polling = true;
    try {
      const documents = await listDocuments(firestore, "users", 100);
      const users = documents.map(userFromDocument).filter((user) => user.uid);
      await pollChatMessages(firestore, users);
      await pollNotifications(fcm, firestore, users);
    } catch (error) {
    } finally {
      polling = false;
    }
  };

  await poll();
  await processLeaderboardCycles(firestore).catch(() => {});
  setInterval(poll, POLL_INTERVAL_MS);
  setInterval(
    () => processLeaderboardCycles(firestore).catch(() => {}),
    CYCLE_INTERVAL_MS
  );
}

module.exports = {
  startFirebaseBridge,
  createOfflineMatchInvite,
  updateOfflineMatchInviteStatus,
};
