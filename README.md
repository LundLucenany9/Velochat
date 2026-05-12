# Velochat

Velochat ist ein Velocity-Plugin für Chat- und Direktnachrichten-Funktionen auf einem Proxy-Netzwerk.

## Funktionen

- Private Nachrichten mit `/msg` und `/r`
- Öffentliche visuelle Antworten im Chat mit `/reply`
- Broadcast-Nachrichten mit `/broadcast`
- Mute/Unmute für Moderatoren mit `/mute` und `/unmute`
- Block/Unblock für Spieler mit `/block` und `/unblock`
- Reload von Konfiguration mit `/velochatreload`
- TOML-Konfiguration über `config.toml` und `messages.toml`

## Voraussetzungen

- Java 21
- Gradle Wrapper (`./gradlew`)
- Velocity 3.4.0-SNAPSHOT (wird in `runVelocity` genutzt)
- Lokale API-Abhängigkeit:
  `compileOnly(files("/home/lundl/IdeaProjects/PAPISocketBridge/build/libs/PAPISocketBridge-velocity-api.jar"))`

## Build

```bash
./gradlew build
```

Das Plugin-JAR liegt danach unter `build/libs/`.

## Lokaler Test mit Velocity

```bash
./gradlew runVelocity
```

## Konfiguration

Beim ersten Start erstellt das Plugin im Plugin-Datenordner:

- `config.toml`
- `messages.toml`

Diese basieren auf den Default-Dateien in `src/main/resources/`.

### Chat-Formatierung & Filter (config.toml)

Wichtige Einstellungen für Chat-Darstellung und Moderation:

- `format`: Standard-Chatformat (MiniMessage, inkl. Reply-Tags wie `<reply_id>` und `<reply_snippet>`)
- `blocked_format`: Vereinfachtes Format bei blockierten Beziehungen
- `replyFormat`: Kopfzeile für öffentliche visuelle Antworten (`/reply`)
- `replayMessagePrefix`: Präfix zwischen Reply-Kopf und Nachricht
- `blocked_reply_format`: Format für Antworten bei Block-Kontext
- `msgFormatSender` / `msgFormatReceiver`: Formate für private Nachrichten (`/msg`, `/r`)
- `filter_enabled`: Wortfilter aktivieren/deaktivieren
- `filter_mode`: `"block"` (Nachricht blockieren) oder `"censor"` (maskieren)
- `filter_words`: Liste der gefilterten Begriffe
- `filter_char`: Ersetzungszeichen für Zensur
- `filter_leetspeak`: Leetspeak-Erkennung (z. B. `h0rse`)
- `ban_alternate_fonts`: Font-Blocker für alternative Unicode-Schriften
- `ban_alternate_fonts_permission`: Bypass-Permission für den Font-Blocker
