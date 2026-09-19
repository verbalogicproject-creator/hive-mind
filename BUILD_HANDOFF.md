# Build Handoff

Create the Android project in AI Studio Build mode, supply this pack, and start with `ai-studio-prompts/00_MASTER_PROMPT.md`. Generate only Phase 0.

Use the generated wrapper and existing pipeline equivalents:

```bash
./gradlew clean :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then test loopback MCP with Inspector. If dependencies fail, do not replace MCP with handwritten JSON-RPC; resolve Kotlin/Ktor/SDK compatibility.

Do not casually reopen: FTS-first baseline; supersession; Room plus vault; SAF; official SDK/Streamable HTTP; stopped/loopback/read-only defaults; no required cloud; canonical export/migrations.

After Phase 0 record generated Kotlin/AGP/Gradle/Compose/min/target SDK; exact Room/MCP/Ktor versions; APK size/device; negotiated protocol; foreground lifecycle result; desktop-to-phone routability.
