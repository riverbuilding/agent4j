import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "agent4j — Java-native agent harness",
  description:
    "A Java-native coding agent harness for streaming model calls, durable sessions, workspace tools, and inspectable agent loops.",
  openGraph: {
    title: "agent4j — Java-native agent harness",
    description:
      "Build coding agents with a runtime you can inspect: streams, sessions, tools, and queues composed as ordinary Java.",
    images: ["/og.png"],
  },
  twitter: {
    card: "summary_large_image",
    title: "agent4j — Java-native agent harness",
    description:
      "A Java-native coding agent harness for streaming model calls, durable sessions, workspace tools, and inspectable agent loops.",
    images: ["/og.png"],
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
