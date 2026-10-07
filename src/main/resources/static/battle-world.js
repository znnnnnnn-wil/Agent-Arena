(() => {
  const profiles = {
    A: { name: '中央村落', resources: '武器 / 弹药', coverReduction: 0, restBonus: 0 },
    B: { name: '北部林地', resources: '食物 / 医疗', coverReduction: 4, restBonus: 0 },
    C: { name: '河畔仓库', resources: '饮水 / 弹药', coverReduction: 0, restBonus: 0 },
    D: { name: '南部废墟', resources: '护甲 / 弹药', coverReduction: 6, restBonus: 0 },
    E: { name: '野外营地', resources: '医疗 / 补给', coverReduction: 0, restBonus: 3 }
  };
  window.BattleWorld = {
    resolve(game, events = []) {
      const zone = [...events].reverse().find(event => event?.eventType === 'BATTLE_ZONE_UPDATED')?.payload;
      const world = game?.battleWorld;
      const source = world || zone;
      const supply = [...events].reverse().find(event => ['BATTLE_SUPPLY_DROP', 'BATTLE_SUPPLY_CAPTURED'].includes(event?.eventType));
      return {
        known: Boolean(source), ringStage: source?.ringStage || 1,
        safeRegions: Array.isArray(source?.safeRegions) ? source.safeRegions : Object.keys(profiles),
        ringOrder: world?.ringOrder || [], nextShrinkRound: source?.nextShrinkRound ?? null,
        zoneDamage: world?.zoneDamage ?? null,
        supplyDropRegion: world ? world.supplyDropRegion : supply?.eventType === 'BATTLE_SUPPLY_DROP' ? supply.payload?.region : null,
        regionProfiles: world?.regionProfiles || profiles,
        topology: world?.topology || { A: ['B','C'], B: ['A','D'], C: ['A','D','E'], D: ['B','C','E'], E: ['C','D'] }
      };
    }
  };
})();
