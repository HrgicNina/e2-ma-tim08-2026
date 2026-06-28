const { WebSocketServer } = require("ws");
const crypto = require("crypto");
const {
  startFirebaseBridge,
  createOfflineMatchInvite,
  updateOfflineMatchInviteStatus,
} = require("./firebaseBridge");

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

const clientsByUid = new Map();
const socketsMeta = new Map();
const queue = [];
const invites = new Map();
const matches = new Map();
const INVITE_TIMEOUT_MS = 10_000;

function send(ws, type, payload = {}) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify({ type, payload }));
  }
}

function info(ws, message) {
  send(ws, "info", { message });
}

function error(ws, message) {
  send(ws, "error", { message });
}

function removeFromQueue(uid) {
  const idx = queue.indexOf(uid);
  if (idx >= 0) queue.splice(idx, 1);
}

function compactQueue() {
  for (let i = queue.length - 1; i >= 0; i--) {
    const uid = queue[i];
    const ws = clientsByUid.get(uid);
    if (!ws) {
      queue.splice(i, 1);
      continue;
    }
    const meta = socketsMeta.get(ws);
    if (!meta || meta.roomId) {
      queue.splice(i, 1);
    }
  }
}

function isUidReadyForQueue(uid) {
  const ws = clientsByUid.get(uid);
  if (!ws) return false;
  const meta = socketsMeta.get(ws);
  return !!meta && !meta.roomId;
}

function popNextReadyUid(excludeUid = null) {
  while (queue.length > 0) {
    const uid = queue.shift();
    if (excludeUid && uid === excludeUid) {
      continue;
    }
    if (!isUidReadyForQueue(uid)) {
      continue;
    }
    return uid;
  }
  return null;
}

function tryStartQueuedMatches() {
  compactQueue();

  while (queue.length >= 2) {
    const uidA = popNextReadyUid();
    if (!uidA) break;

    const uidB = popNextReadyUid(uidA);
    if (!uidB) {
      if (!queue.includes(uidA) && isUidReadyForQueue(uidA)) {
        queue.push(uidA);
      }
      break;
    }

    startMatch(uidA, uidB, false);
  }
}

function getOpponentUid(room, uid) {
  return room.players.find((p) => p !== uid);
}

function startMatch(uidA, uidB, friendly) {
  const wsA = clientsByUid.get(uidA);
  const wsB = clientsByUid.get(uidB);
  if (!wsA || !wsB) return false;

  const metaA = socketsMeta.get(wsA);
  const metaB = socketsMeta.get(wsB);
  if (!metaA || !metaB || metaA.roomId || metaB.roomId) return false;

  removeFromQueue(uidA);
  removeFromQueue(uidB);

  const roomId = crypto.randomUUID();
  const room = {
    roomId,
    friendly,
    players: [uidA, uidB],
    scores: {},
    finished: false,
  };
  matches.set(roomId, room);
  metaA.roomId = roomId;
  metaB.roomId = roomId;

  send(wsA, "match.start", {
    roomId,
    friendly,
    playerNumber: 1,
    opponentUid: uidB,
    opponentUsername: metaB.username,
  });
  send(wsB, "match.start", {
    roomId,
    friendly,
    playerNumber: 2,
    opponentUid: uidA,
    opponentUsername: metaA.username,
  });
  return true;
}

function finishMatch(roomId, winnerUid, loserUid, forfeit = false, draw = false) {
  const room = matches.get(roomId);
  if (!room || room.finished) return;
  room.finished = true;

  for (const uid of room.players) {
    const ws = clientsByUid.get(uid);
    if (!ws) continue;
    const meta = socketsMeta.get(ws);
    if (meta) meta.roomId = null;
    const oppUid = getOpponentUid(room, uid);
    const yourScore = room.scores[uid] || 0;
    const opponentScore = room.scores[oppUid] || 0;
    send(ws, "match.finished", {
      roomId,
      winnerUid,
      loserUid,
      yourScore,
      opponentScore,
      forfeit,
      draw,
    });
  }
}

function handleAuth(ws, payload) {
  const uid = (payload.uid || "").trim();
  const username = (payload.username || "").trim();
  if (!uid) {
    error(ws, "Nedostaje uid.");
    return;
  }

  const prev = clientsByUid.get(uid);
  if (prev && prev !== ws) {
    // Stara konekcija za isti uid se gasi da ne postoji dupli session.
    send(prev, "error", {
      message: "Ovaj nalog je otvoren na drugom uredjaju. Za test partije koristite dva razlicita naloga.",
    });
    try {
      prev.close(1000, "replaced_session");
    } catch (_) {
    }
    const prevMeta = socketsMeta.get(prev);
    if (prevMeta) {
      removeFromQueue(prevMeta.uid);
      prevMeta.roomId = null;
    }
    socketsMeta.delete(prev);
  }

  socketsMeta.set(ws, { uid, username, roomId: null });
  clientsByUid.set(uid, ws);
  removeFromQueue(uid);
  send(ws, "auth.ok", { uid, username });
}

function handleQueueJoin(ws) {
  const meta = socketsMeta.get(ws);
  if (!meta) return error(ws, "Niste autentikovani.");
  if (meta.roomId) return error(ws, "Vec ste u partiji.");
  compactQueue();
  if (!queue.includes(meta.uid)) queue.push(meta.uid);
  send(ws, "queue.joined", {});
  tryStartQueuedMatches();
}

function handleQueueCancel(ws) {
  const meta = socketsMeta.get(ws);
  if (!meta) return;
  removeFromQueue(meta.uid);
  send(ws, "queue.cancelled", {});
}

async function handleInviteSend(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return error(ws, "Niste autentikovani.");
  const target = (payload.target || "").trim();
  if (!target) return error(ws, "Unesite cilj poziva.");

  let targetWs = clientsByUid.get(target);
  if (!targetWs) {
    // fallback po username
    for (const [sock, m] of socketsMeta.entries()) {
      if (m.username && m.username.toLowerCase() === target.toLowerCase()) {
        targetWs = sock;
        break;
      }
    }
  }
  if (!targetWs) {
    const inviteId = crypto.randomUUID();
    const expiresAtMillis = Date.now() + INVITE_TIMEOUT_MS;
    let targetUser;
    try {
      targetUser = await createOfflineMatchInvite({
        inviteId,
        fromUid: meta.uid,
        fromUsername: meta.username || meta.uid,
        targetIdentity: target,
        expiresAtMillis,
      });
    } catch (e) {
      return error(ws, `Offline poziv nije poslat: ${e.message}`);
    }

    const timeoutHandle = setTimeout(() => {
      const activeInvite = invites.get(inviteId);
      if (!activeInvite) return;
      invites.delete(inviteId);
      updateOfflineMatchInviteStatus(inviteId, "expired").catch(() => {});
      const fromWs = clientsByUid.get(activeInvite.fromUid);
      if (fromWs) send(fromWs, "invite.expired", { inviteId });
    }, INVITE_TIMEOUT_MS);

    invites.set(inviteId, {
      fromUid: meta.uid,
      toUid: targetUser.uid,
      timeoutHandle,
      offline: true,
    });
    send(ws, "invite.sent", {
      inviteId,
      expiresInSeconds: INVITE_TIMEOUT_MS / 1000,
    });
    return;
  }

  const targetMeta = socketsMeta.get(targetWs);
  if (!targetMeta) return error(ws, "Prijatelj nije dostupan.");
  if (targetMeta.roomId) return error(ws, "Prijatelj je vec u partiji.");

  const inviteId = crypto.randomUUID();
  const timeoutHandle = setTimeout(() => {
    const activeInvite = invites.get(inviteId);
    if (!activeInvite) return;
    invites.delete(inviteId);

    const fromWs = clientsByUid.get(activeInvite.fromUid);
    if (fromWs) {
      send(fromWs, "invite.expired", {
        inviteId,
      });
    }
    const toWs = clientsByUid.get(activeInvite.toUid);
    if (toWs) {
      send(toWs, "invite.expired", {
        inviteId,
      });
    }
  }, INVITE_TIMEOUT_MS);

  invites.set(inviteId, { fromUid: meta.uid, toUid: targetMeta.uid, timeoutHandle });
  send(ws, "invite.sent", {
    inviteId,
    expiresInSeconds: INVITE_TIMEOUT_MS / 1000,
  });
  send(targetWs, "invite.received", {
    inviteId,
    fromUid: meta.uid,
    fromUsername: meta.username || meta.uid,
  });
  info(ws, "Poziv poslat.");
}

function handleInviteRespond(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return error(ws, "Niste autentikovani.");
  const inviteId = payload.inviteId;
  const accept = !!payload.accept;
  const invite = invites.get(inviteId);
  if (!invite) return error(ws, "Poziv vise ne postoji.");

  if (invite.toUid !== meta.uid) return error(ws, "Nije vas poziv.");

  invites.delete(inviteId);
  if (invite.timeoutHandle) {
    clearTimeout(invite.timeoutHandle);
  }

  const fromWs = clientsByUid.get(invite.fromUid);
  if (!fromWs) {
    if (invite.offline) updateOfflineMatchInviteStatus(inviteId, "expired").catch(() => {});
    return error(ws, "Igrac koji je poslao poziv nije online.");
  }

  if (!accept) {
    if (invite.offline) updateOfflineMatchInviteStatus(inviteId, "declined").catch(() => {});
    send(fromWs, "invite.declined", {
      byUid: meta.uid,
      byUsername: meta.username || meta.uid,
    });
    return;
  }

  const fromMeta = socketsMeta.get(fromWs);
  if (!fromMeta) return error(ws, "Igrac koji je poslao poziv nije dostupan.");

  if (fromMeta.roomId) {
    error(ws, "Igrac koji je poslao poziv je vec u partiji.");
    return error(fromWs, "Vec ste u partiji.");
  }

  if (meta.roomId) {
    error(ws, "Vec ste u partiji.");
    return error(fromWs, "Prijatelj je vec u partiji.");
  }

  if (!startMatch(invite.fromUid, invite.toUid, true)) {
    error(ws, "Partija ne moze da se pokrene jer je jedan igrac zauzet.");
    return error(
      fromWs,
      "Partija ne moze da se pokrene jer je jedan igrac zauzet."
    );
  }

  if (invite.offline) {
    updateOfflineMatchInviteStatus(inviteId, "accepted").catch(() => {});
  }
}

function handleInviteCancel(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return error(ws, "Niste autentikovani.");
  const inviteId = payload.inviteId;
  const invite = invites.get(inviteId);
  if (!invite) return error(ws, "Poziv vise ne postoji.");
  if (invite.fromUid !== meta.uid) return error(ws, "Mozete otkazati samo svoj poziv.");

  invites.delete(inviteId);
  if (invite.timeoutHandle) {
    clearTimeout(invite.timeoutHandle);
  }
  if (invite.offline) updateOfflineMatchInviteStatus(inviteId, "cancelled").catch(() => {});

  const toWs = clientsByUid.get(invite.toUid);
  if (toWs) {
    send(toWs, "invite.cancelled", {
      inviteId,
      byUid: meta.uid,
      byUsername: meta.username || meta.uid,
    });
  }
  info(ws, "Poziv je otkazan.");
}

function handleSubmitScore(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return;
  const roomId = payload.roomId;
  const score = Number(payload.score || 0);
  const room = matches.get(roomId);
  if (!room || room.finished) return error(ws, "Partija ne postoji.");
  if (!room.players.includes(meta.uid)) return error(ws, "Niste igrac u ovoj partiji.");

  room.scores[meta.uid] = score;
  const a = room.players[0];
  const b = room.players[1];
  if (room.scores[a] == null || room.scores[b] == null) {
    info(ws, "Rezultat sacuvan, ceka se protivnik.");
    return;
  }

  if (room.scores[a] === room.scores[b]) {
    finishMatch(roomId, "", "", false, true);
  } else if (room.scores[a] > room.scores[b]) {
    finishMatch(roomId, a, b, false);
  } else {
    finishMatch(roomId, b, a, false);
  }
}

function handleForfeit(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return;
  const roomId = payload.roomId;
  const room = matches.get(roomId);
  if (!room || room.finished) return;
  if (!room.players.includes(meta.uid)) return;

  const opponentUid = getOpponentUid(room, meta.uid);
  finishMatch(roomId, opponentUid, meta.uid, true);
}

function handleGameEvent(ws, payload) {
  const meta = socketsMeta.get(ws);
  if (!meta) return error(ws, "Niste autentikovani.");
  const roomId = payload.roomId;
  const game = payload.game;
  const event = payload.event;
  const data = payload.data || {};
  if (!roomId || !game || !event) return error(ws, "Neispravan game event.");

  const room = matches.get(roomId);
  if (!room || room.finished) return error(ws, "Partija ne postoji.");
  if (!room.players.includes(meta.uid)) return error(ws, "Niste igrac u ovoj partiji.");

  for (const uid of room.players) {
    if (uid === meta.uid) continue;
    const targetWs = clientsByUid.get(uid);
    if (!targetWs) continue;
    send(targetWs, "game.event", {
      roomId,
      game,
      event,
      fromUid: meta.uid,
      data,
    });
  }
}

wss.on("connection", (ws) => {
  ws.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (e) {
      return error(ws, "Neispravan JSON.");
    }

    const type = msg.type;
    const payload = msg.payload || {};

    switch (type) {
      case "auth":
        return handleAuth(ws, payload);
      case "queue.join":
        return handleQueueJoin(ws);
      case "queue.cancel":
        return handleQueueCancel(ws);
      case "invite.send":
        return handleInviteSend(ws, payload);
      case "invite.respond":
        return handleInviteRespond(ws, payload);
      case "invite.cancel":
        return handleInviteCancel(ws, payload);
      case "match.submit_score":
        return handleSubmitScore(ws, payload);
      case "match.forfeit":
        return handleForfeit(ws, payload);
      case "game.event":
        return handleGameEvent(ws, payload);
      default:
        return error(ws, "Nepoznat event: " + type);
    }
  });

  ws.on("close", () => {
    const meta = socketsMeta.get(ws);
    if (!meta) return;

    if (clientsByUid.get(meta.uid) !== ws) {
      socketsMeta.delete(ws);
      return;
    }

    removeFromQueue(meta.uid);
    clientsByUid.delete(meta.uid);

    for (const [inviteId, invite] of invites.entries()) {
      if (invite.fromUid === meta.uid || invite.toUid === meta.uid) {
        if (invite.timeoutHandle) {
          clearTimeout(invite.timeoutHandle);
        }
        invites.delete(inviteId);
        if (invite.offline) {
          updateOfflineMatchInviteStatus(inviteId, "cancelled").catch(() => {});
        }
        if (invite.fromUid === meta.uid) {
          const toWs = clientsByUid.get(invite.toUid);
          if (toWs) {
            send(toWs, "invite.cancelled", {
              inviteId,
              byUid: meta.uid,
              byUsername: meta.username || meta.uid,
            });
          }
        }
      }
    }

    if (meta.roomId) {
      const room = matches.get(meta.roomId);
      if (room && !room.finished) {
        const opponentUid = getOpponentUid(room, meta.uid);
        finishMatch(meta.roomId, opponentUid, meta.uid, true);
      }
    }
    socketsMeta.delete(ws);
  });
});

startFirebaseBridge().catch(() => {});
