"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

type Message = { id: string; role: "user" | "assistant"; content: string; time: number };
type Conversation = { id: string; title: string; updatedAt: number; messages: Message[] };
const STORAGE_KEY = "mira-messenger-history-v1";
const CONVERSATIONS_KEY = "mira-messenger-conversations-v1";
const SETTINGS_KEY = "mira-messenger-settings-v1";
type CharacterTone = "balanced" | "playful" | "gentle" | "direct";
type CharacterSettings = { tone: CharacterTone; memory: string };
const DEFAULT_SETTINGS: CharacterSettings = { tone: "balanced", memory: "" };
const starter: Message[] = [{ id: "welcome", role: "assistant", content: "Hey, you made it. 😊 I’m Mira. We can take this one conversation at a time. What’s on your mind?", time: Date.now() }];

const MEMORY_CATEGORIES = ["Preferences", "Stories", "People", "Milestones", "General"] as const;
type MemoryCategory = (typeof MEMORY_CATEGORIES)[number];
type MemoryEntry = { category: MemoryCategory; text: string; index: number };

function parseMemoryEntries(memory: string): MemoryEntry[] {
  return memory.split("\n").map((line, index) => {
    const match = line.match(/^\[(Preferences|Stories|People|Milestones|General)\]\s*(.*)$/i);
    const category = match ? MEMORY_CATEGORIES.find((item) => item.toLowerCase() === match[1].toLowerCase()) || "General" : "General";
    return { category, text: (match ? match[2] : line).trim(), index };
  }).filter((entry) => entry.text.length > 0);
}

function encodeMemoryEntry(category: MemoryCategory, text: string) {
  return `[${category}] ${text.trim().replace(/\s+/g, " ")}`;
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
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    try {
      const savedSettings = localStorage.getItem(SETTINGS_KEY);
      if (savedSettings) {
        const parsedSettings = JSON.parse(savedSettings);
        if (parsedSettings && ["balanced", "playful", "gentle", "direct"].includes(parsedSettings.tone) && typeof parsedSettings.memory === "string") {
          setCharacterSettings({ tone: parsedSettings.tone, memory: parsedSettings.memory.slice(0, 1600) });
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
    const userMessage: Message = { id: crypto.randomUUID(), role: "user", content, time: Date.now() };
    const next = [...activeConversation.messages, userMessage].slice(-200);
    const title = activeConversation.messages.some((message) => message.role === "user")
      ? activeConversation.title
      : content.slice(0, 36) + (content.length > 36 ? "…" : "");
    setConversations((current) => current.map((conversation) => conversation.id === conversationId
      ? { ...conversation, title, updatedAt: Date.now(), messages: next } : conversation));
    setInput(""); setBusy(true); setNotice("");
    try {
      const response = await fetch("/api/chat", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: next.filter((m) => m.id !== "welcome").map(({ role, content }) => ({ role, content })).slice(-30), characterSettings }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.error || "Something went wrong.");
      setConversations((current) => current.map((conversation) => conversation.id === conversationId
        ? { ...conversation, updatedAt: Date.now(), messages: [...conversation.messages, { id: crypto.randomUUID(), role: "assistant", content: data.reply, time: Date.now() }].slice(-200) }
        : conversation));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Couldn't send that message.");
    } finally { setBusy(false); inputRef.current?.focus(); }
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
    lines[index] = text.trim() ? encodeMemoryEntry(category, text) : "";
    setCharacterSettings((current) => ({ ...current, memory: lines.filter((line) => line.trim()).join("\n").slice(0, 1600) }));
  }

  function deleteMemoryEntry(index: number) {
    const lines = characterSettings.memory.split("\n");
    lines.splice(index, 1);
    setCharacterSettings((current) => ({ ...current, memory: lines.filter((line) => line.trim()).join("\n") }));
    setNotice("Removed that note from Mira’s memory.");
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
        <div className="sidebar-bottom"><span className="secure-icon">✳</span><span><strong>Private by design</strong><small>Your chat stays in this browser</small></span><span className="status-dot" /></div>
      </aside>

      <section className="chat-panel">
        <header className="chat-header">
          <div className="profile-avatar" aria-hidden="true"><span>M</span><i /></div>
          <div className="profile-copy"><h1>Mira Bennett <span className="verified-mark">✦</span></h1><p><span className="online-dot" /> Your AI character <span className="header-dot">·</span> ready when you are</p></div>
          <div className="header-actions"><span className="encrypted-pill"><span /> PRIVATE CHAT</span><button className="icon-button new-chat-mobile" type="button" aria-label="New conversation" title="New conversation" onClick={createConversation} disabled={busy}>＋</button><button className="icon-button settings-trigger" type="button" aria-label="Open character settings" title="Character settings" onClick={() => setSettingsOpen(true)}><span>⚙</span></button><button className="icon-button" type="button" aria-label="Clear current conversation" title="Clear current conversation" onClick={clearChat}><span>↻</span></button></div>
        </header>

        <div className="messages-area">
          <div className="conversation-intro"><span className="intro-line" /><span>THE CONVERSATION</span><span className="intro-line" /></div>
          <div className="intro-note"><span className="note-icon">✳</span><span>A quiet corner of the internet, just for you. Mira is a fictional AI character.</span><button type="button" aria-label="Dismiss note" onClick={(e) => e.currentTarget.parentElement?.remove()}>×</button></div>
          {messages.map((message, index) => (
            <div className={`message-row ${message.role === "user" ? "mine" : "theirs"}`} key={message.id} style={{ animationDelay: `${Math.min(index * 45, 240)}ms` }}>
              {message.role === "assistant" && <div className="message-avatar"><span>M</span></div>}
              <div className="message-content"><div className="bubble">{message.content}</div><div className="message-meta"><time>{timeLabel(message.time)}</time>{message.role === "user" && <span className="read-receipt" title="Sent">✓✓</span>}{message.id !== "welcome" && <button className="remember-message" type="button" onClick={() => rememberMessage(message)} title="Save this message to memory">✳ Remember</button>}</div></div>
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
          <div className="settings-section"><span className="settings-label">CONVERSATION VIBE</span><div className="tone-options">
            {([{ id: "balanced", title: "Balanced", detail: "Natural and nuanced" }, { id: "playful", title: "Playful", detail: "More wit and teasing" }, { id: "gentle", title: "Gentle", detail: "Softer and reassuring" }, { id: "direct", title: "Direct", detail: "Clear and candid" }] as const).map((tone) => <button key={tone.id} type="button" className={characterSettings.tone === tone.id ? "tone-option selected" : "tone-option"} aria-pressed={characterSettings.tone === tone.id} onClick={() => setCharacterSettings((current) => ({ ...current, tone: tone.id }))}><span className="tone-check">{characterSettings.tone === tone.id ? "✓" : ""}</span><strong>{tone.title}</strong><small>{tone.detail}</small></button>)}
          </div></div>
          <div className="settings-section"><div className="memory-library-heading"><div><span className="settings-label">MIRA’S MEMORY LIBRARY</span><p className="settings-help">Organize the details that should carry between chats. You can edit, recategorize, or remove any note.</p></div><span className="memory-count">{characterSettings.memory.length}/1600</span></div>
            <div className="memory-add-panel"><label className="memory-field-label" htmlFor="memory-category">CATEGORY</label><select id="memory-category" className="memory-category-select" value={memoryCategory} onChange={(event) => setMemoryCategory(event.target.value as MemoryCategory)}>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select><label className="memory-field-label" htmlFor="memory-draft">NEW NOTE</label><textarea id="memory-draft" className="memory-input memory-draft" value={memoryDraft} maxLength={500} onChange={(event) => setMemoryDraft(event.target.value)} placeholder="A preference, ongoing story detail, important person, or milestone..." rows={2} /><button className="memory-add-button" type="button" onClick={addMemoryEntry} disabled={!memoryDraft.trim() || characterSettings.memory.length >= 1600}>＋ Add to memory</button></div>
            <div className="memory-filter-row"><label className="memory-field-label" htmlFor="memory-filter">SAVED NOTES</label><select id="memory-filter" className="memory-category-select memory-filter-select" value={memoryFilter} onChange={(event) => setMemoryFilter(event.target.value)}><option value="All">All categories</option>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select></div>
            <div className="memory-entry-list">{parseMemoryEntries(characterSettings.memory).filter((entry) => memoryFilter === "All" || entry.category === memoryFilter).map((entry) => <article className="memory-entry" key={entry.index}><div className="memory-entry-top"><span className={`memory-category-tag category-${entry.category.toLowerCase()}`}>{entry.category}</span><button className="memory-delete-button" type="button" onClick={() => deleteMemoryEntry(entry.index)} aria-label={`Remove memory note: ${entry.text.slice(0, 48)}`} title="Remove note">×</button></div><select className="memory-category-select entry-category-select" aria-label={`Category for memory note: ${entry.text.slice(0, 48)}`} value={entry.category} onChange={(event) => updateMemoryEntry(entry.index, event.target.value as MemoryCategory, entry.text)}>{MEMORY_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select><textarea className="memory-entry-text" aria-label={`Edit memory note: ${entry.text.slice(0, 48)}`} value={entry.text} rows={2} maxLength={500} onChange={(event) => updateMemoryEntry(entry.index, entry.category, event.target.value)} /></article>)}
              {parseMemoryEntries(characterSettings.memory).filter((entry) => memoryFilter === "All" || entry.category === memoryFilter).length === 0 && <div className="memory-empty"><span>✳</span><strong>No notes here yet</strong><small>Add one above, or save a chat message with Remember.</small></div>}</div>
            <div className="memory-footer"><span>Shared across chats · saved in this browser</span><span>{parseMemoryEntries(characterSettings.memory).length} notes</span></div></div>
          <div className="settings-privacy"><span>✳</span><p>These notes are stored in your browser and sent with chat requests so the AI can use them. They are not a secure vault.</p></div>
          <button className="settings-done" type="button" onClick={() => setSettingsOpen(false)}>Save preferences <span>↗</span></button>
        </section>
      </div>}
    </main>
  );
}
