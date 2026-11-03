"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

type Message = { id: string; role: "user" | "assistant"; content: string; time: number };
const STORAGE_KEY = "mira-messenger-history-v1";
const starter: Message[] = [{ id: "welcome", role: "assistant", content: "Hey, you made it. 😊 I’m Mira. We can take this one conversation at a time. What’s on your mind?", time: Date.now() }];

function timeLabel(timestamp: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(timestamp);
}

export default function Home() {
  const [messages, setMessages] = useState<Message[]>(starter);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length) setMessages(parsed);
      }
    } catch { /* Ignore invalid local history. */ }
  }, []);

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-200))); } catch { /* Storage may be unavailable. */ }
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, busy]);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = input.trim();
    if (!content || busy) return;
    const userMessage: Message = { id: crypto.randomUUID(), role: "user", content, time: Date.now() };
    const next = [...messages, userMessage].slice(-40);
    setMessages(next);
    setInput("");
    setBusy(true);
    setNotice("");
    try {
      const response = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: next.filter((m) => m.id !== "welcome").map(({ role, content }) => ({ role, content })).slice(-30) }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.error || "Something went wrong.");
      setMessages((current) => [...current, { id: crypto.randomUUID(), role: "assistant", content: data.reply, time: Date.now() }].slice(-200));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Couldn't send that message.");
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  }

  function clearChat() {
    if (!window.confirm("Clear this conversation from this browser?")) return;
    setMessages([{ ...starter[0], id: crypto.randomUUID(), time: Date.now() }]);
    setNotice("");
    localStorage.removeItem(STORAGE_KEY);
  }

  return (
    <main className="app-shell">
      <div className="ambient ambient-one" aria-hidden="true" />
      <div className="ambient ambient-two" aria-hidden="true" />
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Mira home"><span className="brand-mark"><span>m</span><i>✳</i></span><span className="brand-wordmark">mira<span className="brand-period">.</span><small>YOUR PRIVATE UNIVERSE</small></span></a>
        <button className="new-chat" onClick={clearChat} type="button"><span className="plus-icon">＋</span> New conversation <kbd>⌘ K</kbd></button>
        <div className="side-label">YOUR SPACE</div>
        <button className="chat-link active" type="button" onClick={() => inputRef.current?.focus()}>
          <span className="tiny-avatar"><span>M</span><i /></span>
          <span className="chat-link-copy"><strong>Mira Bennett</strong><small>Where we left off</small></span>
          <span className="chat-link-more">···</span>
        </button>
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
          <div className="header-actions"><span className="encrypted-pill"><span /> PRIVATE CHAT</span><button className="icon-button" type="button" aria-label="Clear conversation" title="Clear conversation" onClick={clearChat}><span>↻</span></button></div>
        </header>

        <div className="messages-area">
          <div className="conversation-intro"><span className="intro-line" /><span>THE CONVERSATION</span><span className="intro-line" /></div>
          <div className="intro-note"><span className="note-icon">✳</span><span>A quiet corner of the internet, just for you. Mira is a fictional AI character.</span><button type="button" aria-label="Dismiss note" onClick={(e) => e.currentTarget.parentElement?.remove()}>×</button></div>
          {messages.map((message, index) => (
            <div className={`message-row ${message.role === "user" ? "mine" : "theirs"}`} key={message.id} style={{ animationDelay: `${Math.min(index * 45, 240)}ms` }}>
              {message.role === "assistant" && <div className="message-avatar"><span>M</span></div>}
              <div className="message-content"><div className="bubble">{message.content}</div><div className="message-meta"><time>{timeLabel(message.time)}</time>{message.role === "user" && <span className="read-receipt" title="Sent">✓✓</span>}</div></div>
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
    </main>
  );
}
