export function normalizeMessage(value) {
  return String(value ?? "").toLocaleLowerCase().normalize("NFKC").replace(/[^\\p{L}\\p{N}\\s']/gu, " ").replace(/\\s+/g, " ").trim();
}

export function chooseLocalReply(message, rules, fallback = "I’m not sure what to say to that yet. Can you add a reply for it in my Reply Library?") {
  const normalized = normalizeMessage(message);
  if (!normalized) return fallback;
  const padded = " " + normalized + " ";
  const candidates = (Array.isArray(rules) ? rules : [])
    .filter((rule) => rule && rule.enabled !== false && Array.isArray(rule.triggers) && Array.isArray(rule.replies))
    .map((rule) => {
      const matched = rule.triggers.map(normalizeMessage).filter(Boolean).filter((trigger) => padded.includes(" " + trigger + " "));
      return { rule, score: matched.reduce((score, trigger) => score + trigger.length + 10, 0), matchedCount: matched.length };
    })
    .filter((candidate) => candidate.matchedCount > 0 && candidate.rule.replies.some((reply) => typeof reply === "string" && reply.trim()));
  candidates.sort((a, b) => b.score - a.score || b.matchedCount - a.matchedCount);
  const winner = candidates[0];
  if (!winner) return fallback;
  const replies = winner.rule.replies.filter((reply) => typeof reply === "string" && reply.trim());
  return replies[Math.floor(Math.random() * replies.length)].trim();
}
