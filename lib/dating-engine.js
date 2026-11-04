export function normalizeText(value) {
  return String(value ?? "").toLocaleLowerCase().normalize("NFKC").replace(/[^\p{L}\p{N}\s']/gu, " ").replace(/\s+/g, " ").trim();
}

export function getDiscoverProfiles(profiles, preferences = {}, state = {}) {
  const liked = new Set(state.likedProfileIds || []);
  const passed = new Set(state.passedProfileIds || []);
  const blocked = new Set(state.blockedProfileIds || []);
  const minAge = Number.isFinite(preferences.minAge) ? preferences.minAge : 18;
  const maxAge = Number.isFinite(preferences.maxAge) ? preferences.maxAge : 99;
  return (Array.isArray(profiles) ? profiles : [])
    .filter((profile) => profile && profile.active !== false && profile.id !== state.ownProfileId)
    .filter((profile) => !liked.has(profile.id) && !passed.has(profile.id) && !blocked.has(profile.id))
    .filter((profile) => profile.age >= minAge && profile.age <= maxAge)
    .filter((profile) => !preferences.intent || preferences.intent === "Any" || profile.intent === preferences.intent)
    .sort((a, b) => getCompatibilityScore(b, preferences) - getCompatibilityScore(a, preferences));
}

export function getCompatibilityScore(profile, preferences = {}) {
  const wanted = new Set((preferences.interests || []).map(normalizeText));
  const theirs = new Set((profile.interests || []).map(normalizeText));
  const shared = [...wanted].filter((interest) => theirs.has(interest)).length;
  const interestScore = wanted.size ? Math.round((shared / wanted.size) * 55) : 25;
  const intentScore = !preferences.intent || preferences.intent === "Any" || profile.intent === preferences.intent ? 25 : 0;
  const ageScore = profile.age >= (preferences.minAge ?? 18) && profile.age <= (preferences.maxAge ?? 99) ? 20 : 0;
  return Math.max(0, Math.min(100, interestScore + intentScore + ageScore));
}

export function applyProfileAction(state, profile, action, now = Date.now()) {
  const next = { ...state, likedProfileIds: [...(state.likedProfileIds || [])], passedProfileIds: [...(state.passedProfileIds || [])], matches: [...(state.matches || [])] };
  if (!profile || !profile.id || !["like", "pass"].includes(action)) return { state: next, matched: false };
  if (action === "pass") {
    if (!next.passedProfileIds.includes(profile.id)) next.passedProfileIds.push(profile.id);
    return { state: next, matched: false };
  }
  if (!next.likedProfileIds.includes(profile.id)) next.likedProfileIds.push(profile.id);
  const matched = profile.likesUser === true;
  if (matched && !next.matches.some((item) => item.profileId === profile.id)) next.matches.unshift({ id: "match-" + profile.id, profileId: profile.id, createdAt: now, unread: true });
  return { state: next, matched };
}

export function chooseProfileReply(message, rules = [], fallback = "I haven't added a reply for that yet.") {
  const normalized = normalizeText(message);
  const padded = " " + normalized + " ";
  const candidates = (Array.isArray(rules) ? rules : [])
    .filter((rule) => rule && rule.enabled !== false && Array.isArray(rule.triggers) && Array.isArray(rule.replies))
    .map((rule) => ({ rule, score: rule.triggers.map(normalizeText).filter(Boolean).filter((trigger) => padded.includes(" " + trigger + " ")).reduce((sum, trigger) => sum + trigger.length + 10, 0) }))
    .filter((item) => item.score > 0 && item.rule.replies.some((reply) => typeof reply === "string" && reply.trim()));
  candidates.sort((a, b) => b.score - a.score);
  if (!candidates.length) return fallback;
  return candidates[0].rule.replies.find((reply) => typeof reply === "string" && reply.trim()).trim();
}
