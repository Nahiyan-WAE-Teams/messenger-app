import test from "node:test";
import assert from "node:assert/strict";
import { applyProfileAction, chooseProfileReply, getDiscoverProfiles, migrateDatingData, normalizeProfile } from "./dating-engine.js";

const profiles = [
  { id: "a", kind: "fictional", age: 25, active: true, intent: "Long-term", interests: ["Music", "Travel"], likesUser: true, personalityTags: ["curious", "creative"] },
  { id: "b", kind: "fictional", age: 27, active: true, intent: "Long-term", interests: ["Music", "Travel"], personalityTags: ["curious", "creative"] },
  { id: "c", kind: "fictional", age: 28, active: true, intent: "Friendship first", interests: ["Gardening", "Books"], personalityTags: ["thoughtful", "quiet"] },
  { id: "d", kind: "real", age: 17, active: true, intent: "Long-term", interests: ["Music"] },
  { id: "e", kind: "fictional", age: 29, active: false, intent: "Long-term", interests: ["Travel"] },
];

test("discovery respects age, intent, active state and interaction exclusions", () => {
  const found = getDiscoverProfiles(profiles, { minAge: 18, maxAge: 30, intent: "Any" }, { likedProfileIds: [], passedProfileIds: [], blockedProfileIds: ["a"] });
  assert.equal(found.some((p) => p.id === "a" || p.id === "d" || p.id === "e"), false);
});

test("discovery returns profiles without exposing a compatibility score", () => {
  const found = getDiscoverProfiles(profiles, { minAge: 18, maxAge: 30, interests: ["Music", "Travel"] }, {});
  assert.ok(found.length > 0);
  assert.equal(Object.hasOwn(found[0], "compatibilityScore"), false);
  assert.equal(Object.hasOwn(found[0], "score"), false);
});

test("discovery balances relevance with profile variety", () => {
  const found = getDiscoverProfiles(profiles.slice(0, 3), { interests: ["Music", "Travel"] }, {});
  assert.equal(found[0].id, "a");
  assert.equal(found[1].id, "c");
  assert.equal(found.length, 3);
});

test("discovery is deterministic for the same inputs", () => {
  const preferences = { interests: ["Music", "Travel"] };
  assert.deepEqual(getDiscoverProfiles(profiles, preferences, {}), getDiscoverProfiles(profiles, preferences, {}));
});

test("a like creates a match only when mutual interest is explicitly recorded", () => {
  const result = applyProfileAction({ likedProfileIds: [], matches: [] }, profiles[0], "like", 123);
  assert.equal(result.matched, true);
  assert.equal(result.state.matches[0].profileId, "a");
  assert.equal(result.state.matches[0].createdAt, 123);
  const noMutual = applyProfileAction({ likedProfileIds: [], matches: [] }, { ...profiles[1], kind: "real", likesUser: false }, "like", 123);
  assert.equal(noMutual.matched, false);
});

test("a pass records the action without creating a match", () => {
  const result = applyProfileAction({ passedProfileIds: [], matches: [] }, profiles[0], "pass");
  assert.equal(result.matched, false);
  assert.deepEqual(result.state.passedProfileIds, ["a"]);
  assert.equal(result.state.matches.length, 0);
});

test("local dialogue selects the most specific matching written reply", () => {
  const rules = [{ triggers: ["hello"], replies: ["Hey!"] }, { triggers: ["good morning"], replies: ["Morning!"] }];
  assert.equal(chooseProfileReply("Well, good morning!", rules), "Morning!");
});

test("unknown messages use the configured fallback without inventing a reply", () => {
  assert.equal(chooseProfileReply("Tell me a secret", [], "Not written yet"), "Not written yet");
});

test("profile normalization supplies safe, versioned defaults", () => {
  const profile = normalizeProfile({ id: "x", name: "  Alex  ", age: 25, kind: "real" });
  assert.equal(profile.name, "Alex");
  assert.equal(profile.kind, "real");
  assert.deepEqual(profile.interests, []);
  assert.equal(profile.active, true);
});

test("legacy local data migrates without dropping profiles or interaction history", () => {
  const migrated = migrateDatingData({
    profiles: [{ id: "legacy", name: "Legacy", age: 24 }],
    state: { likedProfileIds: ["legacy"], passedProfileIds: ["old"], matches: [], chats: [] },
  });
  assert.equal(migrated.version, 2);
  assert.equal(migrated.profiles[0].id, "legacy");
  assert.deepEqual(migrated.state.likedProfileIds, ["legacy"]);
  assert.deepEqual(migrated.state.passedProfileIds, ["old"]);
});
