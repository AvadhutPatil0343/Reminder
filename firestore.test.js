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
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";
const CHARLIE_UID = "charlie_789";
const CHARLIE_EMAIL = "charlie@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validMedicinePayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    profileId: "self",
    profileName: "Self",
    title: "Metformin 500mg",
    notes: "Take with warm water",
    dueAtMillis: Date.now() + 3600_000,
    snoozedUntilMillis: 0,
    leadTimeMinutes: 15,
    priority: "HIGH",
    category: "DIABETES",
    recurrence: "EVERY_12_HOURS",
    completed: false,
    pinned: true,
    urgentAlarm: true,
    locationLabel: "Home Pharmacy",
    latitude: 18.5204,
    longitude: 73.8567,
    radiusMeters: 250,
    locationTriggerEnabled: true,
    dosage: "1 Tablet (500 mg)",
    medForm: "TABLET",
    mealInstruction: "AFTER_FOOD",
    prescribedBy: "Dr. Kulkarni",
    stockCount: 30,
    dosePerIntake: 1,
    refillThreshold: 5,
    takenCount: 0,
    skippedCount: 0,
    lastDoseStatus: "PENDING",
    courseDays: 5,
    courseStartMillis: Date.now() - 86_400_000,
    subtasks: [{ id: "sub_1", title: "Check blood sugar first", completed: false }],
    completedAtMillis: 0,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validSharedMedicinePayload(ownerUid, ownerEmail, participants, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    ownerId: ownerUid,
    ownerEmail,
    participantEmails: participants,
    title: "Amlodipine 5mg",
    notes: "Morning BP medicine for Baba",
    dueAtMillis: Date.now() + 7200_000,
    snoozedUntilMillis: 0,
    leadTimeMinutes: 15,
    priority: "URGENT",
    category: "HEART_BP",
    recurrence: "DAILY",
    completed: false,
    pinned: false,
    dosage: "1 Tablet",
    medForm: "TABLET",
    mealInstruction: "AFTER_FOOD",
    prescribedBy: "Dr. Joshi",
    stockCount: 15,
    dosePerIntake: 1,
    refillThreshold: 4,
    takenCount: 0,
    skippedCount: 0,
    lastDoseStatus: "PENDING",
    subtasks: [{ id: "sub_1", title: "Measure BP before dose", completed: false }],
    completedAtMillis: 0,
    createdAt: past,
    updatedAt: past,
    ...overrides,
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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user: cannot read or write medicines, profiles, vitals, or appointments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("reminders").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("family_profiles").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("health_vitals").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("appointments").get());
  await assertFails(unauthDb.collection("shared_reminders").get());
});

test("Authenticated owner: valid medicine create, dose taken + stock decrement + refill update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders");
  const docRef = colRef.doc("med_1");

  await assertSucceeds(docRef.set(validMedicinePayload(ALICE_UID)));
  await assertSucceeds(colRef.get());
  await assertSucceeds(
    docRef.update({
      completed: false,
      completedAtMillis: Date.now(),
      dueAtMillis: Date.now() + 12 * 3600_000,
      snoozedUntilMillis: 0,
      stockCount: 29,
      takenCount: 1,
      lastDoseStatus: "TAKEN",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    docRef.update({
      stockCount: 59,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Family profiles, health vitals, and doctor appointments enforce owner isolation and field validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const past = new Date(Date.now() - 2000);

  const profRef = aliceDb.collection("users").doc(ALICE_UID).collection("family_profiles").doc("aai_1");
  await assertSucceeds(
    profRef.set({
      userId: ALICE_UID,
      name: "Aai",
      relation: "Mother",
      doctorName: "Dr. Deshmukh",
      doctorPhone: "+919800000001",
      emergencyPhone: "+919800000002",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("family_profiles").get());

  const vitalRef = aliceDb.collection("users").doc(ALICE_UID).collection("health_vitals").doc("v_1");
  await assertSucceeds(
    vitalRef.set({
      userId: ALICE_UID,
      profileId: "aai_1",
      type: "BP",
      valuePrimary: 124,
      valueSecondary: 82,
      unit: "mmHg",
      notes: "Morning reading",
      recordedAtMillis: Date.now() - 1000,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("health_vitals").doc("v_bad").set({
      userId: ALICE_UID,
      profileId: "aai_1",
      type: "INVALID_TYPE",
      valuePrimary: 120,
      valueSecondary: 80,
      unit: "mmHg",
      recordedAtMillis: Date.now(),
      createdAt: past,
      updatedAt: past,
    })
  );

  const apptRef = aliceDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_1");
  await assertSucceeds(
    apptRef.set({
      userId: ALICE_UID,
      profileId: "aai_1",
      doctorName: "Dr. Deshmukh",
      specialty: "Cardiologist",
      clinicName: "City Care",
      clinicPhone: "+919800000001",
      appointmentAtMillis: Date.now() + 86_400_000,
      notes: "Bring ECG report",
      completed: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(apptRef.update({ completed: true, updatedAt: new Date(Date.now() - 200) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_1").delete());
});

test("Rejects invalid medForm, invalid mealInstruction, out-of-range stock, ghost fields, and @DocumentId id", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders");

  await assertFails(colRef.doc("bad_id").set({ ...validMedicinePayload(ALICE_UID), id: "bad_id" }));
  await assertFails(colRef.doc("bad_ghost").set({ ...validMedicinePayload(ALICE_UID), adminFlag: true }));
  await assertFails(colRef.doc("bad_form").set(validMedicinePayload(ALICE_UID, { medForm: "MAGIC_POTION" })));
  await assertFails(colRef.doc("bad_meal").set(validMedicinePayload(ALICE_UID, { mealInstruction: "MIDNIGHT" })));
  await assertFails(colRef.doc("bad_stock").set(validMedicinePayload(ALICE_UID, { stockCount: -99 })));
  await assertFails(colRef.doc("bad_course").set(validMedicinePayload(ALICE_UID, { courseDays: -5 })));
});

test("Caregiver shared medicines: verified caregiver can mark dose taken & update stock; unverified & outsiders fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const spoofedBobDb = testEnv.authenticatedContext("spoof_bob", { email: BOB_EMAIL, email_verified: false }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();

  const sharedRef = aliceDb.collection("shared_reminders").doc("smed_1");
  await assertSucceeds(
    sharedRef.set(validSharedMedicinePayload(ALICE_UID, ALICE_EMAIL, [ALICE_EMAIL, BOB_EMAIL]))
  );

  await assertSucceeds(
    bobDb.collection("shared_reminders").where("participantEmails", "array-contains", BOB_EMAIL).get()
  );
  await assertFails(bobDb.collection("shared_reminders").get());
  await assertFails(spoofedBobDb.collection("shared_reminders").doc("smed_1").get());
  await assertFails(charlieDb.collection("shared_reminders").doc("smed_1").get());

  await assertSucceeds(
    bobDb.collection("shared_reminders").doc("smed_1").update({
      completed: false,
      completedAtMillis: Date.now(),
      dueAtMillis: Date.now() + 24 * 3600_000,
      snoozedUntilMillis: 0,
      stockCount: 14,
      takenCount: 1,
      lastDoseStatus: "TAKEN",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    bobDb.collection("shared_reminders").doc("smed_1").update({
      title: "Hijacked Med",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(bobDb.collection("shared_reminders").doc("smed_1").delete());
  await assertSucceeds(sharedRef.delete());
});
