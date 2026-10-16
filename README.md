# Otter's AE2 Recursive Patterns

[![Minecraft 26.1.2](https://img.shields.io/badge/Minecraft-26.1.2-FF8A24?style=flat-square)](#requirements)
[![NeoForge](https://img.shields.io/badge/Loader-NeoForge-D96B19?style=flat-square)](#requirements)
[![Java 25](https://img.shields.io/badge/Java-25-B45309?style=flat-square)](#requirements)
[![Download JAR](https://img.shields.io/badge/Download-JAR-FF8A24?style=flat-square)](https://github.com/Cupears/RecursiveAE2PatternProvider/releases/latest)

**Recursive AE2 crafting, with fewer patterns to manage.**

I maintain this fork of [Lumengrid's Recursive AE2 Pattern Provider](https://github.com/Lumengrid/RecursiveAE2PatternProvider). I brought my fixes together on `main`, so there is one complete version to download and build.

[Download the JAR](https://github.com/Cupears/RecursiveAE2PatternProvider/releases/latest) | [Report an issue](https://github.com/Cupears/RecursiveAE2PatternProvider/issues) | [My upstream contributions](https://github.com/Lumengrid/RecursiveAE2PatternProvider/issues/16)

## Requirements

- Minecraft **26.1.2**, NeoForge **26.1.2.109**, and Java **25**.
- Applied Energistics 2 for Minecraft 26.1.2. I use **26.1.13-beta** in my FTB Evolution 2 setup.
- Optional integrations: AdvancedAE, ExtendedAE, ExtendedAE Plus, and Wireless Comprehensive Work Terminal.

## Install

1. Download `otter-patterns-1.2.0-beta.6.jar` from Releases.
2. Close Minecraft and stop the server before replacing an older version.
3. If updating, remove the previous `otter-patterns-*.jar` from `mods`.
4. Put the new JAR in the client and server `mods` folders, or just the client folder for single-player, then launch again.

> [!WARNING]
> If the original **Recursive AE2 Pattern Provider** is installed, remove its `recursiveae2patternprovider-*.jar` too. My fork uses the same mod ID, so the original and this fork cannot be installed together. Keep only the newest Otter JAR in `mods`.

Existing recursive flags and the config filename remain compatible.

## Use it

Encode your final crafting recipe in an AE2 Pattern Encoding Terminal. New encoding sessions start with recursion enabled by default; you can switch it off using the terminal toggle. Put the pattern in a connected Pattern Provider with access to Molecular Assemblers. The provider generates eligible crafting dependencies, such as planks and sticks, for AE2 to plan and execute.

You can also combine an encoded pattern with an iron ingot to mark it recursive. The required item is configurable. Craft a recursive pattern by itself to remove the recursive flag.

> [!TIP]
> Recursive mode is on by default. No config edit is needed for a fresh installation. If an older config already has `defaultRecursiveMode = false`, change it to `true` in `config/recursiveae2patternprovider-common.toml`. In multiplayer, the server controls this default.

To start with recursion off instead, set `defaultRecursiveMode = false`. Existing stored patterns are not converted automatically.

## What I changed

- Preserve empty slots in shaped recipes, including clocks.
- Offer recipe alternatives so AE2 can use available materials without expanding compression loops.
- Generate valid ingredient variants and keep the selected materials fixed during execution.
- Add short orange tooltips explaining what ingredients make in successful and partial plans.
- Refresh stale ingredient keys before new plans when the crafting network is idle.
- Add a server-synchronized default for recursive encoding, with an explicit off toggle that also works when re-encoding.
- Load optional wireless-terminal integration only when its mod is installed.

## Recipe-tree editor

On this feature branch, I added a light-orange recipe-tree editor with AE2-style panels.

1. Put a recursive pattern in the Pattern Encoding Terminal's encoded-pattern slot.
2. Click **Configure recipe tree** beside the encoding options. The tree opens expanded, with the requested item on the left and its dependencies extending to the right.
3. **Shift-click a recipe** to disable or re-enable that exact recipe everywhere within this pattern. Other recipes making the same item stay available. **Disable route** and right-click provide the same recipe-wide toggle.
4. **Save** updates the pattern in the terminal and returns to it. **Cancel** or Escape discards unsaved edits.

Drag the empty canvas to pan, scroll to move, and use `+` / `-` to zoom. **Reset rules** clears this pattern's exclusions; **Reset view** recenters the tree. The button also supports the Wireless Comprehensive Work Terminal's crafting panel.

I save up to 128 exact recipe exclusions on the encoded pattern. They survive copying, saving, and re-encoding. This filters dependencies generated for that pattern; AE2's shared network can still use manually supplied patterns or routes offered by other recursive patterns/providers.

Branches load automatically in small batches without a total node, recipe-count or depth cutoff. Repeated ingredients link to their already-expanded recipes, and circular paths stop at the repeated item. Recipe grids show example ingredients rather than a selected AE2 craft or ME inventory stock, so accepted wood and other variants may differ. Saved exclusions remain intact while the tree loads. If all routes are disabled, the item can still come from storage or a supplied pattern.

## Configuration

The common config lives at `config/recursiveae2patternprovider-common.toml`.

| Setting | Default | Purpose |
| --- | --- | --- |
| `defaultRecursiveMode` | `true` | Start new encoding sessions with recursion enabled. |
| `recursionDepth` | `-1` | Unlimited depth; `0` disables generation, positive values limit depth. |
| `retainRecipeAlternatives` | `true` | Let AE2 consider bounded alternative recipe routes. |
| `generatedIngredientAlternatives` | `true` | Generate valid material variants with exact execution inputs. |
| `recipeItem` | `"minecraft:iron_ingot"` | Item used to mark an encoded pattern recursive. |

## Scope

I generate crafting dependencies; machine operations such as smelting still need existing processing patterns or supplied materials. Species-specific recipes still require their specific wood. Recipe choices are bounded and do not guarantee the cheapest possible route. The stale-key refresh covers ordinary AE2 and AdvancedAE providers and waits while a crafting CPU is busy.

## Troubleshooting

| What I see | What to check |
| --- | --- |
| An unexpected wood in the plan | Hover it to see the orange **Used to make** line. Generic chests can use another wood; a cherry drawer still requires cherry materials. |
| Missing essence, hats, or another unusual source | The tooltip describes the selected recipe path. Supplying its product directly can avoid that path; recalculate the plan afterwards. |
| A missing material normally made in a machine | Add its processing pattern or supply it directly. Recursive generation covers crafting recipes. |
| A job stays scheduled | Check provider/assembler connections, power, and the waiting ingredients. Cancel and request a fresh plan after changing patterns. If it still stalls, include the status screen in an issue. |
| Recursion starts off | Check the server's existing `defaultRecursiveMode` value. A saved `false` overrides the new-install default. |
| A duplicate-mod error at launch | Keep one Otter JAR and remove the original Recursive AE2 Pattern Provider JAR. |

[Report a crafting problem](https://github.com/Cupears/RecursiveAE2PatternProvider/issues/new?template=bug_report.yml) with the requested item and quantity, mod versions, and screenshots of the plan and stalled status where applicable.

## Build from source

Use JDK 23 to run the included Gradle wrapper. The configured toolchain resolves Java 25 for compilation.

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`. The installable JAR is written to `build/libs/`. GitHub Actions builds pushes to `main` and pull requests, and attaches the JAR to successful builds. Version tags matching `v<mod_version>-otter.*` also publish a release after the build passes. A manual run can build a branch or publish a matching version tag.

[Read the changelog](CHANGELOG.md) | [View builds](https://github.com/Cupears/RecursiveAE2PatternProvider/actions)

## Credits

Original mod by **Lumengrid (Lumengrid89)**. I maintain this independent fork as **Cupears**; my changes are described above and in [CHANGELOG.md](CHANGELOG.md). This fork is not an official upstream release. My individual contributions remain available as separate upstream pull requests.

I keep this fork under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The NeoForge MDK template retains its separate [MIT license](src/main/resources/META-INF/MDK-MIT.txt).
