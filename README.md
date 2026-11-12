# Smalltalk-Trainer – Android-App

Die ganze App steckt in **`smalltalk-trainer.html`**, derselben Datei wie das claude.ai-Artifact.
`android/` ist eine schlanke WebView-Hülle darum, ohne AndroidX und ohne Abhängigkeiten.

## APK holen
Jeder Push baut über GitHub Actions automatisch eine APK und hängt sie an ein Release:
**Releases → neuestes → `Smalltalk-Trainer.apk`**.

## Was die Hülle macht
- **Sprachausgabe:** Die Android-WebView hat keine Web Speech API. `assets/android-bridge.js` ersetzt
  `speechSynthesis` durch die Android-Sprachausgabe (`TextToSpeech`). Stimmen kommen aus
  *Einstellungen → Sprachausgabe* des Handys (z. B. Google-Sprachdienste + Sprachpakete).
- **Zurück-Taste:** schließt Dialoge, tippt sonst „‹ Zurück“ an, geht sonst zur Übersicht und
  beendet die App erst auf der Übersicht.
- **Offline:** Die Schrift Nunito (OFL) ist eingebettet, die App braucht kein Internet.
- **Speicher:** `localStorage` bleibt in den App-Daten erhalten. Er geht nur beim Deinstallieren verloren.
- **KI-Chat:** Er läuft nur auf claude.ai (`claude.use("sample")`). In der APK zeigt der Reiter
  „nicht verfügbar“.

## Signatur (einmalig einrichten)
Damit sich Updates über die alte Version installieren lassen, muss jede APK mit demselben Schlüssel
signiert sein. Der Schlüssel liegt **nicht** im Repo, sondern als Secret:
*Settings → Secrets and variables → Actions → New repository secret*
- `KEYSTORE_B64`: der Keystore als Base64 (`base64 -w0 release.jks`)
- `KEYSTORE_PASSWORD`: sein Passwort (Alias: `smalltalk`)

Ohne Secret signiert der Build mit einem Debug-Schlüssel. Die APK lässt sich installieren, aber jede
neue Version muss man vorher deinstallieren, und dabei geht der Fortschritt verloren.

## Lokal bauen
```
cd android && ./gradlew assembleRelease   # braucht Android-SDK (ANDROID_HOME), JDK 17+
```
