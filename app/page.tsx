"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { chooseLocalReply } from "../lib/reply-engine.js";

type MemorySuggestion = { category: MemoryCategory; text: string; status: "pending" | "saved" | "dismissed" };
type Message = { id: string; role: "user" | "assistant"; content: string; time: number; memorySuggestion?: MemorySuggestion };
type Conversation = { id: string; title: string; updatedAt: number; messages: Message[] };
const STORAGE_KEY = "mira-messenger-history-v1";
const CONVERSATIONS_KEY = "mira-messenger-conversations-v1";
const SETTINGS_KEY = "mira-messenger-settings-v1";
type CharacterTone = "balanced" | "playful" | "gentle" | "direct";
type ContinuityKind = "Emotional context" | "Unresolved" | "Promise" | "Milestone";
type ContinuityEntry = { id: string; kind: ContinuityKind; text: string; createdAt: number };
type EmotionalMood = "neutral" | "warm" | "playful" | "reflective" | "hurt" | "tired";
type EmotionalState = { mood: EmotionalMood; energy: number; trust: number; closeness: number; tension: number; updatedAt: number };
type ReplyRule = { id: string; triggers: string[]; replies: string[]; enabled: boolean };
type CharacterSettings = { tone: CharacterTone; memory: string; continuity: ContinuityEntry[]; emotionalState: EmotionalState; replyRules: ReplyRule[]; fallbackReply: string };
const DEFAULT_EMOTIONAL_STATE: EmotionalState = { mood: "neutral", energy: 68, trust: 50, closeness: 25, tension: 10, updatedAt: Date.now() };
const DEFAULT_RULES: ReplyRule[] = [{ id: "hello", triggers: ["hello", "hi", "hey"], replies: ["Hey. There you are 😊", "Hi you. How’s your day going?"], enabled: true }, { id: "morning", triggers: ["good morning", "morning"], replies: ["Morning ☀️ Did you sleep okay?"], enabled: true }, { id: "night", triggers: ["good night", "gn"], replies: ["Good night. Get some proper rest, okay? 🌙"], enabled: true }, { id: "miss", triggers: ["i miss you", "missed you"], replies: ["You were gone for a bit. I’m glad you’re here now. 💗"], enabled: true }];
const DEFAULT_FALLBACK = "I don’t have a written reply for that yet. Add one in my Reply Library. 💗";
const DEFAULT_SETTINGS: CharacterSettings = { tone: "balanced", memory: "", continuity: [], emotionalState: DEFAULT_EMOTIONAL_STATE, replyRules: DEFAULT_RULES, fallbackReply: DEFAULT_FALLBACK };
const CONTINUITY_KINDS: ContinuityKind[] = ["Emotional context", "Unresolved", "Promise", "Milestone"];
const EMOTIONAL_MOODS: EmotionalMood[] = ["neutral", "warm", "playful", "reflective", "hurt", "tired"];

function evolveEmotionalState(state: EmotionalState, message: string, now = Date.now()): EmotionalState {
  const text = message.toLowerCase();
  const next = { ...state };

  // Recovery is gradual and only calculated when a new message arrives.
  // Trust and closeness never decay just because time passes.
  const elapsedHours = Math.max(0, (now - state.updatedAt) / (60 * 60 * 1000));
  if (elapsedHours >= 6) next.tension = Math.max(0, next.tension - Math.floor(elapsedHours / 6));
  if (elapsedHours >= 2) next.energy = Math.min(68, next.energy + Math.floor(elapsedHours / 2));
  if (elapsedHours >= 24 && (next.mood === "hurt" || next.mood === "tired")) next.mood = "reflective";
  if (elapsedHours >= 48 && next.mood === "reflective") next.mood = "neutral";

  const seriousHurt = /\b(you lied|you betrayed me|that really hurt|that hurt me|i feel hurt|i'm upset with you|im upset with you|you let me down)\b/.test(text);
  const repeatedBrokenCommitment = /\b(you promised|you said you would|you said you wouldn't|you said you would not|broke your promise|did it again|doing this again|keep doing this|kept doing this|another broken promise)\b/.test(text);
  const hostile = /\b(you are useless|you're useless|shut up|i hate you|you're stupid|you are stupid)\b/.test(text);
  const sincereApology = /\b(i'm sorry|i am sorry|im sorry|i apologize|my bad|i was wrong|i shouldn't have|i should not have|i understand why that hurt|i'll make it right|i will make it right)\b/.test(text);
  const playfulCue = /\b(lol|lmao|😂|🤣|kidding|just joking|i'm teasing|im teasing|that was funny)\b/.test(text);
  const seriousDisagreement = /\b(i disagree|i don't agree|i do not agree|i see it differently|that isn't fair|that is not fair|i don't think that's right|i do not think that's right|we disagree)\b/.test(text);
  const tiredCue = /\b(exhausted|so tired|drained|long day|need some rest)\b/.test(text);
  const reflectiveCue = /\b(thinking about|i've been wondering|i have been wondering|not sure how i feel)\b/.test(text);
  const gratitude = /\b(thank you|thanks|i appreciate you|that means a lot|you helped me)\b/.test(text);
  const affection = /\b(i like you|i care about you|i missed you|i trust you|love you)\b/.test(text);

  // Conflict signals take priority: a joke or apology should not erase real hurt.
  if (seriousHurt || repeatedBrokenCommitment || hostile) {
    next.tension += repeatedBrokenCommitment ? 7 : hostile ? 5 : 6;
    next.trust -= repeatedBrokenCommitment ? 3 : hostile ? 2 : 2;
    next.mood = "hurt";
  } else if (sincereApology) {
    // An apology opens the door to repair, but it does not instantly restore trust.
    next.tension -= 5;
    next.trust += 1;
    next.mood = "reflective";
  } else if (seriousDisagreement) {
    // Disagreement can be respectful and is not automatically a relationship injury.
    next.mood = "reflective";
    next.tension += 1;
  } else if (playfulCue) {
    next.mood = "playful";
    next.energy += 2;
    next.tension -= 1;
  } else if (gratitude) {
    next.tension -= 2;
    next.trust += 2;
    next.closeness += 1;
    next.mood = "warm";
  } else if (affection) {
    next.closeness += 2;
    next.trust += 1;
    next.mood = "warm";
  } else if (reflectiveCue && next.mood === "neutral") {
    next.mood = "reflective";
  }

  if (tiredCue) {
    next.energy -= 5;
    if (next.mood === "neutral") next.mood = "tired";
  }
  next.energy = Math.max(0, Math.min(100, next.energy));
  next.trust = Math.max(0, Math.min(100, next.trust));
  next.closeness = Math.max(0, Math.min(100, next.closeness));
  next.tension = Math.max(0, Math.min(100, next.tension));
  next.updatedAt = now;
  return next;
}
const starter: Message[] = [{ id: "welcome", role: "assistant", content: "Hey, you made it. 😊 I’m Mira. We can take this one conversation at a time. What’s on your mind?", time: Date.now() }];

const MEMORY_CATEGORIES = ["Preferences", "Stories", "People", "Milestones", "General"] as const;
type MemoryCategory = (typeof MEMORY_CATEGORIES)[number];
type MemoryEntry = { category: MemoryCategory; text: string; index: number };

function parseMemoryEntries(memory: string): MemoryEntry[] {
  return memory.split("\n").map((line, index) => {
    const match = line.match(/^\[(Preferences|Stories|People|Milestones|General)\]\s*(.*)$/i);
    const category = match ? MEMORY_CATEGORIES.find((item) => item.toLowerCase() === match[1].toLowerCase()) || "General" : "General";
    return { category, text: (match ? match[2] : line).trim(), index };
  }).filter((entry) => entry.text.length > 0 || /^\[(Preferences|Stories|People|Milestones|General)\]/i.test(memory.split("\n")[entry.index] || ""));
}

function encodeMemoryEntry(category: MemoryCategory, text: string) {
  return `[${category}] ${text.trim().replace(/\s+/g, " ")}`;
}

function suggestMemoryFromMessage(content: string): MemorySuggestion | undefined {
  const text = content.trim().replace(/\s+/g, " ");
  if (text.length < 8 || text.length > 240 || /\b(password|passcode|ssn|social security|credit card|bank account|financial|salary|debt|health|medical|doctor|therap|illness|disease|diagnos(?:ed|is)|medication|depress|anxiet|suicid|self-harm|politic|vote for|religion|religious|sexual orientation|gender identity|immigration status|exact address)\b/i.test(text)) return undefined;
  const patterns: { pattern: RegExp; category: MemoryCategory; prefix: string }[] = [
    { pattern: /\b(?:i like|i love|i enjoy|i prefer|my favorite is|my favourite is|i’m a fan of|i'm a fan of)\s+(.+)/i, category: "Preferences", prefix: "User likes" },
    { pattern: /\b(?:i’m learning|i'm learning|i am learning|i’m working on|i'm working on|i am working on|i’m building|i'm building|i am building|i’ve been practicing|i've been practicing)\s+(.+)/i, category: "Stories", prefix: "User is working on" },
    { pattern: /\b(?:i play|i collect|i spend a lot of time)\s+(.+)/i, category: "Preferences", prefix: "User enjoys" },
  ];
  for (const item of patterns) {
    const match = text.match(item.pattern);
    const detail = match?.[1]?.replace(/[.!?]+$/, "").trim();
    if (detail && detail.length >= 3 && detail.length <= 120) {
      return { category: item.category, text: item.prefix + " " + detail + ".", status: "pending" };
    }
  }
  return undefined;
}

function timeLabel(timestamp: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(timestamp);
}

export default function Home() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState("");
  const [hydrated, setHydrated] = useState(false);
  const activeConversation = conversations.find((conversation) => conversation.id === activeConversationId) ?? conversations[0];
  const messages = activeConversation?.messages ?? starter;
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [characterSettings, setCharacterSettings] = useState<CharacterSettings>(DEFAULT_SETTINGS);
  const [memoryDraft, setMemoryDraft] = useState("");
  const [memoryCategory, setMemoryCategory] = useState<MemoryCategory>("Preferences");
  const [memoryFilter, setMemoryFilter] = useState("All");
  const [continuityDraft, setContinuityDraft] = useState("");
  const [continuityKind, setContinuityKind] = useState<ContinuityKind>("Emotional context");
  const [ruleTriggerDraft, setRuleTriggerDraft] = useState("");
  const [ruleRepliesDraft, setRuleRepliesDraft] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    try {
      const savedSettings = localStorage.getItem(SETTINGS_KEY);
      if (savedSettings) {
        const parsedSettings = JSON.parse(savedSettings);
        if (parsedSettings && ["balanced", "playful", "gentle", "direct"].includes(parsedSettings.tone) && typeof parsedSettings.memory === "string") {
          const savedContinuity = Array.isArray(parsedSettings.continuity) ? parsedSettings.continuity
            .filter((item: unknown) => item && typeof item === "object" && typeof (item as ContinuityEntry).id === "string" &&
              CONTINUITY_KINDS.includes((item as ContinuityEntry).kind) && typeof (item as ContinuityEntry).text === "string")
            .slice(-12).map((item: ContinuityEntry) => ({ ...item, text: item.text.trim().slice(0, 220) })).filter((item: ContinuityEntry) => item.text.length > 0) : [];
          const savedState = parsedSettings.emotionalState && typeof parsedSettings.emotionalState === "object" ? parsedSettings.emotionalState : DEFAULT_EMOTIONAL_STATE;
          const safeState: EmotionalState = {
            mood: EMOTIONAL_MOODS.includes(savedState.mood) ? savedState.mood : "neutral",
            energy: Number.isFinite(savedState.energy) ? Math.max(0, Math.min(100, savedState.energy)) : 68,
            trust: Number.isFinite(savedState.trust) ? Math.max(0, Math.min(100, savedState.trust)) : 50,
            closeness: Number.isFinite(savedState.closeness) ? Math.max(0, Math.min(100, savedState.closeness)) : 25,
            tension: Number.isFinite(savedState.tension) ? Math.max(0, Math.min(100, savedState.tension)) : 10,
            updatedAt: Number.isFinite(savedState.updatedAt) ? savedState.updatedAt : Date.now(),
          };
          setCharacterSettings({ tone: parsedSettings.tone, memory: parsedSettings.memory.slice(0, 1600), continuity: savedContinuity, emotionalState: safeState, replyRules: Array.isArray(parsedSettings.replyRules) ? parsedSettings.replyRules.filter((rule: ReplyRule) => rule && typeof rule.id === "string" && Array.isArray(rule.triggers) && Array.isArray(rule.replies)).slice(0, 300) : DEFAULT_RULES, fallbackReply: typeof parsedSettings.fallbackReply === "string" ? parsedSettings.fallbackReply.slice(0, 1000) : DEFAULT_FALLBACK });
        }
      }
      const savedConversations = localStorage.getItem(CONVERSATIONS_KEY);
      if (savedConversations) {
        const parsed = JSON.parse(savedConversations);
        if (Array.isArray(parsed) && parsed.length && parsed.every((item) => item && typeof item.id === "string" && Array.isArray(item.messages))) {
          const safe = parsed.slice(0, 50) as Conversation[];
          setConversations(safe);
          const savedActiveId = localStorage.getItem("mira-messenger-active-conversation-v1");
          setActiveConversationId(safe.some((item) => item.id === savedActiveId) ? savedActiveId! : safe[0].id);
        } else {
          const fallback: Conversation = { id: crypto.randomUUID(), title: "Our conversation", updatedAt: Date.now(), messages: starter };
          setConversations([fallback]); setActiveConversationId(fallback.id);
        }
      } else {
        const oldHistory = localStorage.getItem(STORAGE_KEY);
        let initialMessages = starter;
        if (oldHistory) {
          const parsed = JSON.parse(oldHistory);
          if (Array.isArray(parsed) && parsed.length) initialMessages = parsed;
        }
        const migrated: Conversation = { id: crypto.randomUUID(), title: "Our conversation", updatedAt: Date.now(), messages: initialMessages };
        setConversations([migrated]); setActiveConversationId(migrated.id);
      }
    } catch {
      const fallback: Conversation = { id: crypto.randomUUID(), title: "Our conversation", updatedAt: Date.now(), messages: starter };
      setConversations([fallback]); setActiveConversationId(fallback.id);
    } finally { setHydrated(true); }
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(characterSettings)); } catch { /* Storage may be unavailable. */ }
  }, [characterSettings, hydrated]);

  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations.slice(0, 50).map((conversation) => ({ ...conversation, messages: conversation.messages.slice(-200) }))));
      localStorage.setItem("mira-messenger-active-conversation-v1", activeConversationId);
      localStorage.removeItem(STORAGE_KEY);
    } catch { /* Storage may be unavailable. */ }
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [conversations, activeConversationId, busy, hydrated]);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = input.trim();
    if (!content || busy || !activeConversation) return;
    const conversationId = activeConversation.id;
    const nextEmotionalState = evolveEmotionalState(characterSettings.emotionalState, content);
    setCharacterSettings((current) => ({ ...current, emotionalState: nextEmotionalState }));
    const userMessage: Message = { id: crypto.randomUUID(), role: "user", content, time: Date.now(), memorySuggestion: suggestMemoryFromMessage(content) };
    const next = [...activeConversation.messages, userMessage].slice(-200);
    const title = activeConversation.messages.some((message) => message.role === "user") ? activeConversation.title : content.slice(0, 36) + (content.length > 36 ? "…" : "");
    setConversations((current) => current.map((conversation) => conversation.id === conversationId ? { ...conversation, title, updatedAt: Date.now(), messages: next } : conversation));
    setInput(""); setBusy(true); setNotice("");
    const reply = chooseLocalReply(content, characterSettings.replyRules, characterSettings.fallbackReply);
    window.setTimeout(() => {
      setConversations((current) => current.map((conversation) => conversation.id === conversationId ? { ...conversation, updatedAt: Date.now(), messages: [...conversation.messages, { id: crypto.randomUUID(), role: "assistant", content: reply, time: Date.now() }].slice(-200) } : conversation));
      setBusy(false); inputRef.current?.focus();
    }, 300);
  }

  function addReplyRule() {
    const triggers = ruleTriggerDraft.split(",").map((item) => item.trim()).filter(Boolean).slice(0, 20);
    const replies = ruleRepliesDraft.split("\n").map((item) => item.trim()).filter(Boolean).slice(0, 20);
    if (!triggers.length || !replies.length) { setNotice("Add at least one trigger and one reply."); return; }
    setCharacterSettings((current) => ({ ...current, replyRules: [...current.replyRules, { id: crypto.randomUUID(), triggers, replies, enabled: true }].slice(-300) }));
    setRuleTriggerDraft(""); setRuleRepliesDraft(""); setNotice("Added to Mira’s local Reply Library.");
  }

  function updateReplyRule(id: string, patch: Partial<ReplyRule>) {
    setCharacterSettings((current) => ({ ...current, replyRules: current.replyRules.map((rule) => rule.id === id ? { ...rule, ...patch } : rule) }));
  }

  function deleteReplyRule(id: string) {
    setCharacterSettings((current) => ({ ...current, replyRules: current.replyRules.filter((rule) => rule.id !== id) }));
  }
  function rememberMessage(message: Message) {
    const label = message.role === "user" ? "You" : "Mira";
    const entry = encodeMemoryEntry("General", `• ${label}: ${message.content.trim().replace(/\s+/g, " ")}`);
    const existing = characterSettings.memory.split("\n").map((line) => line.trim()).filter(Boolean);
    if (existing.includes(entry)) {
      setNotice("That message is already in Mira’s shared memory notes.");
      return;
    }
    const combined = [characterSettings.memory.trim(), entry].filter(Boolean).join("\n");
    if (combined.length > 1600) {
      setNotice("Mira’s memory notes are full. Open settings and edit or shorten them before saving this message.");
      return;
    }
    setCharacterSettings((current) => ({ ...current, memory: [current.memory.trim(), entry].filter(Boolean).join("\n") }));
    setNotice("Saved to Mira’s shared memory notes. You can review or edit it in character settings.");
  }

  function handleMemorySuggestion(conversationId: string, messageId: string, action: "save" | "dismiss") {
    const candidate = conversations.find((conversation) => conversation.id === conversationId)?.messages.find((message) => message.id === messageId)?.memorySuggestion;
    if (!candidate || candidate.status !== "pending") return;
    if (action === "save") {
      const entry = encodeMemoryEntry(candidate.category, candidate.text);
      const existing = characterSettings.memory.split("\n").map((line) => line.trim()).filter(Boolean);
      if (existing.includes(entry)) {
        setNotice("That detail is already in Mira’s memory.");
      } else {
        const combined = [characterSettings.memory.trim(), entry].filter(Boolean).join("\n");
        if (combined.length > 1600) {
          setNotice("Mira’s memory is full. Open settings and shorten or remove a note before saving this suggestion.");
          return;
        }
        setCharacterSettings((current) => ({ ...current, memory: [current.memory.trim(), entry].filter(Boolean).join("\n") }));
        setNotice("Saved the suggested detail to Mira’s memory.");
      }
    }
    setConversations((current) => current.map((conversation) => conversation.id !== conversationId ? conversation : {
      ...conversation,
      messages: conversation.messages.map((message) => message.id !== messageId || !message.memorySuggestion ? message : {
        ...message, memorySuggestion: { ...message.memorySuggestion, status: action === "save" ? "saved" : "dismissed" },
      }),
    }));
  }

  function addMemoryEntry() {
    const text = memoryDraft.trim().replace(/\s+/g, " ");
    if (!text) return;
    const entry = encodeMemoryEntry(memoryCategory, text);
    const existing = characterSettings.memory.split("\n").map((line) => line.trim()).filter(Boolean);
    if (existing.includes(entry)) {
      setNotice("That note is already in Mira’s memory.");
      return;
    }
    const combined = [characterSettings.memory.trim(), entry].filter(Boolean).join("\n");
    if (combined.length > 1600) {
      setNotice("Mira’s memory is full. Remove or shorten a note before adding another.");
      return;
    }
    setCharacterSettings((current) => ({ ...current, memory: [current.memory.trim(), entry].filter(Boolean).join("\n") }));
    setMemoryDraft("");
    setNotice("Added to Mira’s shared memory.");
  }

  function updateMemoryEntry(index: number, category: MemoryCategory, text: string) {
    const lines = characterSettings.memory.split("\n");
    lines[index] = encodeMemoryEntry(category, text);
    const combined = lines.filter((line) => line.trim()).join("\n");
    if (combined.length > 1600) {
      setNotice("That edit would exceed the memory limit. Shorten the note a little.");
      return;
    }
    setCharacterSettings((current) => ({ ...current, memory: combined }));
  }

  function deleteMemoryEntry(index: number) {
    const lines = characterSettings.memory.split("\n");
    lines.splice(index, 1);
    setCharacterSettings((current) => ({ ...current, memory: lines.filter((line) => line.trim()).join("\n") }));
    setNotice("Removed that note from Mira’s memory.");
  }

  function addContinuityEntry() {
    const text = continuityDraft.trim().replace(/\s+/g, " ");
    if (!text) return;
    if (text.length > 220) {
      setNotice("Keep each continuity note under 220 characters.");
      return;
    }
    const duplicate = characterSettings.continuity.some((entry) => entry.kind === continuityKind && entry.text.toLowerCase() === text.toLowerCase());
    if (duplicate) {
      setNotice("That continuity note is already saved.");
      return;
    }
    if (characterSettings.continuity.length >= 12) {
      setNotice("The continuity journal holds up to 12 notes. Remove an old note to make room.");
      return;
    }
    setCharacterSettings((current) => ({ ...current, continuity: [...current.continuity, { id: crypto.randomUUID(), kind: continuityKind, text, createdAt: Date.now() }] }));
    setContinuityDraft("");
    setNotice("Added to Mira’s continuity journal.");
  }

  function deleteContinuityEntry(id: string) {
    setCharacterSettings((current) => ({ ...current, continuity: current.continuity.filter((entry) => entry.id !== id) }));
    setNotice("Removed that continuity note.");
  }

  function createConversation() {
    if (busy) return;
    const now = Date.now();
    const conversation: Conversation = { id: crypto.randomUUID(), title: "New conversation", updatedAt: now, messages: [{ ...starter[0], id: crypto.randomUUID(), time: now }] };
    setConversations((current) => [conversation, ...current].slice(0, 50));
    setActiveConversationId(conversation.id); setNotice(""); setInput("");
    inputRef.current?.focus();
  }

  function clearChat() {
    if (!activeConversation || !window.confirm("Clear this conversation's messages? Other conversations will be kept.")) return;
    const now = Date.now();
    const freshMessages = [{ ...starter[0], id: crypto.randomUUID(), time: now }];
    setConversations((current) => current.map((conversation) => conversation.id === activeConversation.id
      ? { ...conversation, title: "New conversation", updatedAt: now, messages: freshMessages } : conversation));
    setNotice(""); setInput("");
  }

  function deleteConversation(conversationId: string) {
    if (busy || !window.confirm("Delete this conversation from this browser?")) return;
    const remaining = conversations.filter((conversation) => conversation.id !== conversationId);
    if (remaining.length === 0) {
      const now = Date.now();
      const fresh: Conversation = { id: crypto.randomUUID(), title: "New conversation", updatedAt: now, messages: [{ ...starter[0], id: crypto.randomUUID(), time: now }] };
      setConversations([fresh]); setActiveConversationId(fresh.id);
    } else {
      setConversations(remaining);
      if (activeConversationId === conversationId) setActiveConversationId(remaining[0].id);
    }
    setNotice("");
  }

  return (
    <main className="app-shell">
      <div className="ambient ambient-one" aria-hidden="true" />
      <div className="ambient ambient-two" aria-hidden="true" />
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Mira home"><span className="brand-mark"><span>m</span><i>✳</i></span><span className="brand-wordmark">mira<span className="brand-period">.</span><small>YOUR PRIVATE UNIVERSE</small></span></a>
        <button className="new-chat" onClick={createConversation} type="button" disabled={busy}><span className="plus-icon">＋</span> New conversation <kbd>⌘ K</kbd></button>
        <div className="side-label">YOUR SPACE</div>
        <div className="conversation-list" aria-label="Saved conversations">
          {conversations.map((conversation) => <div className={`conversation-item ${conversation.id === activeConversationId ? "active" : ""}`} key={conversation.id}>
            <button className={`chat-link ${conversation.id === activeConversationId ? "active" : ""}`} type="button" onClick={() => { if (!busy) { setActiveConversationId(conversation.id); setNotice(""); } }} disabled={busy} title={conversation.title}>
              <span className="tiny-avatar"><span>M</span><i /></span>
              <span className="chat-link-copy"><strong>{conversation.title || "New conversation"}</strong><small>{new Date(conversation.updatedAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })} · {conversation.messages.length} messages</small></span>
            </button>
            <button className="conversation-delete" type="button" aria-label={`Delete ${conversation.title}`} title="Delete conversation" disabled={busy} onClick={() => deleteConversation(conversation.id)}>×</button>
          </div>)}
        </div>
        <div className="sidebar-feature">
          <div className="feature-orbit"><span className="orbit-star">✳</span><span className="orbit-dot" /><span className="orbit-dot orbit-dot-two" /><div className="feature-avatar">M</div></div>
          <span className="feature-eyebrow">A LITTLE SPACE, JUST FOR YOU</span>
          <h2>Talk it out.<br /><em>Stay a while.</em></h2>
          <p>Thoughtful conversations, one message at a time.</p>
          <div className="feature-line"><span /> YOUR SPACE IS READY</div>
        </div>
        <div className="sidebar-bottom"><span className="secure-icon">✳</span><span><strong>Private by design</strong><small>Stored on this device</small></span><span className="status-dot" /></div>
      </aside>

      <section className="chat-panel">
        <header className="chat-header">
          <div className="profile-avatar" aria-hidden="true"><span>M</span><i /></div>
          <div className="profile-copy"><h1>Mira Bennett <span className="verified-mark">✦</span></h1><p><span className="online-dot" /> Your offline character <span className="header-dot">·</span> ready when you are</p></div>
          <div className="header-actions"><span className="encrypted-pill"><span /> PRIVATE CHAT</span><button className="icon-button new-chat-mobile" type="button" aria-label="New conversation" title="New conversation" onClick={createConversation} disabled={busy}>＋</button><button className="icon-button settings-trigger" type="button" aria-label="Open character settings" title="Character settings" onClick={() => setSettingsOpen(true)}><span>⚙</span></button><button className="icon-button" type="button" aria-label="Clear current conversation" title="Clear current conversation" onClick={clearChat}><span>↻</span></button></div>
        </header>

        <div className="messages-area">
          <div className="conversation-intro"><span className="intro-line" /><span>THE CONVERSATION</span><span className="intro-line" /></div>
          <div className="intro-note"><span className="note-icon">✳</span><span>A quiet corner on your device. Mira uses your written replies.</span><button type="button" aria-label="Dismiss note" onClick={(e) => e.currentTarget.parentElement?.remove()}>×</button></div>
          {messages.map((message, index) => (
            <div className={`message-row ${message.role === "user" ? "mine" : "theirs"}`} key={message.id} style={{ animationDelay: `${Math.min(index * 45, 240)}ms` }}>
              {message.role === "assistant" && <div className="message-avatar"><span>M</span></div>}
              <div className="message-content"><div className="bubble">{message.content}</div><div className="message-meta"><time>{timeLabel(message.time)}</time>{message.role === "user" && <span className="read-receipt" title="Sent">✓✓</span>}{message.id !== "welcome" && <button className="remember-message" type="button" onClick={() => rememberMessage(message)} title="Save this message to memory">✳ Remember</button>}{message.role === "user" && message.memorySuggestion?.status === "pending" && <div className="memory-suggestion"><div className="memory-suggestion-copy"><span className="memory-suggestion-icon">✳</span><span><strong>Worth remembering?</strong><small>Mira could keep this detail across chats. Nothing is saved unless you choose.</small></span></div><div className="memory-suggestion-actions"><button type="button" onClick={() => handleMemorySuggestion(activeConversation?.id || "", message.id, "save")}>Save detail</button><button type="button" className="memory-suggestion-dismiss" onClick={() => handleMemorySuggestion(activeConversation?.id || "", message.id, "dismiss")}>Not now</button></div></div>}{message.memorySuggestion && message.memorySuggestion.status !== "pending" && <small className="memory-suggestion-status">{message.memorySuggestion.status === "saved" ? "✳ Saved to Mira’s memory" : "Suggestion dismissed"}</small>}</div></div>
            </div>
          ))}
          {busy && <div className="message-row theirs typing-row"><div className="message-avatar"><span>M</span></div><div className="message-content"><div className="bubble typing"><span /><span /><span /></div><small className="typing-label">Mira is finding her words</small></div></div>}
          {notice && <div className="error-notice" role="alert"><span className="error-symbol">!</span><span>{notice}</span><button type="button" onClick={() => setNotice("")} aria-label="Dismiss error">×</button></div>}
          <div ref={endRef} />
        </div>

        <div className="composer-wrap">
          <form className="composer" onSubmit={sendMessage}>
            <button className="composer-plus" type="button" title="A little room for more" aria-label="Focus message input" onClick={() => inputRef.current?.focus()}>＋</button>
            <textarea ref={inputRef} value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); (e.currentTarget.form as HTMLFormElement | null)?.requestSubmit(); } }} placeholder="Write your next thought..." rows={1} maxLength={6000} aria-label="Message Mira" />
            <span className="composer-sparkle" aria-hidden="true">✳</span>
            <button className="send-button" type="submit" disabled={busy || !input.trim()} aria-label="Send message"><span>↑</span></button>
          </form>
          <div className="composer-foot"><span><kbd>↵</kbd> to send <span className="foot-separator">·</span> <kbd>⇧ ↵</kbd> new line</span><span className="powered-label"><span className="powered-dot" /> MADE FOR MEANINGFUL MOMENTS</span></div>
        </div>
      </section>
      {settingsOpen && <div className="settings-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSettingsOpen(false); }}>
        <section className="settings-drawer" role="dialog" aria-modal="true" aria-labelledby="settings-title">
          <div className="settings-heading"><div><span className="settings-eyebrow">MAKE THIS SPACE YOURS</span><h2 id="settings-title">A little personalization</h2><p>Fine-tune Mira’s conversational feel and keep a few useful details close.</p></div><button className="icon-button settings-close" type="button" aria-label="Close settings" onClick={() => setSettingsOpen(false)}>×</button></div>
          <div className="settings-section">
<div className="memory-library-heading"><div><span className="settings-label">LOCAL REPLY LIBRARY</span><p className="settings-help">Mira only uses dialogue you write. Triggers are matched on this phone without AI or internet access.</p></div><span className="memory-count">{characterSettings.replyRules.length} rules</span></div>
<div className="memory-add-panel"><label className="memory-field-label" htmlFor="reply-triggers">TRIGGERS (COMMA SEPARATED)</label><input id="reply-triggers" className="memory-input" value={ruleTriggerDraft} maxLength={300} onChange={(event) => setRuleTriggerDraft(event.target.value)} placeholder="hello, hey, hi" /><label className="memory-field-label" htmlFor="reply-variations">MIRA’S REPLIES (ONE PER LINE)</label><textarea id="reply-variations" className="memory-input memory-draft" value={ruleRepliesDraft} maxLength={2000} onChange={(event) => setRuleRepliesDraft(event.target.value)} placeholder={"Hey, there you are 😊\nHi you. How’s your day?"} rows={4} /><button className="memory-add-button" type="button" onClick={addReplyRule} disabled={!ruleTriggerDraft.trim() || !ruleRepliesDraft.trim()}>＋ Add reply rule</button></div>
<div className="memory-entry-list">{characterSettings.replyRules.map((rule) => <article className="memory-entry" key={rule.id}><div className="memory-entry-top"><strong>{rule.triggers.join(", ")}</strong><button className="memory-delete-button" type="button" onClick={() => deleteReplyRule(rule.id)} aria-label="Delete reply rule">×</button></div><label className="memory-field-label"><input type="checkbox" checked={rule.enabled} onChange={(event) => updateReplyRule(rule.id, { enabled: event.target.checked })} /> Enabled</label><input className="memory-input" value={rule.triggers.join(", ")} onChange={(event) => updateReplyRule(rule.id, { triggers: event.target.value.split(",").map((item) => item.trim()).filter(Boolean).slice(0,20) })} aria-label="Edit trigger phrases" /><textarea className="memory-entry-text" rows={Math.min(6, Math.max(2, rule.replies.length))} value={rule.replies.join("\n")} onChange={(event) => updateReplyRule(rule.id, { replies: event.target.value.split("\n").slice(0,20) })} aria-label={"Replies for " + rule.triggers.join(", ")} /></article>)}</div>
<div className="memory-add-panel"><label className="memory-field-label" htmlFor="fallback-reply">DEFAULT REPLY WHEN NOTHING MATCHES</label><textarea id="fallback-reply" className="memory-input memory-draft" rows={3} maxLength={1000} value={characterSettings.fallbackReply} onChange={(event) => setCharacterSettings((current) => ({ ...current, fallbackReply: event.target.value }))} /></div>
</div>
<div className="settings-section"><span className="settings-label">CONVERSATION VIBE</span><div className="tone-options">
            {([{ id: "balanced", title: "Balanced", detail: "Natural and nuanced" }, { id: "playful", title: "Playful", detail: "More wit and teasing" }, { id: "gentle", title: "Gentle", detail: "Softer and reassuring" }, { id: "direct", title: "Direct", detail: "Clear and candid" }] as const).map((tone) => <button key={tone.id} type="button" className={characterSettings.tone === tone.id ? "tone-option selected" : "tone-option"} aria-pressed={characterSettings.tone === tone.id} onClick={() => setCharacterSettings((current) => ({ ...current, tone: tone.id }))}><span className="tone-check">{characterSettings.tone === tone.id ? "✓" : ""}</span><strong>{tone.title}</strong><small>{tone.detail}</small></button>)}
          </div></div>
          <div className="settings-section emotional-state-section"><div className="memory-library-heading"><div><span className="settings-label">MIRA’S EMOTIONAL STATE</span><p className="settings-help">A lightweight continuity model, not a claim that feelings can be measured exactly. Values change gently from text cues and can be edited or reset by you.</p></div><span className="memory-count">{characterSettings.emotionalState.mood}</span></div>
            <div className="emotion-mood-row"><label className="memory-field-label" htmlFor="emotion-mood">CURRENT TONE</label><select id="emotion-mood" className="memory-category-select" value={characterSettings.emotionalState.mood} onChange={(event) => setCharacterSettings((current) => ({ ...current, emotionalState: { ...current.emotionalState, mood: event.target.value as EmotionalMood, updatedAt: Date.now() } }))}>{EMOTIONAL_MOODS.map((mood) => <option key={mood} value={mood}>{mood.charAt(0).toUpperCase() + mood.slice(1)}</option>)}</select></div>
            {([{ key: "energy", label: "Energy", hint: "How lively or worn down she feels" }, { key: "trust", label: "Trust", hint: "Earned through consistent interaction" }, { key: "closeness", label: "Closeness", hint: "Familiarity, not automatic romance" }, { key: "tension", label: "Lingering tension", hint: "Unresolved friction that may ease over time" }] as const).map((item) => <div className="emotion-meter" key={item.key}><div className="emotion-meter-heading"><label htmlFor={"emotion-" + item.key}>{item.label}</label><output>{characterSettings.emotionalState[item.key]}/100</output></div><input id={"emotion-" + item.key} type="range" min="0" max="100" value={characterSettings.emotionalState[item.key]} onChange={(event) => setCharacterSettings((current) => ({ ...current, emotionalState: { ...current.emotionalState, [item.key]: Number(event.target.value), updatedAt: Date.now() } }))} /><small>{item.hint}</small></div>)}
            <div className="emotion-state-footer"><span>Saved locally on this device</span><button type="button" className="memory-suggestion-dismiss" onClick={() => setCharacterSettings((current) => ({ ...current, emotionalState: { ...DEFAULT_EMOTIONAL_STATE, updatedAt: Date.now() } }))}>Reset state</button></div></div>
          <div className="settings-section"><div className="memory-library-heading"><div><span className="settings-label">MIRA’S MEMORY LIBRARY</span><p className="settings-help">Organize the details that should carry between chats. You can edit, recategorize, or remove any note.</p></div><span className="memory-count">{characterSettings.memory.length}/1600</span></div>
            <div className="memory-add-panel"><label className="memory-field-label" htmlFor="memory-category">CATEGORY</label><select id="memory-category" className="memory-category-select" value={memoryCategory} onChange={(event) => setMemoryCategory(event.target.value as MemoryCategory)}>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select><label className="memory-field-label" htmlFor="memory-draft">NEW NOTE</label><textarea id="memory-draft" className="memory-input memory-draft" value={memoryDraft} maxLength={500} onChange={(event) => setMemoryDraft(event.target.value)} placeholder="A preference, ongoing story detail, important person, or milestone..." rows={2} /><button className="memory-add-button" type="button" onClick={addMemoryEntry} disabled={!memoryDraft.trim() || characterSettings.memory.length >= 1600}>＋ Add to memory</button></div>
            <div className="memory-filter-row"><label className="memory-field-label" htmlFor="memory-filter">SAVED NOTES</label><select id="memory-filter" className="memory-category-select memory-filter-select" value={memoryFilter} onChange={(event) => setMemoryFilter(event.target.value)}><option value="All">All categories</option>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select></div>
            <div className="memory-entry-list">{parseMemoryEntries(characterSettings.memory).filter((entry) => memoryFilter === "All" || entry.category === memoryFilter).map((entry) => <article className="memory-entry" key={entry.index}><div className="memory-entry-top"><span className={`memory-category-tag category-${entry.category.toLowerCase()}`}>{entry.category}</span><button className="memory-delete-button" type="button" onClick={() => deleteMemoryEntry(entry.index)} aria-label={`Remove memory note: ${entry.text.slice(0, 48)}`} title="Remove note">×</button></div><select className="memory-category-select entry-category-select" aria-label={`Category for memory note: ${entry.text.slice(0, 48)}`} value={entry.category} onChange={(event) => updateMemoryEntry(entry.index, event.target.value as MemoryCategory, entry.text)}>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select><textarea className="memory-entry-text" aria-label={`Edit memory note: ${entry.text.slice(0, 48)}`} value={entry.text} rows={2} maxLength={500} onChange={(event) => updateMemoryEntry(entry.index, entry.category, event.target.value)} /></article>)}
              {parseMemoryEntries(characterSettings.memory).filter((entry) => memoryFilter === "All" || entry.category === memoryFilter).length === 0 && <div className="memory-empty"><span>✳</span><strong>No notes here yet</strong><small>Add one above, or save a chat message with Remember.</small></div>}</div>
            <div className="memory-footer"><span>Shared across chats · saved in this browser</span><span>{parseMemoryEntries(characterSettings.memory).length} notes</span></div></div>
          <div className="settings-section"><div className="memory-library-heading"><div><span className="settings-label">EMOTIONAL CONTINUITY JOURNAL</span><p className="settings-help">Keep the thread between conversations: feelings that matter, unfinished conversations, promises, and meaningful moments. You decide what belongs here.</p></div><span className="memory-count">{characterSettings.continuity.length}/12</span></div>
            <div className="continuity-add-panel"><label className="memory-field-label" htmlFor="continuity-kind">MOMENT TYPE</label><select id="continuity-kind" className="memory-category-select" value={continuityKind} onChange={(event) => setContinuityKind(event.target.value as ContinuityKind)}>{CONTINUITY_KINDS.map((kind) => <option key={kind} value={kind}>{kind}</option>)}</select><label className="memory-field-label" htmlFor="continuity-draft">WHAT SHOULD CARRY FORWARD?</label><textarea id="continuity-draft" className="memory-input continuity-draft" value={continuityDraft} maxLength={220} onChange={(event) => setContinuityDraft(event.target.value)} placeholder="Example: We left off after a difficult conversation about trust. Mira apologized, but we have not fully resolved it." rows={3} /><div className="continuity-footer"><span>{continuityDraft.length}/220 characters</span><button className="memory-add-button" type="button" onClick={addContinuityEntry} disabled={!continuityDraft.trim() || characterSettings.continuity.length >= 12}>＋ Keep this thread</button></div></div>
            <div className="continuity-entry-list">{characterSettings.continuity.slice().reverse().map((entry) => <article className="continuity-entry" key={entry.id}><div className="memory-entry-top"><span className={"continuity-kind-tag continuity-" + entry.kind.toLowerCase().replace(/\s+/g, "-")}>{entry.kind}</span><button className="memory-delete-button" type="button" onClick={() => deleteContinuityEntry(entry.id)} aria-label={"Remove continuity note: " + entry.text.slice(0, 48)} title="Remove note">×</button></div><p>{entry.text}</p><small>{new Date(entry.createdAt).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}</small></article>)}
              {characterSettings.continuity.length === 0 && <div className="memory-empty"><span>♡</span><strong>The story is still unfolding</strong><small>Add a meaningful moment, an unfinished thread, or a promise you want Mira to keep in mind.</small></div>}</div>
            <div className="memory-footer"><span>Shared across chats · saved in this browser</span><span>{characterSettings.continuity.length} journal notes</span></div></div>
          <div className="settings-privacy"><span>✳</span><p>All character settings, replies, and conversations stay in this browser’s local storage. They are not synced or encrypted.</p></div>
          <button className="settings-done" type="button" onClick={() => setSettingsOpen(false)}>Save preferences <span>↗</span></button>
        </section>
      </div>}
    </main>
  );
}
