# Ark Survival Returns
NeoForge 26.2, Java 25, Gradle wrapper 9.2.1, GeckoLib 5.
- Use ./gradlew, never system Gradle; use Gradle MCP if available.
- Run ./gradlew runData after data provider changes. Commit generated resources; never hand-edit them.
- Run ./gradlew build and ./gradlew runGameTestServer for gameplay changes.
- Do not launch runClient; the user performs interactive visual testing.
- Keep common/server code free of client imports.
- Import runtime assets from ../Creatures with python tools/import_creatures.py.
- Python asset scripts belong in tools/.
- Spawning must never force chunk loads; respect biome tags, collision, population caps and game rules.
- Use graphify queries if graphify-out/graph.json exists. Run graphify update . after code changes when available.
