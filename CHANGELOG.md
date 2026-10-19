# Changelog

## 1.2.0-beta.16

- Prevent generated recovery recipes from crafting their own conversion inputs, avoiding dye-and-recover glass detours.
- Keep recovery from stored variants and normal ingot-to-block crafting available.

## 1.2.0-beta.15

- Added an orange From pattern line identifying the stored recursive pattern behind a crafting-plan ingredient.
- Track pattern origins through generated dependencies and shared recipe paths.

## 1.2.0-beta.14

- Restore orange missing-source explanations for direct inputs of recursive patterns.
- Explain flexible missing inputs when the selected source is unambiguous.

## 1.2.0-beta.13

- Item choices now save per-pattern locks, with an Any accepted item reset.
- Enforce locked root inputs during AE2 planning and execution; filter generated dependencies by saved choices.
- Added JEI crafting and machine recipe previews with full recipe viewer access.
- Keep editor sessions intact while visiting JEI.

## 1.2.0-beta.12

- Added a searchable icon picker to view ingredient alternatives together.
- Fixed shapeless ingredient substitutions when transferred slots differ from recipe order.
- Start ingredient previews on a non-cyclic choice and mark cyclic alternatives.

## 1.2.0-beta.11

- Redesigned the personal blacklist with searchable rows, item previews, and full recipe-ID tooltips.
- Added dedicated remove buttons, a scrollbar, recipe counts, and a Done button.
- Kept blacklist controls separate from tree clicks and zoom shortcuts.

## 1.2.0-beta.10

- Prefer accepted FTB Materials replacements over obsolete ingredient duplicates.
- Omit hidden alternatives when visible choices exist in the tree and generated recipes.
- Explain replaced exact ingredients without silently changing their requirements.

## 1.2.0-beta.9

- Show tag and substitution alternatives as one ingredient choice, preserving quantities and separate requirements.
- Include accepted choices in nested recipes, with paging for large groups.
- Wrap card names, descriptions and recipe IDs within their reserved space; full text stays available in tooltips.

## 1.2.0-beta.8

- Paged large initial ingredient lists so patterns with hundreds of accepted inputs can open.
- Preserved selected branches when more variants arrive during loading.
- Protected personal defaults changed outside the editor and skipped unchanged file writes.
- Prevented large ingredient counts from overflowing and reduced tooltip rebuilding.

## 1.2.0-beta.7

- Mouse-wheel zoom follows the cursor.
- Added a personal recipe blacklist and management panel; new recursive patterns inherit saved defaults.
- Blocked recipe paths collapse immediately, and compressed routes start collapsed.
- Grouped color and wood types into compact cards with cycling previews and type controls.

## 1.2.0-beta.6

- Restored the left-to-right recipe-tree layout and horizontal connectors.
- Reset view returns to the main item at the upper left.

## 1.2.0-beta.5

- Moved the editor to a Configure recipe tree button in pattern terminals.
- Trees open expanded and load automatically, with the main item above its dependencies.
- Added light-orange AE2-style panels and shared-ingredient links.
- Shift-click toggles the selected recipe throughout the edited pattern, preserving alternatives.
- Save and Cancel return to the terminal; saving checks the encoded pattern still matches.

## 1.2.0-beta.4

- Shortened the save confirmation to "Recipe choices saved."
- Added a transparent editor background, smaller cards and closer branch spacing.
- Replaced the tree's node, recipe-count and depth cutoffs with branches that load on demand. All static alternatives remain browsable; cycles stop at repeated items.
- Added checks for large recipe lists, deep branches and pattern-specific furnace exclusions.

## 1.2.0-beta.3

- Expired or missing editor sessions return an error instead of leaving Save waiting indefinitely.
- Cancel and Escape wait for an in-progress save to finish, with a timeout if no confirmation arrives.
- Pattern decoding errors are handled without breaking the editor's right-click request.
- Added an isolated client smoke run covering terminal compatibility, editor navigation, saving, cancelling and stale sessions.

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
