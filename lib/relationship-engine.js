const MAX_ACTIVE_DAYS = 30;

export function createRelationshipState(profileId) {
  return {
    profileId: String(profileId ?? ""),
    exchangeCount: 0,
    activeDays: [],
    lastInteractionAt: null,
  };
}

export function getRelationshipStage(state = {}) {
  const exchanges = Math.max(0, Number(state.exchangeCount) || 0);
  const activeDays = Array.isArray(state.activeDays) ? new Set(state.activeDays).size : 0;
  if (exchanges >= 10 && activeDays >= 3) return "close";
  if (exchanges >= 5 && activeDays >= 2) return "familiar";
  if (exchanges >= 1) return "getting-to-know";
  return "first-meeting";
}

export function recordRelationshipExchange(relationships = {}, profile, timestamp = Date.now()) {
  if (!profile || profile.kind !== "fictional" || !profile.id) return relationships;
  const date = new Date(timestamp);
  if (!Number.isFinite(date.getTime())) return relationships;

  const current = relationships && typeof relationships === "object"
    ? relationships
    : {};
  const previous = current[profile.id] ?? createRelationshipState(profile.id);
  const day = date.toISOString().slice(0, 10);
  const activeDays = [...new Set([...(Array.isArray(previous.activeDays) ? previous.activeDays : []), day])].slice(-MAX_ACTIVE_DAYS);

  return {
    ...current,
    [profile.id]: {
      ...previous,
      profileId: profile.id,
      exchangeCount: Math.max(0, Number(previous.exchangeCount) || 0) + 1,
      activeDays,
      lastInteractionAt: date.getTime(),
    },
  };
}
