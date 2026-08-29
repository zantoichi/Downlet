# Product

## Register

product

## Users

Downlet serves someone who wants to download audio or video from a YouTube URL to their local Windows computer without using `yt-dlp` directly. Their expectation is a short, trustworthy desktop workflow: paste, confirm, choose the few meaningful options, and understand progress.

## Product Purpose

Downlet is a deliberately small, Windows-first native desktop frontend for `yt-dlp`. Its primary job is to turn a pasted YouTube URL into a clear local audio or video download decision without exposing backend machinery.

Success means the user can identify the media, choose video or audio and an understandable quality, confirm the destination, start the download, and recognize progress or recovery states without documentation. During the design-only phase, every state and transition is deterministic and fake so the interface can be reviewed independently of backend reliability.

## Brand Personality

Precise, calm, trustworthy.

Downlet should feel competent without feeling technical, restrained without feeling unfinished, and native without copying an IDE. Copy is direct and ordinary. The interface stays visually quiet until useful information becomes available.

## Anti-references

- An IntelliJ clone with tool-window chrome, sidebars, tabs, consoles, or configuration density.
- A generic SaaS or AI-generated dashboard with giant headings, hero layouts, repeated cards, or ornamental metrics.
- Oversized cards, nested cards, excessive whitespace, giant corner radii, and pill-shaped controls everywhere.
- Glassmorphism, gratuitous gradients, neon accents, decorative effects, or color used without meaning.
- Mobile Material patterns enlarged for desktop.
- A download telemetry dashboard, terminal panel, format table, codec picker, flag editor, or raw backend-log viewer.
- Settings, onboarding, account, cloud, history, and library surfaces before a real product need exists.
- Confetti, celebration pages, giant success treatments, or playful motion that delays the task.
- Decorative icons where text is clearer, icon-only essential actions, or controls hidden behind hover.

## Design Principles

1. **Paste is intent.** A valid paste starts resolution immediately; the interface does not ask for a redundant Analyze or Enter action.
2. **Reveal only what is useful now.** Empty and Resolving use a compact link-first window; metadata, choices, and destination appear only after resolution expands the existing window.
3. **Keep context stable.** One window, one primary column, and one URL anchor persist across all states. Automatic sizing changes height only at task-stage boundaries, while user-selected window placement and size take precedence.
4. **Let Jewel be the system.** Use Jewel controls, typography, focus behavior, metrics, and semantic colors before adding product-specific styling.
5. **Prove the design in running code.** Generated concepts may guide G0, but coded gates use deterministic real Jewel UI evidence only.

## Accessibility & Inclusion

Use a basic native-desktop accessibility baseline rather than pursuing formal certification in this design change. Support logical keyboard focus order, visible focus, meaningful semantics, understandable progress, sufficient contrast, and status communication that does not rely on color alone. Long titles, long paths, missing thumbnails, reasonable font scaling, and both light and dark themes remain required review cases.
