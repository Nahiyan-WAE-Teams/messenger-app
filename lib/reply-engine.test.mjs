import test from "node:test";
import assert from "node:assert/strict";
import { chooseLocalReply, normalizeMessage } from "./reply-engine.js";

test("normalizes case and punctuation before matching", () => {
  assert.equal(normalizeMessage("HELLO, Mira!!"), "hello mira");
});
test("matches whole-word triggers, not substrings", () => {
  assert.equal(chooseLocalReply("hello there", [{ triggers: ["hello"], replies: ["Hi there"] }]), "Hi there");
  assert.equal(chooseLocalReply("shelloworld", [{ triggers: ["hello"], replies: ["Hi there"] }], "fallback"), "fallback");
});
test("prefers the most specific matching trigger", () => {
  const rules = [{ triggers: ["hello"], replies: ["General"] }, { triggers: ["good morning"], replies: ["Morning"] }];
  assert.equal(chooseLocalReply("Well, good morning!", rules), "Morning");
});
test("uses fallback when no enabled rule matches", () => {
  assert.equal(chooseLocalReply("unknown", [{ triggers: ["hello"], replies: ["Hi"], enabled: false }], "Fallback"), "Fallback");
});
test("ignores empty reply variations", () => {
  assert.equal(chooseLocalReply("hello", [{ triggers: ["hello"], replies: ["", "  ", "Hi"] }]), "Hi");
});
