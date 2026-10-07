async function prepareVisualPage(page) {
  // Match the low-power profile of hosted runners so snapshots remain deterministic.
  // Battle scenes still load the external survivor and island assets in this profile.
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 2 });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => 4 });
  });
}

module.exports = { prepareVisualPage };
