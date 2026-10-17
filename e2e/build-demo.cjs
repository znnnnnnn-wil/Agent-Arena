const fs = require('node:fs/promises');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const source = path.join(root, 'src/main/resources/static');
const output = path.join(root, 'target/demo-site');
const repository = 'https://github.com/znnnnnnn-wil/Agent-Arena';

async function buildDemo() {
  // Only this explicitly named generated directory is cleared, never the checkout.
  if (path.resolve(output) !== path.resolve(root, 'target', 'demo-site')) throw new Error('Unexpected output path');
  await fs.rm(output, { recursive: true, force: true });
  await fs.mkdir(output, { recursive: true });
  const textFiles = ['demo.html', 'demo.js', 'demo.css', 'styles.css', 'fonts.css',
    'arena-3d.js', 'arena-3d.css', 'battle-island.js', 'battle-world.js'];
  for (const filename of textFiles) {
    let text = await fs.readFile(path.join(source, filename), 'utf8');
    if (filename === 'demo.html') {
      // GitHub Pages hosts the self-contained demo. Creation belongs to the full application.
      text = text.replaceAll('href="/#create"', `href="${repository}#创建第一场对局"`)
        .replaceAll('href="/"', `href="${repository}"`)
        .replaceAll('创建我的第一场对局', '查看创建指南')
        .replaceAll('创建我的对局', '查看创建指南')
        .replaceAll('配置狼人杀 →', '查看狼人杀玩法 →')
        .replaceAll('配置大逃杀 →', '查看大逃杀玩法 →')
        .replaceAll('配置图灵潜伏局 →', '查看图灵潜伏局玩法 →');
    }
    // Relative URLs also work below the /Agent-Arena/ project-site prefix.
    text = text.replace(/(["'])\/(?!\/)/g, '$1./');
    await fs.writeFile(path.join(output, filename), text);
    if (filename === 'demo.html') await fs.writeFile(path.join(output, 'index.html'), text);
  }
  for (const directory of ['vendor', 'assets/3d', 'assets/fonts', 'assets/demo']) {
    await fs.cp(path.join(source, directory), path.join(output, directory), { recursive: true });
  }
  for (const filename of ['LICENSE', 'THIRD_PARTY_NOTICES.md']) {
    await fs.copyFile(path.join(root, filename), path.join(output, filename));
  }
  await fs.writeFile(path.join(output, '.nojekyll'), '');
  console.log(`GitHub Pages demo: ${output}`);
}

buildDemo().catch((error) => { console.error(error); process.exitCode = 1; });
