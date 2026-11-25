# Native migration

This branch introduces a native Android entry experience while preserving the existing HTML execution engine.

## Architecture
- Native Kotlin + Jetpack Compose home screen for project selection and recent-project launch.
- Android Storage Access Framework for selecting a project folder or one HTML document.
- The existing Java WebView activity remains the isolated HTML/CSS/JavaScript runtime. Replacing that renderer with Compose would remove the product's core ability to execute arbitrary web projects.

## Safety
- Work is isolated on `native-migration`; `main` and the current release are unchanged.
- The preview workflow builds an APK artifact only and does not publish or replace a release.
- The existing package ID and release signing key are retained so a validated build can later update the installed app.
