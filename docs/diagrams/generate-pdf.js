#!/usr/bin/env node
/**
 * Generates both architecture PDFs from HTML files.
 * Requires: npm install (in this directory), then npm run generate-pdf
 * Output:
 *   - detail-architecture-component-interaction.pdf (full detail architecture)
 *   - architecture-interaction.pdf (architecture & interaction diagrams)
 */

const path = require('path');
const fs = require('fs');

const jobs = [
  {
    html: 'detail-architecture-component-interaction.html',
    pdf: 'detail-architecture-component-interaction.pdf',
    label: 'Detail architecture & component interaction'
  },
  {
    html: 'architecture-interaction.html',
    pdf: 'architecture-interaction.pdf',
    label: 'Architecture & interaction diagrams'
  }
];

async function generatePdf(page, htmlPath, pdfPath) {
  await page.goto('file://' + htmlPath, { waitUntil: 'networkidle0' });
  await new Promise((r) => setTimeout(r, 2500));
  await page.pdf({
    path: pdfPath,
    format: 'A4',
    printBackground: true,
    margin: { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' }
  });
}

async function main() {
  let puppeteer;
  try {
    puppeteer = require('puppeteer');
  } catch (e) {
    console.error('Run "npm install" in docs/diagrams first (installs puppeteer).');
    process.exit(1);
  }

  for (const job of jobs) {
    const htmlPath = path.join(__dirname, job.html);
    if (!fs.existsSync(htmlPath)) {
      console.error('HTML file not found:', htmlPath);
      continue;
    }
  }

  const browser = await puppeteer.launch({ headless: 'new' });
  try {
    const page = await browser.newPage();
    for (const job of jobs) {
      const htmlPath = path.join(__dirname, job.html);
      const pdfPath = path.join(__dirname, job.pdf);
      if (!fs.existsSync(htmlPath)) continue;
      await generatePdf(page, htmlPath, pdfPath);
      console.log('PDF written:', job.pdf, '(', job.label, ')');
    }
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
