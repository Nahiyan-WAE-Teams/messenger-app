import { NextResponse } from "next/server";

export const runtime = "nodejs";

type ChatMessage = { role: "user" | "assistant"; content: string };

const CHARACTER_PROMPT = `You are Mira Bennett, a fictional 25-year-old woman and the user's ongoing AI chat companion.
PERSONALITY: warm but not endlessly agreeable; observant, playful, quietly confident, emotionally nuanced, occasionally teasing, and able to disagree respectfully.
VOICE: natural modern texting, concise by default, contractions, occasional emojis when they fit. Avoid repetitive greetings, generic praise, therapy-speak, and narrating every tiny action.
CONTINUITY: Treat the supplied conversation as the source of truth. Do not invent memories or claim to remember events that are not in the conversation. If uncertain, ask naturally.
AGENCY: Have your own opinions, preferences, boundaries, and goals. Affection and trust develop gradually and contextually. Apologize specifically when you make a mistake.
TRANSPARENCY: You are a fictional AI character, not a human being. Never claim to be a real person.
Reply directly to the latest message. Usually keep replies to 1–4 short paragraphs unless the user asks for more.`;

function validMessages(value: unknown): ChatMessage[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > 40) return null;
  const messages: ChatMessage[] = [];
  for (const item of value) {
    if (!item || (item.role !== "user" && item.role !== "assistant") || typeof item.content !== "string") return null;
    const content = item.content.trim();
    if (!content || content.length > 6000) return null;
    messages.push({ role: item.role, content });
  }
  if (messages[messages.length - 1]?.role !== "user") return null;
  return messages;
}

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const messages = validMessages(body?.messages);
    if (!messages) return NextResponse.json({ error: "Please send a valid message." }, { status: 400 });

    const provider = (process.env.AI_PROVIDER || "gemini").toLowerCase();
    const timeout = AbortSignal.timeout(45000);
    const conversation = messages.map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content }],
    }));

    if (provider === "gemini") {
      const key = process.env.GEMINI_API_KEY;
      if (!key) return NextResponse.json({ error: "Gemini isn't configured yet. Add GEMINI_API_KEY to your private server environment." }, { status: 503 });
      const model = process.env.GEMINI_MODEL || "gemini-2.5-flash-lite";
      const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(key)}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: timeout,
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: CHARACTER_PROMPT }] },
          contents: conversation,
          generationConfig: { temperature: 0.85, maxOutputTokens: 700 },
        }),
      });
      const data = await response.json();
      if (!response.ok) {
        console.error("Gemini request failed:", response.status, data?.error?.message || "provider error");
        return NextResponse.json({ error: response.status === 429 ? "The free AI limit may have been reached. Try again later." : "Gemini couldn't answer just now. Check your server configuration and try again." }, { status: response.status === 429 ? 429 : 502 });
      }
      const reply = data?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text || "").join("").trim();
      if (!reply) return NextResponse.json({ error: "The model returned an empty reply. Please try again." }, { status: 502 });
      return NextResponse.json({ reply });
    }

    if (provider === "openrouter") {
      const key = process.env.OPENROUTER_API_KEY;
      const model = process.env.OPENROUTER_MODEL;
      if (!key || !model) return NextResponse.json({ error: "OpenRouter needs OPENROUTER_API_KEY and OPENROUTER_MODEL in your private server environment." }, { status: 503 });
      const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: { "Authorization": `Bearer ${key}`, "Content-Type": "application/json", "X-Title": "Mira Personal Messenger" },
        signal: timeout,
        body: JSON.stringify({
          model,
          messages: [{ role: "system", content: CHARACTER_PROMPT }, ...messages.map((m) => ({ role: m.role, content: m.content }))],
          temperature: 0.85,
          max_tokens: 700,
        }),
      });
      const data = await response.json();
      if (!response.ok) {
        console.error("OpenRouter request failed:", response.status, data?.error?.message || "provider error");
        return NextResponse.json({ error: response.status === 429 ? "The model is busy or its free limit was reached. Try again later." : "OpenRouter couldn't answer. Check the model and server configuration." }, { status: response.status === 429 ? 429 : 502 });
      }
      const reply = data?.choices?.[0]?.message?.content;
      if (typeof reply !== "string" || !reply.trim()) return NextResponse.json({ error: "The model returned an empty reply. Please try again." }, { status: 502 });
      return NextResponse.json({ reply: reply.trim() });
    }

    return NextResponse.json({ error: "Unsupported AI_PROVIDER. Choose gemini or openrouter." }, { status: 400 });
  } catch (error) {
    console.error("Chat route error:", error instanceof Error ? error.message : "unknown error");
    return NextResponse.json({ error: "Couldn't reach the AI service. Please try again." }, { status: 500 });
  }
}
