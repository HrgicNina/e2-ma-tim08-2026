const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

exports.pushUserNotification = onDocumentCreated(
  "users/{uid}/notifications/{notificationId}",
  async (event) => {
    const notification = event.data;
    if (!notification) return;

    const { uid, notificationId } = event.params;
    const userRef = admin.firestore().collection("users").doc(uid);
    const user = await userRef.get();
    const token = user.get("fcmToken");
    const loggedIn = user.get("loggedIn") === true;

    if (!loggedIn || typeof token !== "string" || !token.trim()) {
      return;
    }

    const payload = notification.data() || {};
    const stringValue = (value) => value == null ? "" : String(value);

    try {
      await admin.messaging().send({
        token: token.trim(),
        data: {
          notificationId,
          type: stringValue(payload.type),
          title: stringValue(payload.title),
          message: stringValue(payload.message),
          actionType: stringValue(payload.actionType),
          actionPayload: stringValue(payload.actionPayload),
        },
        android: {
          priority: "high",
        },
      });
      await notification.ref.update({
        localShown: true,
        pushedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } catch (error) {
      const code = error && error.code ? String(error.code) : "";
      if (code.includes("registration-token-not-registered")
          || code.includes("invalid-registration-token")) {
        await userRef.update({
          fcmToken: admin.firestore.FieldValue.delete(),
        });
      }
      logger.error("Push notification failed", { uid, notificationId, code });
    }
  }
);
