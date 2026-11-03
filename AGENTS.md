<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

## Project rules

- The real product is the native Android app in `android/` (Kotlin, no XML layouts); the web app in `src/` is only the landing/guide page. Why: screen capture and overlays need native Android APIs.
- `TranslatorService` must never run OCR on a frame that contains its own overlay: show results only after the screen is still, clear the overlay as soon as the content moves, and replace (never merge) blocks. Why: MediaProjection captures our overlay, which made translations stick and pile up.
- The APK is built by `.github/workflows/android.yml` on a pinned runner image with pinned action majors; verify Kotlin changes with a local Gradle build before finishing. Why: `ubuntu-latest`/old action versions broke CI.
- Translation models are fetched only through `LangModels` (async, shared, with timeout) and never awaited on the capture worker; language choice (`LangPick`) and OCR text cleanup/patch color (`TextFix`) are pure objects with JUnit tests in `android/app/src/test`. Why: blocking downloads of randomly-guessed languages froze auto mode.
