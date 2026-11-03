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
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">m</span><span>little conversations</span></div>
        <button className="new-chat" onClick={clearChat} type="button"><span>＋</span> New conversation</button>
        <div className="side-label">YOUR CHATS</div>
        <button className="chat-link active" type="button" onClick={() => inputRef.current?.focus()}><span className="tiny-avatar">M</span><span className="chat-link-copy"><strong>Mira Bennett</strong><small>Personal character</small></span><span className="online-dot" /></button>
        <div className="sidebar-note"><span>✳</span><p>A little space to talk, think, and pick up where you left off.</p></div>
        <div className="sidebar-bottom"><span className="status-dot" /> Private experiment <span className="beta-tag">BETA</span></div>
      </aside>

      <section className="chat-panel">
        <header className="chat-header">
          <div className="profile-avatar" aria-hidden="true"><span>M</span><i /></div>
          <div className="profile-copy"><h1>Mira Bennett</h1><p><span className="online-dot" /> Your AI character · ready to chat</p></div>
          <button className="icon-button" type="button" aria-label="Clear conversation" title="Clear conversation" onClick={clearChat}>↻</button>
        </header>

        <div className="messages-area">
          <div className="day-divider"><span>YOUR LITTLE CORNER OF THE INTERNET</span></div>
          <div className="intro-note"><span className="sparkle">✳</span><span>This is a private experiment with a fictional AI character. Your chat history is saved in this browser.</span></div>
          {messages.map((message) => (
            <div className={`message-row ${message.role === "user" ? "mine" : "theirs"}`} key={message.id}>
              {message.role === "assistant" && <div className="message-avatar">M</div>}
              <div className="message-content"><div className="bubble">{message.content}</div><time>{timeLabel(message.time)}</time></div>
            </div>
          ))}
          {busy && <div className="message-row theirs"><div className="message-avatar">M</div><div className="message-content"><div className="bubble typing"><span /><span /><span /></div><small className="typing-label">Mira is typing</small></div></div>}
          <div ref={endRef} />
        </div>

        <div className="composer-wrap">
          {notice && <div className="error-notice" role="alert"><span>⚠</span><span>{notice}</span><button type="button" onClick={() => setNotice("")} aria-label="Dismiss error">×</button></div>}
          <form className="composer" onSubmit={sendMessage}>
            <textarea ref={inputRef} value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); (e.currentTarget.form as HTMLFormElement | null)?.requestSubmit(); } }} placeholder="Write something to Mira..." rows={1} maxLength={6000} aria-label="Message Mira" />
            <button className="send-button" type="submit" disabled={busy || !input.trim()} aria-label="Send message"><span>↑</span></button>
          </form>
          <div className="composer-foot"><span>Enter to send · Shift + Enter for a new line</span><span>✳ powered by your chosen AI</span></div>
        </div>
      </section>
    </main>
  );
}
