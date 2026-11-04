import test from "node:test";
import assert from "node:assert/strict";
import { applyProfileAction, chooseProfileReply, getCompatibilityScore, getDiscoverProfiles } from "./dating-engine.js";

const profiles = [
  { id: "a", age: 25, active: true, intent: "Long-term", interests: ["Music", "Travel"], likesUser: true },
  { id: "b", age: 17, active: true, intent: "Long-term", interests: ["Music"] },
  { id: "c", age: 28, active: false, intent: "Casual", interests: ["Travel"] },
];

test("discovery excludes inactive, out-of-age, liked, passed and blocked profiles", () => {
  const found = getDiscoverProfiles(profiles, { minAge: 18, maxAge: 30 }, { likedProfileIds: [], passedProfileIds: [], blockedProfileIds: ["a"] });
  assert.deepEqual(found, []);
});
test("discovery sorts by locally calculated compatibility", () => {
  const found = getDiscoverProfiles(profiles, { minAge: 18, maxAge: 30, interests: ["Music", "Travel"] }, {});
  assert.equal(found[0].id, "a");
  assert.equal(getCompatibilityScore(found[0], { interests: ["Music", "Travel"] }), 100);
});
test("a like creates a match only when the local profile data says interest is mutual", () => {
  const result = applyProfileAction({ likedProfileIds: [], matches: [] }, profiles[0], "like", 123);
  assert.equal(result.matched, true);
  assert.equal(result.state.matches[0].profileId, "a");
  assert.equal(result.state.matches[0].createdAt, 123);
});
test("a pass does not create a match", () => {
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
