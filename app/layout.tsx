import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Mira | Private AI Messenger",
  description: "A personal AI character messenger experiment.",
  applicationName: "Mira Messenger",
};

export const viewport: Viewport = { themeColor: "#f6f5f2", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
