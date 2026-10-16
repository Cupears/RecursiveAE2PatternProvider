# Changelog

## 1.2.0-beta.2

Fixed the recursive encoding terminal failing to open when TransparentAE2 is installed because both mods registered GUI sync ID 30000. Moved recursive mode to a separate shared ID and added a parent-menu synchronization regression check.

## 1.2.0-beta.1

I added an orange recipe-tree editor opened by right-clicking a held recursive pattern in air. I can disable or re-enable exact generated recipe routes, save those exclusions on the pattern, and inspect nested example recipes. I added bounded server-authored previews, save/cancel, pan/zoom, reset controls, and checks that the held pattern has not changed before saving.

## 1.1.3

I aligned the fork's metadata and documentation with the author's published CC BY 4.0 license. I included the full license, attribution, and unchanged NeoForge template notice in the JAR. Crafting behavior is unchanged from 1.1.2.

## 1.1.2

- I made the recursive toggle respect my off choice when I re-encode an existing recursive pattern.
- I guarded the optional Wireless Comprehensive Work Terminal integration so it is skipped when that mod is absent.
- I extended the orange ingredient tooltips to successful plans, including crafted intermediates, and kept missing-source explanations first.
- I limited Minecraft compatibility metadata to 26.1.2 and added links to this fork in the mod list.
- I added troubleshooting guidance, a crafting bug-report form, and builds that publish versioned releases from tags.

## 1.1.1

I brought my clock, recipe-selection, material-variant, stale-pattern, and missing-source fixes together on main. I renamed the download to `otter-patterns` and enabled recursive encoding by default for fresh installations.
