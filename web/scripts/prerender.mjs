// After `vite build` and the SSR build of src/prerender.tsx: writes dist/prerendered/<page>.html,
// each one dist/index.html with that page's HTML, title and description filled in.
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'

const { PAGES, render } = await import('../dist-ssr/prerender.js')
const template = readFileSync('dist/index.html', 'utf8')
const escape = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')

mkdirSync('dist/prerendered', { recursive: true })
for (const [name, page] of Object.entries(PAGES)) {
  const html = template
    .replace(/<title>.*?<\/title>/, `<title>${escape(page.title)}</title>\n    <meta name="description" content="${escape(page.description)}" />`)
    .replace('<div id="root"></div>', `<div id="root"><div data-prerendered>${render(name)}</div></div>`)
  if (!html.includes('data-prerendered')) throw new Error('dist/index.html has no empty <div id="root"></div> to fill')
  writeFileSync(`dist/prerendered/${name}.html`, html)
  console.log(`prerendered ${page.path} → dist/prerendered/${name}.html`)
}
rmSync('dist-ssr', { recursive: true, force: true })
