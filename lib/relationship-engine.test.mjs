import test from "node:test";
import assert from "node:assert/strict";
import { createRelationshipState, getRelationshipStage, recordRelationshipExchange } from "./relationship-engine.js";

test("a new fictional profile starts with no assumed intimacy", () => {
  const state = createRelationshipState("ava");
  assert.equal(state.profileId, "ava");
  assert.equal(state.exchangeCount, 0);
  assert.equal(getRelationshipStage(state), "first-meeting");
});

test("completed exchanges gradually build familiarity without a visible score", () => {
  let relationships = { ava: createRelationshipState("ava") };
  relationships = recordRelationshipExchange(relationships, { id: "ava", kind: "fictional" }, Date.parse("2026-11-01T10:00:00Z"));
  assert.equal(getRelationshipStage(relationships.ava), "getting-to-know");
  for (let i = 0; i < 4; i += 1) {
    relationships = recordRelationshipExchange(relationships, { id: "ava", kind: "fictional" }, Date.parse("2026-11-02T10:00:00Z") + i);
  }
  assert.equal(getRelationshipStage(relationships.ava), "familiar");
  for (let i = 0; i < 5; i += 1) {
    relationships = recordRelationshipExchange(relationships, { id: "ava", kind: "fictional" }, Date.parse("2026-11-03T10:00:00Z") + i);
  }
  assert.equal(getRelationshipStage(relationships.ava), "close");
  assert.equal(Object.hasOwn(relationships.ava, "score"), false);
  assert.equal(Object.hasOwn(relationships.ava, "compatibility"), false);
});

test("familiarity requires both conversation and time together", () => {
  let relationships = { ava: createRelationshipState("ava") };
  for (let i = 0; i < 8; i += 1) {
    relationships = recordRelationshipExchange(relationships, { id: "ava", kind: "fictional" }, Date.parse("2026-11-01T10:00:00Z") + i);
  }
  assert.equal(getRelationshipStage(relationships.ava), "getting-to-know");
});

test("relationship history is isolated by profile and does not mutate prior state", () => {
  const initial = {};
  const next = recordRelationshipExchange(initial, { id: "ava", kind: "fictional" }, Date.parse("2026-11-01T10:00:00Z"));
  assert.deepEqual(initial, {});
  assert.equal(next.ava.exchangeCount, 1);
  assert.equal(next.jules, undefined);
});

test("real-person private records never receive simulated relationship progression", () => {
  const initial = { ava: createRelationshipState("ava") };
  const next = recordRelationshipExchange(initial, { id: "real-1", kind: "real" }, Date.now());
  assert.deepEqual(next, initial);
});
