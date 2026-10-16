# Otter’s AE2 Recursive Patterns

[![Minecraft 26.1.2](https://img.shields.io/badge/Minecraft-26.1.2-FF8A24?style=flat-square)](#requirements)
[![NeoForge](https://img.shields.io/badge/Loader-NeoForge-D96B19?style=flat-square)](#requirements)
[![Java 25](https://img.shields.io/badge/Java-25-B45309?style=flat-square)](#requirements)
[![Download JAR](https://img.shields.io/badge/Download-JAR-FF8A24?style=flat-square)](https://github.com/Cupears/RecursiveAE2PatternProvider/releases/latest)

**Recursive AE2 crafting, with fewer patterns to manage.**

I maintain this fork of [Lumengrid's Recursive AE2 Pattern Provider](https://github.com/Lumengrid/RecursiveAE2PatternProvider). I brought my fixes together on `main`, so there is one complete version to download and build.

[Download the JAR](https://github.com/Cupears/RecursiveAE2PatternProvider/releases/latest) · [Report an issue](https://github.com/Cupears/RecursiveAE2PatternProvider/issues) · [My upstream contributions](https://github.com/Lumengrid/RecursiveAE2PatternProvider/issues/16)

## Requirements

- Minecraft **26.1.2**, NeoForge **26.1.2.109**, and Java **25**.
- Applied Energistics 2 for Minecraft 26.1.2. I use **26.1.13-beta** in my FTB Evolution 2 setup.
- Optional integrations: AdvancedAE, ExtendedAE, ExtendedAE Plus, and Wireless Comprehensive Work Terminal.

## Install

1. Download `otter-patterns-1.1.0.jar` from Releases.
2. Close Minecraft and stop the server before replacing an older version.
3. Put the JAR in the client and server `mods` folders, or just the client folder for single-player.
4. Remove the older Recursive AE2 Pattern Provider JAR from `mods`, then launch again.

> [!WARNING]
> Install only one copy. I retain the original mod ID, `recursiveae2patternprovider`, so this replaces Recursive AE2 Pattern Provider. Remove the older JAR from `mods` before launching.

Existing recursive flags and the config filename remain compatible.

## Use it

Encode your final crafting recipe in an AE2 Pattern Encoding Terminal and enable its recursive toggle. Put that pattern in a connected Pattern Provider with access to Molecular Assemblers. The provider generates eligible crafting dependencies, such as planks and sticks, for AE2 to plan and execute.

You can also combine an encoded pattern with an iron ingot to mark it recursive. The required item is configurable. Craft a recursive pattern by itself to remove the recursive flag.

To start new encoding sessions in recursive mode, add this to `config/recursiveae2patternprovider-common.toml`:

```toml
defaultRecursiveMode = true
```

> [!TIP]
> Recursive mode starts off by default. Set `defaultRecursiveMode = true` to start new encoding sessions with it enabled. In multiplayer, set this on the server; you can still switch it off in the terminal.

Existing stored patterns are not converted automatically.

## What I changed

- Preserve empty slots in shaped recipes, including clocks.
- Offer recipe alternatives so AE2 can use available materials without expanding compression loops.
- Generate valid ingredient variants and keep the selected materials fixed during execution.
- Add short orange tooltips explaining what a missing source would produce.
- Refresh stale ingredient keys before new plans when the crafting network is idle.
- Add a server-synchronized default for recursive encoding.

## Configuration

The common config lives at `config/recursiveae2patternprovider-common.toml`.

| Setting | Default | Purpose |
| --- | --- | --- |
| `defaultRecursiveMode` | `false` | Start new encoding sessions with recursion enabled. |
| `recursionDepth` | `-1` | Unlimited depth; `0` disables generation, positive values limit depth. |
| `retainRecipeAlternatives` | `true` | Let AE2 consider bounded alternative recipe routes. |
| `generatedIngredientAlternatives` | `true` | Generate valid material variants with exact execution inputs. |
| `recipeItem` | `"minecraft:iron_ingot"` | Item used to mark an encoded pattern recursive. |

## Scope

I generate crafting dependencies; machine operations such as smelting still need existing processing patterns or supplied materials. Species-specific recipes still require their specific wood. Recipe choices are bounded and do not guarantee the cheapest possible route. The stale-key refresh covers ordinary AE2 and AdvancedAE providers and waits while a crafting CPU is busy.

## Build from source

Use JDK 23 to run the included Gradle wrapper. The configured toolchain resolves Java 25 for compilation.

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`. The installable JAR is written to `build/libs/`. GitHub Actions also attaches the JAR to successful builds.

## Credits

Original mod by **Lumengrid**. Fork maintenance and the changes above by **Cupears**. I keep the upstream licensing and attribution intact. My individual contributions remain available as separate upstream pull requests.
