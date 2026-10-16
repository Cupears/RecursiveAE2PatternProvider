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
- Opening a held recursive pattern through its actual right-click packet.
- Loading 200 additional recipe alternatives across multiple server responses, exceeding 320 total nodes.
- Expanding branches, panning, zooming, resetting the view and toggling recipe routes.
- Saving rules to the held pattern, reopening, cancelling changes and resetting saved rules.
- Checking the short save confirmation.
- Blocking Cancel during a pending save, rejecting a missing session and rejecting a changed held item.
- The missing-confirmation timeout boundary (advanced directly rather than waiting 30 seconds).

Results are written to `../recipe-tree-smoke/` beside the checkout. The report must end in `PASS`; a successful Gradle exit alone does not establish that the checks passed. The smoke harness is excluded from all JAR archives. Normal builds do not compile it.

This isolated check uses AE2 and the specified compatibility mod. It does not replace verification of a full modpack, its custom recipes or multiplayer play.
