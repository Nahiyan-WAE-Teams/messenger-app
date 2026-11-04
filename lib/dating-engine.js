const DATA_VERSION = 2;

export function normalizeText(value) {
  return String(value ?? "").toLocaleLowerCase().normalize("NFKC").replace(/[^\p{L}\p{N}\s']/gu, " ").replace(/\s+/g, " ").trim();
}

export function normalizeProfile(profile = {}) {
  const name = String(profile.name ?? "").trim();
  const age = Number(profile.age);
  return {
    ...profile,
    id: String(profile.id ?? ""),
    kind: profile.kind === "real" ? "real" : "fictional",
    name,
    age: Number.isFinite(age) ? age : 18,
    bio: String(profile.bio ?? ""),
    intent: String(profile.intent ?? "Any"),
    interests: Array.isArray(profile.interests) ? [...new Set(profile.interests.map(String).map((x) => x.trim()).filter(Boolean))] : [],
    personalityTags: Array.isArray(profile.personalityTags) ? [...new Set(profile.personalityTags.map(normalizeText).filter(Boolean))] : [],
    values: Array.isArray(profile.values) ? [...new Set(profile.values.map(normalizeText).filter(Boolean))] : [],
    lifestyle: Array.isArray(profile.lifestyle) ? [...new Set(profile.lifestyle.map(normalizeText).filter(Boolean))] : [],
    active: profile.active !== false,
  };
}

export function migrateDatingData(raw = {}) {
  const source = raw && typeof raw === "object" ? raw : {};
  const oldState = source.state && typeof source.state === "object" ? source.state : {};
  const state = {
    likedProfileIds: Array.isArray(oldState.likedProfileIds) ? oldState.likedProfileIds : [],
    passedProfileIds: Array.isArray(oldState.passedProfileIds) ? oldState.passedProfileIds : [],
    blockedProfileIds: Array.isArray(oldState.blockedProfileIds) ? oldState.blockedProfileIds : [],
    matches: Array.isArray(oldState.matches) ? oldState.matches : [],
    chats: Array.isArray(oldState.chats) ? oldState.chats : [],
    ownProfileId: oldState.ownProfileId ?? "me",
    discoveryHistory: Array.isArray(oldState.discoveryHistory) ? oldState.discoveryHistory : [],
    interactions: Array.isArray(oldState.interactions) ? oldState.interactions : [],
    relationships: oldState.relationships && typeof oldState.relationships === "object" ? oldState.relationships : {},
  };
  return {
    version: DATA_VERSION,
    profiles: Array.isArray(source.profiles) ? source.profiles.map(normalizeProfile) : [],
    state,
    preferences: source.preferences && typeof source.preferences === "object" ? source.preferences : source.filters ?? {},
    own: source.own && typeof source.own === "object" ? source.own : {},
  };
}

function overlapRatio(left = [], right = []) {
  const a = new Set(left.map(normalizeText).filter(Boolean));
  const b = new Set(right.map(normalizeText).filter(Boolean));
  if (!a.size || !b.size) return 0;
  let shared = 0;
  for (const value of a) if (b.has(value)) shared += 1;
  return shared / Math.max(a.size, b.size);
}

function chemistry(profile, preferences = {}) {
  const interest = overlapRatio(preferences.interests ?? [], profile.interests ?? []);
  const values = overlapRatio(preferences.values ?? [], profile.values ?? []);
  const lifestyle = overlapRatio(preferences.lifestyle ?? [], profile.lifestyle ?? []);
  const personality = overlapRatio(preferences.personalityTags ?? [], profile.personalityTags ?? []);
  const intentFit = !preferences.intent || preferences.intent === "Any" || profile.intent === preferences.intent ? 1 : 0;
  const ageFit = profile.age >= (preferences.minAge ?? 18) && profile.age <= (preferences.maxAge ?? 99) ? 1 : 0;
  const signals = [
    [interest, 0.28, (preferences.interests ?? []).length > 0],
    [values, 0.22, (preferences.values ?? []).length > 0],
    [lifestyle, 0.16, (preferences.lifestyle ?? []).length > 0],
    [personality, 0.12, (preferences.personalityTags ?? []).length > 0],
    [intentFit, 0.14, Boolean(preferences.intent && preferences.intent !== "Any")],
    [ageFit, 0.08, true],
  ];
  const active = signals.filter(([, , enabled]) => enabled);
  const availableWeight = active.reduce((sum, [, weight]) => sum + weight, 0);
  return availableWeight ? active.reduce((sum, [value, weight]) => sum + value * weight, 0) / availableWeight : 0.5;
}

function profileSimilarity(a, b) {
  const interests = overlapRatio(a.interests, b.interests);
  const personality = overlapRatio(a.personalityTags, b.personalityTags);
  const values = overlapRatio(a.values, b.values);
  return interests * 0.55 + personality * 0.25 + values * 0.2;
}

function stableTieBreak(profile, index, seed) {
  const text = String(profile.id ?? index) + ":" + seed;
  let hash = 2166136261;
  for (let i = 0; i < text.length; i += 1) hash = Math.imul(hash ^ text.charCodeAt(i), 16777619);
  return hash >>> 0;
}

export function getDiscoverProfiles(profiles, preferences = {}, state = {}) {
  const liked = new Set(state.likedProfileIds || []);
  const passed = new Set(state.passedProfileIds || []);
  const blocked = new Set(state.blockedProfileIds || []);
  const minAge = Number.isFinite(preferences.minAge) ? preferences.minAge : 18;
  const maxAge = Number.isFinite(preferences.maxAge) ? preferences.maxAge : 99;
  const seed = (state.interactions || []).length + (state.discoveryHistory || []).length;
  const candidates = (Array.isArray(profiles) ? profiles : [])
    .map(normalizeProfile)
    .filter((profile) => profile.id && profile.active && profile.id !== state.ownProfileId)
    .filter((profile) => !liked.has(profile.id) && !passed.has(profile.id) && !blocked.has(profile.id))
    .filter((profile) => profile.age >= minAge && profile.age <= maxAge)
    .filter((profile) => !preferences.intent || preferences.intent === "Any" || profile.intent === preferences.intent)
    .map((profile, index) => ({ profile, relevance: chemistry(profile, preferences), tie: stableTieBreak(profile, index, seed) }));

  // Greedy diversity ranking: keep relevance, but avoid showing near-duplicates back-to-back.
  const ranked = [];
  while (candidates.length) {
    let bestIndex = 0;
    let bestUtility = -Infinity;
    for (let i = 0; i < candidates.length; i += 1) {
      const candidate = candidates[i];
      const similarity = ranked.length ? Math.max(...ranked.map((chosen) => profileSimilarity(candidate.profile, chosen.profile))) : 0;
      const utility = candidate.relevance * 0.47 + (1 - similarity) * 0.48 + (candidate.tie / 0xffffffff) * 0.05;
      if (utility > bestUtility) { bestUtility = utility; bestIndex = i; }
    }
    ranked.push(candidates.splice(bestIndex, 1)[0]);
  }
  return ranked.map((item) => item.profile);
}

export function applyProfileAction(state, profile, action, now = Date.now()) {
  const next = {
    ...state,
    likedProfileIds: [...(state.likedProfileIds || [])],
    passedProfileIds: [...(state.passedProfileIds || [])],
    blockedProfileIds: [...(state.blockedProfileIds || [])],
    matches: [...(state.matches || [])],
    interactions: [...(state.interactions || [])],
    discoveryHistory: [...(state.discoveryHistory || [])],
  };
  if (!profile || !profile.id || !["like", "pass"].includes(action)) return { state: next, matched: false };
  const target = action === "like" ? next.likedProfileIds : next.passedProfileIds;
  if (!target.includes(profile.id)) target.push(profile.id);
  next.interactions.push({ profileId: profile.id, action, at: now, kind: profile.kind === "real" ? "real" : "fictional" });
  next.discoveryHistory.push(profile.id);
  next.discoveryHistory = next.discoveryHistory.slice(-80);
  const matched = action === "like" && profile.kind !== "real" && profile.likesUser === true;
  if (matched && !next.matches.some((item) => item.profileId === profile.id)) {
    next.matches.unshift({ id: "match-" + profile.id, profileId: profile.id, createdAt: now, unread: true });
  }
  return { state: next, matched };
}

export function chooseProfileReply(message, rules = [], fallback = "I haven't added a reply for that yet.", stage = "first-meeting") {
  const normalized = normalizeText(message);
  const padded = " " + normalized + " ";
  const candidates = (Array.isArray(rules) ? rules : [])
    .filter((rule) => rule && rule.enabled !== false && (!rule.stage || rule.stage === stage) && Array.isArray(rule.triggers) && Array.isArray(rule.replies))
    .map((rule) => ({
      rule,
      stagePriority: rule.stage === stage ? 1 : 0.5,
      score: rule.triggers.map(normalizeText).filter(Boolean).filter((trigger) => padded.includes(" " + trigger + " ")).reduce((sum, trigger) => sum + trigger.length + 10, 0),
    }))
    .filter((item) => item.score > 0 && item.rule.replies.some((reply) => typeof reply === "string" && reply.trim()));
  candidates.sort((a, b) => b.stagePriority - a.stagePriority || b.score - a.score);
  if (!candidates.length) return fallback;
  const replies = candidates[0].rule.replies.filter((reply) => typeof reply === "string" && reply.trim());
  return replies[0].trim();
}
