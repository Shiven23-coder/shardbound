# Real-device checklist (P-24)

One manual run per region on a phone of the target class: mid-range Android, about 4 GB RAM, Snapdragon 6-series class (§4.13). Record the device, browser, date and results in the phase report. Final tuning of night darkness, light radii and rain happens here, in the real Phaser scene, not in offline renders.

## Setup (phone and computer on the same home Wi-Fi)
1. Start Docker Desktop on the computer.
2. In the repo root, run `npm run dev:lan`. It is `npm run dev` with Vite listening on the home network; the phone only talks to Vite, which forwards `/api` to the API on the computer.
3. When Vite prints `Network: http://192.168.x.y:5173/`, note that address. (Or run `ipconfig` and take the IPv4 address of the Wi-Fi adapter.)
4. The first time, Windows asks whether Node.js may accept connections: allow **Private networks** only. If the phone cannot connect later, check that the Wi-Fi network is set to *Private* in Windows settings.
5. On the phone, open Chrome at that address, e.g. `http://192.168.1.20:5173`.
6. Set screen brightness to about **50 %** and turn adaptive brightness off.
7. When done, stop with Ctrl+C. Nothing stays open on the network.
- Start a **New game** with a fresh profile.
- **[core]** marks the 10 checks that matter most: do them first, in both orientations, if time is short.
- Play the sections below **upright (portrait, the main phone layout) and sideways (landscape)**; tick each box for both, or note which one failed. Rotating mid-game is allowed and should switch the layout at once (P-40).

## Eldmere (V3)

### Look and readability
- [ ] **[core]** **Night readability at ~50 % phone brightness:** on the storm night, the player, the lane, the trees and the water can be told apart outside the light pools; the cottage, Corvin's lantern and the Heartshard read as warm or green pools of light.
- [ ] Water is never the brightest thing on screen at night.
- [ ] The player's own light is visible but subtle; it disappears at dawn.
- [ ] Rain reads as light rain (thin streaks, varied angle), not noise; splashes show on puddles and the flooded road.
- [ ] Dawn (after taking the Heartshard) is clearly brighter and has no rain.
- [ ] Pixels are crisp at the chosen zoom (no blur or shimmering while walking).

### Layout (P-39, P-40)
- [ ] **[core]** **Portrait:** the world fills the top; the control deck below holds the D-pad, Ⓐ, Bag and Menu. No "turn your phone" hint.
- [ ] **[core]** **Portrait:** when a dialogue opens, its text and choices appear in the deck and the world above stays uncovered, with the speaker in view; long text scrolls while the choices stay visible.
- [ ] **Landscape:** the world fills the screen; the D-pad and Ⓐ are translucent until pressed, then fully visible while held.
- [ ] **Landscape:** the dialogue takes at most about a third of the screen and the speaker stays visible above it.
- [ ] Rotating the phone switches between the two layouts without a reload, also with a dialogue open.
- [ ] The HUD (level badge, HP and energy bars, top-left) is small; numbers show for a moment when a value changes or after tapping the bars. It fades after a few calm seconds and never hides the player (walk into the top-left corner of a map: it all but disappears).
- [ ] Gold shows bottom-right only for a moment when it changes, and never under the Ⓐ button.
- [ ] The full character sheet (HP, XP, energy, gold, weapon) is in the Bag.
- [ ] **Portrait deck (P-47):** about 30 % of the screen, no large empty bands; Bag and Menu are small pills that are still easy to hit.
- [ ] **Arrival (P-47, P-49):** entering an area shows its name large in the middle for about 2 s; any opening dialogue comes after it, then the objective line; never two of them at once, and no permanent area label.
- [ ] **Landscape (P-47):** Bag and Menu are translucent like the D-pad until pressed.
- [ ] Notches, rounded corners and the gesture bar do not cover the HUD, the deck or the controls (safe areas).

### World (P-43, P-45, P-46)
- [ ] **[core]** Walk around every building in all four areas (the cottage, Maren's and the other houses, the forge, kiln and stall tent, the mill, the watchtower, the ruined house): the player never walks into a wall or a roof, and stands in front of a wall when below it.
- [ ] The cottage door: Ⓐ next to it says "The door is barred against the storm."
- [ ] The flooded west road (Cottage Lane), the flooded ford (Mill Road) and the rubble (Old Ruins) block the way and explain themselves when used; no "The way is closed." message anywhere.
- [ ] Crates block Tanner's Alley before you follow Corvin, and are gone after "Follow him".
- [ ] A small marker (A on touch, matching the Ⓐ button) floats over the nearest person or object in reach, and moves when another one is closer.
- [ ] The objective line appears at the start and when it changes (after taking the Heartshard, after following Corvin), and the Menu always shows it.

### Dialogue (P-44)
- [ ] **[core]** Text comes in pages of at most 4 lines upright and 3 sideways that break between sentences (or clauses), with no empty band under short ones; the last page, above the choices, is never a scrap of a sentence. A tap completes a page, a second tap turns it; ▼ marks a finished page with more to come.
- [ ] Choices appear only on the last page; tapping a choice takes it at once; tapping the text never takes a choice by accident.

### Controls
- [ ] **[core]** The D-pad walks one tile per tap and keeps walking smoothly while held, in all four directions; a quick tap on a new direction only turns the player; letting go stops on the next tile (P-51).
- [ ] Lifting the thumb always stops walking, even after sliding it off the button (no stuck movement).
- [ ] The interact button talks to Corvin, Maren and Brann, and reads the signs, when standing next to them.
- [ ] Tapping an NPC or sign next to the player also works.
- [ ] **[core]** Every button (D-pad, interact, Bag, Menu, dialogue choices, tabs, Settings sliders and switches) is easy to hit with a thumb (≥ 48 px).
- [ ] **Hints (P-41):** a new profile gets "Hold the arrows to walk." at the start and "Tap Ⓐ to talk." (or "to look") at the first person or sign in reach; each disappears once done or skipped with ✕ and does not come back for that profile. Settings → Show hints off stops them.
- [ ] **Controls screen** (title screen and Menu → Controls) lists keyboard, gamepad and touch and fits the screen in both orientations.

### Sound (P-42)
- [ ] Nothing plays before the first tap; after it, the Eldmere music and (on the storm night) the rain start.
- [ ] **On an iPhone (any iOS version you have):** the music plays too (the AAC fallback, P-48).
- [ ] Footsteps sound while walking and stop when standing; dialogue text blips softly; menu buttons click.
- [ ] Levels feel right at a normal phone volume: the music does not drown the footsteps or the rain. Note any sound that is too loud or too quiet.
- [ ] Settings → Mute silences everything at once; the music and effects sliders change their levels.
- [ ] Locking the phone and coming back does not leave sound stuck or doubled.

### Walk the prologue
- [ ] Opening dialogue → Cottage Lane → the Old Ruins → take the Heartshard → back to the lane → the Square.
- [ ] The dawn text plays once on entering the Square, and not again.
- [ ] Mill Road from the Square and back; the millstone and the ford sign answer.
- [ ] **[core]** Corvin's offer → follow him → the alley → the fight → the ending (END_QUICK).
- [ ] Signs, the cottage door and the obstacles open as a message box (■, no choice button) that a tap closes.
- [ ] Helping Corvin on the storm night shows "Corvin will remember that." (no numbers); the Bag still shows the affinity number.
- [ ] Pushing hard into a fence, a wall or Tanner's crates never carries the player through, even when the phone stutters.

### Resilience
- [ ] Lock the phone mid-walk for a minute, unlock: the game continues where it was.
- [ ] **[core]** Turn Wi-Fi off, try to walk through an exit, turn it back on: "Reconnecting…" shows, then the game resumes.
- [ ] Reload the page in the Square: the run resumes in the Square at the same place.

### Performance (feel)
- [ ] **[core]** Walking is smooth (no visible stutter) in each area, in rain and at dawn.
- [ ] The first area loads within about 2 seconds after the opening dialogue; later areas within about 1.5 seconds.
- [ ] The phone does not get noticeably warm after ten minutes.

### Record
| Item | Value |
|---|---|
| Device / Android version | |
| Browser and version | |
| Date | |
| Orientation(s) tested | |
| Problems found | |
