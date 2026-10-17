# Recipe-tree client smoke check

This opt-in development run creates a temporary flat world in `run/`, checks real client/server packets and UI controls, writes screenshots and a report, then closes the development client normally. It never changes an installed modpack or existing world.

Use the Gradle launcher JDK 23. Gradle supplies the Minecraft Java 25 runtime.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
.\gradlew.bat -PeditorSmoke runClient --console=plain
```

To include TransparentAE2, add `'-PsmokeCompatJar=C:\path\to\transparentae2.jar'` to that command. The supplied JAR must match the project's Minecraft and AE2 versions.

The run exercises:

- Pattern Encoding, Crafting, Pattern Access and ME terminals, including recursive-mode synchronization.
- Opening the editor through the terminal button with a real encoded crafting pattern.
- Automatically loading 200 additional recipe alternatives across multiple server responses, exceeding 320 total nodes.
- Expanded defaults, shared-ingredient links, wheel zoom and reset view.
- Folded compressed routes, grouped color recipes, cycling previews, hover pause and type selection.
- Shift-click excluding only the selected recipe, leaving alternatives available and collapsing the blocked path.
- Saving a personal blacklist, inheriting it through AE2 encoding a new pattern, and removing remembered recipes.
- Cancel preserving both pattern rules and saved personal defaults.
- Saving to the encoded slot and returning to the terminal, cancelling edits and resetting rules.
- Blocking Cancel during a pending save and rejecting a changed encoded pattern without overwriting its replacement.
- Checking that right-clicking a held pattern and requests for an invalid menu cannot open the editor.

Results are written to `../recipe-tree-smoke/` beside the checkout. The report must end in `PASS`; a successful Gradle exit alone does not establish that the checks passed. The smoke harness is excluded from all JAR archives. Normal builds do not compile it.

This isolated check uses AE2 and the specified compatibility mod. It does not replace verification of a full modpack, its custom recipes or multiplayer play.
