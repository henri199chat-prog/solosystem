# SoloSystem — rules for Claude Code
- Android app (Kotlin + Jetpack Compose), package `com.ayman.solosystem`. Owner: Ayman (Tunisia, writes Arabic, phone only, beginner): explain simply in Arabic.
- Save tokens: read only the files you need; don't analyse the whole project unless asked.
- Before any big change, write a short plan and wait for Ayman's approval.
- Never commit secrets: no keystore, passwords or API keys. The signing key is NOT in this repo.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` for each new version.
- Public download link (GitHub Releases): the APK asset must be named exactly `SoloSystem.apk`
  https://github.com/henri199chat-prog/solosystem/releases/latest/download/SoloSystem.apk
- Signed release APKs are built by Ayman's builder (APKMEN), because the signing key lives only there. A different key would block updating over the installed app.
- Keep the player's save data compatible across updates (no destructive migrations).
