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

1. Download `otter-patterns-1.1.1.jar` from Releases.
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
- Add short orange tooltips explaining what a missing source would produce.
- Refresh stale ingredient keys before new plans when the crafting network is idle.
- Add a server-synchronized default for recursive encoding.

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

## Build from source

Use JDK 23 to run the included Gradle wrapper. The configured toolchain resolves Java 25 for compilation.

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`. The installable JAR is written to `build/libs/`. GitHub Actions also attaches the JAR to successful builds.

## Credits

Original mod by **Lumengrid**. Fork maintenance and the changes above by **Cupears**. I keep the upstream licensing and attribution intact. My individual contributions remain available as separate upstream pull requests.
