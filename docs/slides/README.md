# Presenter deck

A short, self-contained slide deck for the workshop presenter.

Open `presenter-deck.html` in any browser. No build step, no internet, no dependencies.

## Controls

- Right arrow, space, or click: next slide
- Left arrow: previous slide
- Home / End: first / last slide
- `N`: toggle presenter notes (shown at the bottom of each slide)

## Contents

20 slides with a high-contrast neon aesthetic and inline SVG diagrams.

A background section first explains how modern agents are wired: the agent loop, RAG, MCP, and why agent security differs from app security, plus a dedicated slide on the OWASP Top 10 for LLM Applications (2025) highlighting the risks the workshop covers (LLM01, LLM02, LLM06, LLM07).

The exploit-then-defend section then covers the two-app architecture, the threat model (alice the attendee, bob the organizer), the attack loop, and the four modules: indirect prompt injection (LLM01), broken authorization via MCP token propagation (BOLA), excessive agency (LLM06), and sensitive information disclosure (LLM02 / LLM07). It ends on a defense-in-depth summary and the linchpin idea: carry the user's identity to where the decision is made.
