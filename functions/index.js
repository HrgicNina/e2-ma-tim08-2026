const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();
const WEEKLY_REWARDS = [5, 3, 2];
const MONTHLY_REWARDS = [10, 6, 4];
const APP_ACTIVE_STALE_MS = 90 * 1000;

exports.processLeaderboardCycles = onSchedule(
  {
    schedule: "every 2 minutes",
    timeZone: "Europe/Belgrade",
    maxInstances: 1,
  },
  async () => {
    const now = Date.now();
    const cycles = await db.collection("leaderboardCycles")
      .where("processed", "==", false)
      .get();

    for (const cycleDoc of cycles.docs) {
      const endMs = numberValue(cycleDoc.get("endMs"));
      if (endMs <= 0 || endMs >= now) continue;
      await processOneCycle(cycleDoc.ref, cycleDoc.id);
    }
  }
);

async function processOneCycle(cycleRef, cycleId) {
  const claimed = await db.runTransaction(async (tx) => {
    const cycle = await tx.get(cycleRef);
    if (!cycle.exists || cycle.get("processed") === true) return false;
    const processingAt = numberValue(cycle.get("processingStartedAtMillis"));
    if (cycle.get("processing") === true && Date.now() - processingAt < 10 * 60 * 1000) {
      return false;
    }
    tx.set(cycleRef, {
      processing: true,
      processingStartedAtMillis: Date.now(),
    }, { merge: true });
    return true;
  });
  if (!claimed) return;

  try {
    const cycle = await cycleRef.get();
    const monthly = cycle.get("monthly") === true;
    const entriesSnapshot = await cycleRef.collection("entries").get();
    const ranked = entriesSnapshot.docs
      .map((doc) => ({
        uid: doc.id,
        username: stringValue(doc.get("username")),
        stars: numberValue(doc.get("cycleStars")),
        matches: numberValue(doc.get("cycleMatches")),
      }))
      .filter((entry) => entry.matches > 0)
      .sort((a, b) => (b.stars - a.stars) || a.username.localeCompare(b.username));

    for (let index = 0; index < Math.min(10, ranked.length); index++) {
      const rank = index + 1;
      const tokens = rewardForRank(rank, monthly);
      if (tokens > 0) {
        await rewardUser(ranked[index].uid, cycleId, monthly, rank, tokens, cycle);
      }
    }

    await cycleRef.set({
      processed: true,
      processing: false,
      processedAt: Timestamp.now(),
      participantCount: ranked.length,
    }, { merge: true });
  } catch (error) {
    await cycleRef.set({
      processing: false,
      lastProcessingError: String(error && error.message ? error.message : error),
    }, { merge: true });
    throw error;
  }
}

async function rewardUser(uid, cycleId, monthly, rank, tokens, cycle) {
  const userRef = db.collection("users").doc(uid);
  const claimedField = monthly ? "monthlyRewardClaimedCycleId" : "weeklyRewardClaimedCycleId";
  const label = cycleLabel(numberValue(cycle.get("startMs")), numberValue(cycle.get("endMs")));
  const rankingRef = userRef.collection("notifications").doc(`${cycleId}_ranking`);
  const rewardRef = userRef.collection("notifications").doc(`${cycleId}_reward`);

  await db.runTransaction(async (tx) => {
    const user = await tx.get(userRef);
    if (!user.exists || stringValue(user.get(claimedField)) === cycleId) return;

    tx.update(userRef, {
      tokens: FieldValue.increment(tokens),
      [claimedField]: cycleId,
    });
    tx.set(rankingRef, notificationPayload(
      "ranking",
      monthly ? "Mesecni plasman" : "Nedeljni plasman",
      `Zauzeli ste mesto #${rank} na ${monthly ? "mesecnoj" : "nedeljnoj"} rang listi.`,
      "open_rankings",
      cycleId
    ));
    tx.set(rewardRef, notificationPayload(
      "rewards",
      monthly ? "Mesecna rang nagrada" : "Nedeljna rang nagrada",
      `Osvojeno mesto #${rank} na ${monthly ? "mesecnoj" : "nedeljnoj"} rang listi ` +
        `(ciklus: ${label}). Dobijas ${tokens} tokena.`,
      "open_ranking_rewards",
      cycleId
    ));
  });
}

exports.createRegionChatNotifications = onDocumentCreated(
  "regionChats/{roomId}/messages/{messageId}",
  async (event) => {
    const message = event.data;
    if (!message) return;
    const senderUid = stringValue(message.get("senderUid"));
    if (!senderUid) return;

    const sender = await db.collection("users").doc(senderUid).get();
    if (!sender.exists) return;
    const region = stringValue(sender.get("region"));
    const senderName = stringValue(sender.get("username")) || "Igrac";
    if (!region) return;

    const users = await db.collection("users").where("region", "==", region).get();
    const offlineTargets = users.docs.filter((user) => {
      if (user.id === senderUid) return false;
      const active = user.get("appActive") === true;
      const lastSeen = numberValue(user.get("appLastSeenAtMillis"));
      return !(active && (lastSeen <= 0 || Date.now() - lastSeen <= APP_ACTIVE_STALE_MS));
    });

    const preview = truncate(stringValue(message.get("text")), 90);
    for (let offset = 0; offset < offlineTargets.length; offset += 400) {
      const batch = db.batch();
      for (const user of offlineTargets.slice(offset, offset + 400)) {
        const notificationRef = user.ref.collection("notifications")
          .doc(`chat_${event.params.messageId}`);
        batch.set(notificationRef, notificationPayload(
          "chat",
          "Nova poruka u cetu",
          `${senderName}: ${preview}`,
          "open_chat",
          event.params.roomId
        ));
      }
      await batch.commit();
    }
  }
);

// Any current or future notification producer (including league changes) automatically gets push delivery.
exports.pushSystemNotification = onDocumentCreated(
  "users/{uid}/notifications/{notificationId}",
  async (event) => {
    const notification = event.data;
    if (!notification) return;
    const tokenSnapshot = await db.collection("users")
      .doc(event.params.uid)
      .collection("fcmTokens")
      .get();
    const tokens = tokenSnapshot.docs
      .map((doc) => stringValue(doc.get("token")))
      .filter(Boolean);
    if (tokens.length === 0) return;

    const data = {
      targetUid: event.params.uid,
      notificationId: event.params.notificationId,
      type: stringValue(notification.get("type")),
      title: stringValue(notification.get("title")) || "Novo obavestenje",
      message: stringValue(notification.get("message")),
      actionType: stringValue(notification.get("actionType")),
      actionPayload: stringValue(notification.get("actionPayload")),
    };

    for (let offset = 0; offset < tokens.length; offset += 500) {
      const tokenChunk = tokens.slice(offset, offset + 500);
      const response = await getMessaging().sendEachForMulticast({ tokens: tokenChunk, data });
      const cleanup = [];
      response.responses.forEach((item, index) => {
        const code = item.error && item.error.code;
        if (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token") {
          const staleDoc = tokenSnapshot.docs.find(
            (doc) => stringValue(doc.get("token")) === tokenChunk[index]
          );
          if (staleDoc) cleanup.push(staleDoc.ref.delete());
        }
      });
      await Promise.all(cleanup);
    }
  }
);

function notificationPayload(type, title, message, actionType, actionPayload) {
  return {
    type,
    title,
    message,
    read: false,
    localShown: false,
    createdAt: Timestamp.now(),
    actionType,
    actionPayload,
  };
}

function rewardForRank(rank, monthly) {
  if (rank >= 1 && rank <= 3) {
    return monthly ? MONTHLY_REWARDS[rank - 1] : WEEKLY_REWARDS[rank - 1];
  }
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

function truncate(value, max) {
  return value.length <= max ? value : `${value.slice(0, max)}...`;
}

function stringValue(value) {
  return typeof value === "string" ? value : "";
}

function numberValue(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
