const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_gamer_123";
const BOB_UID = "bob_gamer_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validWorkspace(uid, now) {
  return {
    userId: uid,
    displayName: "GR Gamer",
    email: "gr@example.com",
    channelConnected: false,
    autoReplyEnabled: false,
    autoReplyIntervalMinutes: 15,
    ignoreSpam: true,
    ignoreAbusive: true,
    avoidDuplicates: true,
    primaryLanguage: "AUTO",
    customPrompt: "You are GR GAMER AI, an intelligent gaming YouTube assistant for a Free Fire channel.",
    totalCommentsReceived: 0,
    totalRepliesSent: 0,
    pendingCommentsCount: 0,
    failedRepliesCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validComment(uid, now, status = "PENDING") {
  return {
    userId: uid,
    youtubeCommentId: "UgwTestComment123",
    channelId: "UC123456",
    videoId: "vid_999",
    videoTitle: "Free Fire Ranked Clash Squad",
    authorName: "ProPlayerYT",
    commentText: "Bhai OP gameplay! Sensitivity batao",
    detectedLanguage: "HINGLISH",
    classification: "NORMAL",
    status,
    publishedAtText: "2026-11-17T10:00:00Z",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any workspace, comment, or replyHistory", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validWorkspace(ALICE_UID, past)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("comments").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("replyHistory").get());
});

test("Authenticated user can create and update own workspace with valid actions, but cannot list /users or access Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validWorkspace(ALICE_UID, past))
  );

  // Action 1: Connect YouTube channel
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      connectedChannelId: "UC_GR_GAMER",
      connectedChannelTitle: "GR GAMER",
      connectedChannelHandle: "@grgamer",
      subscriberCount: 15400,
      videoCount: 42,
      channelConnected: true,
      autoReplyEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Update prompt & language
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      primaryLanguage: "HINGLISH",
      customPrompt: "Reply like a friendly Free Fire esports coach in Hinglish.",
      autoReplyEnabled: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Listing users collection must fail
  await assertFails(aliceDb.collection("users").get());

  // Cross-user access must fail
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set(validWorkspace(BOB_UID, past)));
});

test("Comments subcollection supports create, list, status transitions, and locks REPLIED terminal state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Empty subcollection list works for owner, fails for non-owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("comments").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("comments").get());

  // Create comment (NO @DocumentId 'id' in payload)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("comments").doc("c_1").set(validComment(ALICE_UID, past))
  );

  // Including 'id' ghost key must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("comments").doc("c_2").set({
      id: "c_2",
      ...validComment(ALICE_UID, past),
    })
  );

  // Transition PENDING -> REPLIED succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("comments").doc("c_1").update({
      status: "REPLIED",
      generatedReply: "Shukriya bhai! Sensitivity 95-100 try karo headshot ke liye!",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: attempting to re-reply or roll back REPLIED comment must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("comments").doc("c_1").update({
      status: "PENDING",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("ReplyHistory subcollection is create-and-read only (updates forbidden) and isolated per user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("replyHistory").doc("h_1").set({
      userId: ALICE_UID,
      youtubeCommentId: "UgwTestComment123",
      channelId: "UC123456",
      videoTitle: "Free Fire Ranked Clash Squad",
      authorName: "ProPlayerYT",
      originalComment: "OP gameplay bhai!",
      replyText: "Thanks bhai! Next stream mein milte hain!",
      language: "HINGLISH",
      status: "SENT",
      createdAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("replyHistory").get()
  );

  // Immutable audit log: update must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("replyHistory").doc("h_1").update({
      replyText: "Tampered",
    })
  );

  // Bob cannot read Alice's replyHistory
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("replyHistory").get()
  );
});
