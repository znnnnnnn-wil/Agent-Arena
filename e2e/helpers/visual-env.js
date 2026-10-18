async function prepareVisualPage(page) {
  // Match the low-power profile of hosted runners so snapshots remain deterministic.
  // Battle scenes use the shared articulated forest cast in this profile.
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 2 });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => 4 });
  });
}

module.exports = { prepareVisualPage };
