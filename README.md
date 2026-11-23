# StorageESP Forge 1.21.11 — v1.2.0

Updated source project. Forge 61.2.5, Java 21 + Java 25 for ForgeGradle, Gradle 9.5.0.

### Installation / GitHub build

The repository structure is intentional: `.github/workflows/build.yml` at the **repository root**, and mod sources inside `StorageESP/`. Upload the **contents of this ZIP** to the existing `StorageESP-Forge` GitHub repository (same paths, replacing older files). A push to `main` runs GitHub Actions. Download the artifact after a **green** build, unzip, then put the `.jar` in Legacy Launcher Forge 1.21.11 `mods/`, removing the old StorageESP jar.

### Changes v1.2

- Player ESP: draw only valid, alive, currently loaded players (no boxes for removed/dead entities); restrict render range; neon-style outlined tracers from in front of camera + endpoint, 2.2px boxes.
- Health: `❤` + current/max HP + 10-segment progress bar, dynamically colored by health.
- Armor: equipped head/chest/legs/boots and type (N=netherite, D=diamond, I=iron, C=chain, G=gold, L=leather, T=turtle).
- Player distance + name rendered as separate customizable label.
- Markers: manual `M`, local death, observed other-player death, optional last-known position in cyan (NOT death). Never guesses death on teleport or chunk unload.
- Warden: remembers only real client-observed hologram timers (custom names and TextDisplay); estimates countdown while traveling; gray = unknown, red = timer running, green = last observed timer expired. Reloading a chest with a server timer resynchronizes it. No more green on freshly loaded chest with missing timer.
- Storage: barrels, ender chests, and shulker boxes have independent toggles (all on by default) + configurable tracer. Cached search every 8 ticks, max 260 boxes / 12 tracers.
- Fix ESP settings now actually being written to `config/storageesp.properties` on save (v1.1 forgot this).
- The responsive, scrollable GUI from v1.1 remains; Right Ctrl opens it, right-click Player ESP/Storage ESP/Marks to configure.

### Limitations and truth in labeling

This project is **not** yet recompiled or runtime-tested in the present environment. GitHub build success only establishes successful compilation, not gameplay correctness. Remote player death is only known when the client sees the player's death state. If the server hides deaths or teleports, it cannot be inferred. The Warden client countdown is approximate: expired timer does not guarantee the server has opened the chest. Chests in never-loaded chunks are unknowable.
