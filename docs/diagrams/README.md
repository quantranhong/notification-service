# Architecture diagrams

## Detail architecture & component interaction (PDF)

- **View in browser:** Open [detail-architecture-component-interaction.html](detail-architecture-component-interaction.html) in a browser for the full document with diagrams.
- **Download as PDF (manual):** In that page, click **“Download as PDF”** (or press Ctrl+P / Cmd+P), then choose **“Save as PDF”** or **“Microsoft Print to PDF”** as the destination.
- **Generate PDF file (script):** From this directory run:
  ```bash
  npm install
  npm run generate-pdf
  ```
  This produces **two PDFs** in this folder (requires Node.js and Puppeteer):
- **detail-architecture-component-interaction.pdf** — Full detail architecture, components, data model, API, deployment
- **architecture-interaction.pdf** — Architecture overview and interaction (sequence) diagrams

On first run you may need: `npx puppeteer browsers install chrome`.

## Source HTML

- [detail-architecture-component-interaction.html](detail-architecture-component-interaction.html) — Full detail document
- [architecture-interaction.html](architecture-interaction.html) — Architecture and sequence diagrams for review
