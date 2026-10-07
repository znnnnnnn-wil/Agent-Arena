async function prepareVisualPage(page) {
  // Desktop snapshots exercise the detailed stage even when the hosted runner has fewer CPUs.
  // Mobile still selects the low-power mode through its viewport.
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
  });
}

module.exports = { prepareVisualPage };
