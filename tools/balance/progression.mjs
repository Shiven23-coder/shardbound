// Shardbound progression model (V2_SPEC §10.2).
//
// Simulates the Shards economy along the critical path and prints the expected
// player level at each boss, for two scenarios:
//   baseline  — no deaths
//   deaths    — one death per boss; half of the Lost Shards dropped are never recovered
//
// Run from the repo root:   node tools/balance/progression.mjs
// Optional curve override:  node tools/balance/progression.mjs <a> <p> <b> <q> <bossMult>
//   cost(L) = round(a·L^p)   creepShards(lvl) = round(b·lvl^q)   bossShards = bossMult·creepShards(bossLevel)
//
// Model assumptions (keep in sync with §10.2):
//   - the player rests every 3rd fight and before every boss, and buys levels greedily;
//   - weapon upgrades are bought only at the rest before a region's LAST boss;
//   - creep levels rise linearly across each region;
//   - bosses are evenly spaced through their region's fights.
// From V5 the constants are read from content/balance.json; until then they are the defaults below.

const DEFAULTS = { a: 100, p: 1.5, b: 35, q: 1.45, bossMult: 5 };

// [region, fights, [creepLevelMin, creepLevelMax], boss levels, weapon upgrade target, target player level per boss]
const REGIONS = [
  ["Eldmere",         8, [1, 4],   [5],      1, [4]],
  ["Whispering Wood", 15, [4, 9],  [10],     3, [9]],
  ["Hollow Deep",     18, [9, 15], [14, 17], 5, [13, 16]],
  ["Valdren City",    14, [15, 20], [21],    6, [20]],
  ["Ashen Wastes",    18, [20, 27], [25, 28], 8, [24, 27]],
  ["Crystal Spire",   12, [27, 31], [32],    10, [31]],
];

const upgradeCost = (u) => 150 * (u + 1) ** 2;          // +u → +u+1

function simulate(params, scenario) {
  const cost = (level) => Math.round(params.a * level ** params.p);
  const creepShards = (lvl) => Math.round(params.b * lvl ** params.q);

  let level = 1;
  let bank = 0;
  let upgrade = 0;
  let firstLevelUpFight = null;
  let fightNumber = 0;
  let totalLost = 0;
  const rows = [];

  for (const [name, fights, [lo, hi], bossLevels, upgradeTarget, targets] of REGIONS) {
    const row = { name, earned: 0, onLevels: 0, onUpgrades: 0, lost: 0, bossLevels: [], targets };
    const bossAfterFight = bossLevels.map((_, i) => Math.round((fights * (i + 1)) / bossLevels.length));
    let bossIndex = 0;

    for (let f = 1; f <= fights; f++) {
      fightNumber++;
      const creepLevel = Math.round(lo + ((hi - lo) * (f - 1)) / Math.max(1, fights - 1));
      const gained = creepShards(creepLevel);
      bank += gained;
      row.earned += gained;

      const bossNext = bossAfterFight.includes(f);
      if (f % 3 === 0 || bossNext) {                              // rest at a Lantern
        if (bossNext && bossIndex === bossLevels.length - 1) {
          while (upgrade < upgradeTarget && bank >= upgradeCost(upgrade)) {
            bank -= upgradeCost(upgrade);
            row.onUpgrades += upgradeCost(upgrade);
            upgrade++;
          }
        }
        while (bank >= cost(level)) {
          bank -= cost(level);
          row.onLevels += cost(level);
          level++;
          if (firstLevelUpFight === null) firstLevelUpFight = fightNumber;
        }
      }

      if (bossNext) {
        if (scenario === "deaths") {                              // one death; half the drop never recovered
          const lost = Math.floor(bank / 2);
          bank -= lost;
          row.lost += lost;
          totalLost += lost;
        }
        row.bossLevels.push(level);
        const reward = Math.round(params.bossMult * creepShards(bossLevels[bossIndex]));
        bank += reward;
        row.earned += reward;
        bossIndex++;
      }
    }
    row.upgrade = upgrade;
    row.carry = bank;
    rows.push(row);
  }
  return { rows, firstLevelUpFight, totalLost, finalLevel: level };
}

function print(title, result) {
  console.log(`\n== ${title} ==  (first level-up after fight ${result.firstLevelUpFight})`);
  for (const r of result.rows) {
    const levels = r.bossLevels.map((lv, i) => `${lv} (target ${r.targets[i]})`).join(", ");
    console.log(
      `${r.name.padEnd(16)} earned ${String(r.earned).padStart(7)}  levels ${String(r.onLevels).padStart(7)}` +
        `  upgrades ${String(r.onUpgrades).padStart(6)} (+${r.upgrade})  lost ${String(r.lost).padStart(6)}` +
        `  carry ${String(r.carry).padStart(6)}  boss level ${levels}`
    );
  }
  console.log(`total Shards lost: ${result.totalLost}   final level: ${result.finalLevel}`);
}

const args = process.argv.slice(2).map(Number);
const params = args.length === 5
  ? { a: args[0], p: args[1], b: args[2], q: args[3], bossMult: args[4] }
  : DEFAULTS;

console.log(`cost(L) = round(${params.a}·L^${params.p})   creepShards(lvl) = round(${params.b}·lvl^${params.q})   boss ×${params.bossMult}`);
print("baseline: no deaths", simulate(params, "baseline"));
print("deaths: one death per boss, half the Lost Shards never recovered", simulate(params, "deaths"));
