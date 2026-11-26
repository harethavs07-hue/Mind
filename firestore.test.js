const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("messages").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      memoryEnabled: true,
      faceConsentGranted: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("UserProfile: owner create, partial update, ghost field & future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      memoryEnabled: true,
      faceConsentGranted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      memoryEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      memoryEnabled: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Memories subcollection: owner create, update, list, delete; cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Empty subcollection list query must succeed for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        category: "GOAL",
        title: "Build climate hardware startup",
        content: "Focus on low-cost atmospheric water sensors for small farms.",
        approved: false,
        source: "SHADOW_PROPOSED",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Partial update: approve & refine memory
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .update({
        approved: true,
        content: "Focus on solar-powered soil & water sensors under $40.",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject extra @DocumentId `id` field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_2")
      .set({
        id: "mem_2",
        userId: ALICE_UID,
        category: "IDEA",
        title: "Extra ID",
        content: "Should fail",
        approved: true,
        source: "USER",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject cross-user read, list, update, delete
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .delete()
  );

  // Owner can delete
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .delete()
  );
});

test("Messages subcollection: owner create, ordered list, delete; cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        role: "SHADOW",
        text: "Welcome. I am your Shadow. Tell me what you are thinking about, and we will explore it together.",
        reasoningMode: "GREETING",
        clientOrder: 1732570000000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Ordered query mirroring MessageRepository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("messages")
      .orderBy("clientOrder", "asc")
      .get()
  );

  // Reject invalid role
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("messages")
      .doc("msg_2")
      .set({
        userId: ALICE_UID,
        role: "SYSTEM",
        text: "Invalid role",
        reasoningMode: "UNDERSTAND",
        clientOrder: 1732570000001,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user list denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("messages").get()
  );
});
