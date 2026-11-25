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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRecipePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    title: "Roasted Garlic & Tomato Rigatoni",
    description: "Silky roasted tomato sauce with sweet garlic confit and basil.",
    sourceType: "PERSONAL",
    prepMinutes: 15,
    cookMinutes: 30,
    servings: 4,
    ingredients: ["400g rigatoni", "6 cloves garlic", "800g cherry tomatoes", "Fresh basil"],
    steps: ["Roast tomatoes and garlic at 200C for 25 minutes.", "Toss with al dente pasta and basil."],
    tags: ["Italian", "Weeknight", "Vegetarian"],
    favorite: false,
    offlinePinned: true,
    rating: 5,
    notes: "Save a cup of pasta water for emulsifying.",
    createdAt: now,
    updatedAt: now,
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read, list, or write recipes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("recipes").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("recipes").doc("r1").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recipes")
      .doc("r1")
      .set(validRecipePayload(ALICE_UID))
  );
});

test("Authenticated owner: valid create, list, get, partial toggle updates, full edit, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const recipesRef = aliceDb.collection("users").doc(ALICE_UID).collection("recipes");

  // Create personal recipe (no @DocumentId `id` field, optional fields omitted)
  await assertSucceeds(recipesRef.doc("recipe_1").set(validRecipePayload(ALICE_UID)));

  // Create online recipe with optional sourceUrl, sourceName, imageUrl
  await assertSucceeds(
    recipesRef.doc("recipe_2").set(
      validRecipePayload(ALICE_UID, {
        title: "Crispy Chili Oil Noodles",
        sourceType: "ONLINE",
        sourceUrl: "https://example.com/chili-oil-noodles",
        sourceName: "example.com",
        imageUrl: "https://example.com/noodles.jpg",
      })
    )
  );

  // List & Get
  await assertSucceeds(recipesRef.get());
  await assertSucceeds(recipesRef.doc("recipe_1").get());

  // Partial update: favorite toggle
  await assertSucceeds(
    recipesRef.doc("recipe_1").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: offlinePinned toggle
  await assertSucceeds(
    recipesRef.doc("recipe_1").update({
      offlinePinned: false,
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Partial update: rating update
  await assertSucceeds(
    recipesRef.doc("recipe_1").update({
      rating: 4,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Full edit update (without touching createdAt or userId)
  await assertSucceeds(
    recipesRef.doc("recipe_1").update({
      title: "Charred Cherry Tomato Rigatoni",
      description: "Updated description",
      sourceType: "PERSONAL",
      prepMinutes: 10,
      cookMinutes: 25,
      servings: 2,
      ingredients: ["250g rigatoni", "4 cloves garlic"],
      steps: ["Roast tomatoes", "Boil pasta"],
      tags: ["Quick"],
      favorite: true,
      offlinePinned: true,
      rating: 5,
      notes: "Extra chili flakes!",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete
  await assertSucceeds(recipesRef.doc("recipe_2").delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's recipes", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("recipes")
      .doc("alice_recipe")
      .set(validRecipePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceRecipes = bobDb.collection("users").doc(ALICE_UID).collection("recipes");

  await assertFails(aliceRecipes.get());
  await assertFails(aliceRecipes.doc("alice_recipe").get());
  await assertFails(aliceRecipes.doc("bob_sneak").set(validRecipePayload(BOB_UID)));
  await assertFails(
    aliceRecipes.doc("alice_recipe").update({ favorite: true, updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(aliceRecipes.doc("alice_recipe").delete());
});

test("Security & validation bounds reject ghost fields, @DocumentId id, immutable edits, bad types, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const recipesRef = aliceDb.collection("users").doc(ALICE_UID).collection("recipes");

  // Reject ghost field on create
  await assertFails(
    recipesRef.doc("bad_ghost").set(validRecipePayload(ALICE_UID, { isVerified: true }))
  );

  // Reject @DocumentId `id` field inside document body
  await assertFails(
    recipesRef.doc("bad_id").set(validRecipePayload(ALICE_UID, { id: "bad_id" }))
  );

  // Reject empty title
  await assertFails(
    recipesRef.doc("bad_title").set(validRecipePayload(ALICE_UID, { title: "" }))
  );

  // Reject empty ingredients list
  await assertFails(
    recipesRef.doc("bad_ing").set(validRecipePayload(ALICE_UID, { ingredients: [] }))
  );

  // Reject invalid sourceType
  await assertFails(
    recipesRef.doc("bad_src").set(validRecipePayload(ALICE_UID, { sourceType: "AI_GENERATED" }))
  );

  // Reject tampered future timestamp
  await assertFails(
    recipesRef
      .doc("bad_time")
      .set(validRecipePayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Seed valid recipe for update attack tests
  await assertSucceeds(recipesRef.doc("r_guard").set(validRecipePayload(ALICE_UID)));

  // Reject immutable createdAt modification
  await assertFails(
    recipesRef.doc("r_guard").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject immutable userId modification
  await assertFails(
    recipesRef.doc("r_guard").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field on update
  await assertFails(
    recipesRef.doc("r_guard").update({
      favorite: true,
      adminOverride: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
