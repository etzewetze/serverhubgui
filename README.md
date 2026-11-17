# ServerHub GUI 2.5.1

Zentrale Serverauswahl für Velocity: AMP-Schlafstatus, mehrere eigene Schriften, automatisch passende Buttonbreiten, eigene Button-PNGs und Server-Neuigkeiten. Keine Client-Mod nötig; der feste Dialogkopf und die native Scrollliste öffnen kein Truhen- oder Spielerinventar.

[ServerHub-2.5.1.jar](https://github.com/etzewetze/serverhubgui/releases/download/v2.5.1/ServerHub-2.5.1.jar) · [Release und Komplettpaket](https://github.com/etzewetze/serverhubgui/releases/tag/v2.5.1) · [ServerNavigator ab 1.2.0](https://github.com/etzewetze/ServerNavigator/releases/tag/v1.2.0)

**Minecraft Java / Paper 26.3, Java 25, Velocity 4.2.0 oder 4.3.0.** ServerNavigator 1.2.0 benötigt Velocity 4.3.0. Dieselbe ServerHub-JAR auf Proxy und allen Backends installieren.

![Designreferenz mit den enthaltenen Texturen und beispielhaften Statuswerten, kein Ingame-Screenshot](preview/Menue-Vorschau.png)

## Installation und zentrale Verwaltung

1. Proxy und Backends beenden, alte ServerHub-JARs durch `ServerHub-2.5.1.jar` ersetzen, dann neu starten. Menüprotokoll 3 erfordert das gemeinsame Update.
2. Im Netzwerk ausschließlich **`plugins/serverhub-velocity/` am Proxy** bearbeiten. Alle registrierten Velocity-Ziele werden automatisch in **`servers.yml`** angelegt. Eigene Namen, Farben und Einstellungen bleiben erhalten. Entfernte Ziele behalten ihre Konfiguration, erscheinen aber nicht im Menü.
3. `/serverhubproxy reload` prüft Einstellungen und baut das Pack. Nach einer Packänderung neu verbinden. `/serverhubproxy status`, `fonts` und `packcheck` helfen bei der Prüfung.

Paper erkennt Velocity-/Bungee-Forwarding automatisch. Lokale Einstellungen und Packs werden dann ignoriert; am Backend wird kein eigener Pack-Host gestartet. `network.mode` in der lokalen `config.yml`: `auto`, `proxy`, `standalone`. Vorhandenes sicheres Proxy-Forwarding weiter verwenden.

Ohne Proxy liegen dieselben Dateien unter **`plugins/ServerHub/`**. `/sh reload` lädt lokal neu; es gibt nur das lokale Ziel `local`. Die Netzwerkerkennung ermittelt keine fremden Server ohne Velocity.

## CubeCoders AMP: den tatsächlichen Schlafzustand lesen

AMP kann den Java-Prozess vollständig beenden und Minecraft-Pings trotzdem aus dem Cache beantworten. Das erklärt eine grüne Anzeige trotz 0 MB RAM. Das Paper-Plugin kann im beendeten Prozess keine Meldung mehr senden. **Für diesen Modus `amp.yml` am Proxy einmalig konfigurieren:**

```yaml
enabled: true
url: 'https://panel.example.org/'
mode: controller
username: serverhub
password: ''
token: 'DEIN-SERVICE-LOGIN-TOKEN'
poll-interval-seconds: 3
stale-after-seconds: 12
auto-match: true
```

Die Vorlage enthält alle Einstellungen. Entweder Benutzer und Passwort oder Benutzer und einen gültigen AMP-Service-Login-Token verwenden. Bei Tokenanmeldung das Passwort leer lassen. Alternativ Namen vorhandener Umgebungsvariablen in `password-env` / `token-env` eintragen. Keine Webseite mit vorgeschalteter interaktiver Anmeldung verwenden. API-Dokumentation der eigenen AMP-Installation: Paneladresse plus `/API`.

ServerHub verwendet ausschließlich `Core/Login` und `ADSModule/GetInstances`. Der Benutzer benötigt die passenden Session- und Instanz-Leserechte in AMP; keine Start-, Stop- oder Konsolenrechte erforderlich. Die Zugangsdaten bleiben auf dem Proxy. Der Pollingaufruf startet oder weckt selbst keinen Spielserver.

Eindeutige AMP-Instanznamen bzw. FriendlyNames werden mit den Velocity-Namen verglichen. Stimmen sie nicht überein, zentral pro Ziel zuordnen:

```yaml
servers:
  survival:
    amp-instance: 'AMP-Instanz-ID-oder-exakter-Name'
```

Bei mehreren gleich benannten Instanzen die eindeutige `InstanceID` verwenden. Mit `auto-match: false` werden nur explizit zugeordnete Ziele von AMP verwaltet; alle anderen nutzen den bisherigen Statusweg. Bei aktivem Auto-Matching ohne eindeutigen Treffer, unbekanntem Zustand oder abgelaufener API-Antwort erscheint **Grau** statt eines irreführenden grünen Cache-Pings. `/serverhubproxy status` zeigt die API-Diagnose und die getrennten Ping-/Backendwerte.

Standardzuordnung in `amp.yml`: `Sleeping`/50 und `PreparingForSleep`/45 → Gelb; `Ready`/20 → Grün; Start-/Wartezustände → Gelb; gestoppt/Fehler → Rot. Falls die AMP-Version andere Zustandskennungen liefert, lassen sich die Listen unter `states` ergänzen/überschreiben. Eine unbekannte Kennung wird nicht als online ausgegeben.

`mode: instance` mit `instance-target: survival` kann stattdessen den direkten Web-Endpunkt **einer** AMP-Spielinstanz über `Core/GetStatus` lesen. Für mehrere Server den ADS-Controller und `mode: controller` verwenden. Bei API-Problemen werden alte Werte nur bis `stale-after-seconds` verwendet; fehlgeschlagene Anmeldungen haben eine Wartezeit.

## Menü, Breiten und eigene Buttongrafiken

```yaml
menu:
  title: Serverauswahl
  font-family: sans
  font-size: 12
  title-font-size: 12
  title-padding: 48
  title-max-width: 400
  button-width: 0
  button-min-width: 180
  button-max-width: 360
  align-button-widths: true
  button-style: minecraft
  rounded-buttons: false
  layout: list
  navigation: scroll
  show-server-count: false
  show-page-info: true
  single-page-info: false
servers:
  challenges:
    name: Challenges
    color: '#FFFFFF'
    font-size: 12
    font-family: ''
    width: 0
    background: ''
    background-tint: '#FFFFFF'
    rounded: false
    prefix: ''
    hover-enabled: true
    hover: ''
    amp-instance: ''
    status-override: auto
    sleep-motd-contains: []
    hidden: false
    order: 0
    permission: ''
    description: []
```

`button-width: 0` misst den tatsächlichen Text einschließlich aktivem Präfix und Newszeichen. `align-button-widths: true` gibt allen Servern die größte benötigte Breite. Dadurch wird beispielsweise „Challenges“ vollständig angezeigt. `servers.<ziel>.width: 240` setzt eine eigene Wunschbreite; bei gemeinsamer Ausrichtung gilt der größte Wert für alle. Für unterschiedlich breite Buttons `align-button-widths: false`. Serverbreiten: 80–500 GUI-Pixel. Bei extrem langen Texten begrenzt `button-max-width` die automatische Breite.

Der Titel bleibt fest über der Scrollliste. Die neue originale, transparente Pergamentgrafik wird aus zwei Endstücken und einem variablen Mittelteil zusammengesetzt. `title-padding` lässt mehr Abstand zwischen Text und eingerollten Enden. Der Titel wird bei `title-max-width` passend gekürzt. Das gelbe Warnsymbol ist Teil des Minecraft-Clients und per Dialog-API nicht abschaltbar. `title-warning-offset: 24` gleicht dessen Platz rechts aus; einstellbar von 0 bis 40.

Standard bleiben native Minecraft-Buttons mit unveränderter Hover-/Fokusdarstellung. Für eine eigene Grafik **`backgrounds/challenges.png`** im Proxy-Pluginordner ablegen und `background: challenges` am Ziel setzen. Die gesamte PNG wird auf dieselbe Buttonfläche skaliert; Text und Statuspunkt bleiben daran ausgerichtet. Vorlagen `blue`, `paper` und `wood` sind enthalten. `background-tint` tönt die Grafik, Weiß erhält die Originalfarbe.

`rounded: true` bzw. `menu.rounded-buttons: true` rundet die eigene PNG mit einem Radius von vier GUI-Pixeln. **Die native Klickfläche und die darunterliegende Minecraft-Buttonfläche bleiben rechteckig.** Vollständig freie Buttongeometrie ohne diese Unterlage ist mit nativen Dialogbuttons nicht möglich. Bei `background: ''` und `button-style: minecraft` gibt es keine eigene PNG zum Abrunden. Keine globalen Minecraft-Texturen werden ersetzt.

Die Bedienbuttons haben unter `buttons.previous`, `buttons.next` und `buttons.close` jeweils `width`, `font-size`, `font-family`, `color` und `background-color`. Breite 80–300, feste native Höhe 20 Pixel. `color` tönt den Text; `background-color` wirkt bei `menu.button-style: wood`. Texte unter `menu.previous`, `menu.next`, `menu.close`. `text.shadow: true` und `text.shadow-color` verbessern die Lesbarkeit.

`layout: grid` bietet zwei Spalten. `navigation: pages` aktiviert Blättern mit `page-size`. Im Scrollmodus gibt es keine Seiteninfo; im Seitenmodus erscheint sie standardmäßig erst bei mehreren Seiten direkt über „Schließen“. Mit `show-page-info: false` vollständig abschaltbar.

## Mehrere eigene TTF-/OTF-Schriften

Die Standardfamilien **`sans`**, **`serif`**, **`mono`** sind bereits im Pack enthalten. Eigene Dateien etwa **`fonts/meine_schrift.ttf`** und **`fonts/zweite.otf`** im Proxy-Pluginordner ablegen. Die Schrift-ID ist der Dateiname ohne Endung, in Kleinbuchstaben; andere Zeichen werden zu `_`. IDs beginnen mit einem Buchstaben und enthalten bis zu 32 Zeichen aus `a-z`, `0-9`, `_`, `-`.

```yaml
menu:
  font-family: meine_schrift
servers:
  challenges:
    font-family: zweite
buttons:
  close:
    font-family: serif
fonts:
  dritte:
    url: 'https://example.org/fonts/Dritte.ttf'
    refresh: false
```

Mehrere direkte HTTPS-Links unter `fonts` sind möglich; lokale Dateien und Links nicht mit derselben ID belegen. Es müssen echte TTF/unterstützte OTF-Dateien sein, keine HTML-Seiten, CSS oder ZIPs. Maximal 8 MiB pro Datei und 16 Familien einschließlich der Standards. Größen: **8, 10, 12, 14, 16**. `font.url` bleibt aus Kompatibilität als Einzellink für die ID `custom` verfügbar.

Beim Reload werden neue/geänderte Dateien geprüft und in Minecraft-Bitmap-Schriften für alle Größen umgewandelt. **Die gültige Original-TTF wird nicht direkt vom Client als Fontprovider geladen.** Fehlerhafte Schriftdateien werden ausgelassen, bestehende funktionierende Atlanten bleiben erhalten, fehlende Familien fallen auf Sans zurück. Details in **`font-problems.txt`**; IDs in **`fonts-available.txt`** oder **`/serverhubproxy fonts`**. Die fehlerhafte Datei bleibt zur Korrektur im Quellordner.

Auch `pack/assets/serverhub/fonts/<id>.ttf` wird weiter unterstützt. Unveränderte Schriften werden nicht neu gerastert; manuelle Atlasänderungen bleiben erhalten. Menülabels unterstützen ASCII und Latin-1 einschließlich Umlauten und ß; weitere Zeichen werden durch `?` ersetzt. Bestehende Spieler behalten die Schrift- und Grafikmetriken ihres geladenen Packs bis zur nächsten Netzwerkverbindung.

## Packfehler nach einer eigenen Schrift oder PNG

Ein eigenes Pack unterscheidet sich vom unveränderten GitHub-Download. **`pack.mode=auto` wechselt deshalb auf den eingebauten HTTP-Host, standardmäßig Port 8124/TCP. Dieser muss von den Spielern erreichbar sein.** Eine gültige TTF allein behebt einen nicht erreichbaren Download nicht.

Die Einstellungen stehen in `config.properties`:

| Modus | Auslieferung |
|---|---|
| `auto` | Exakter Standard über den festen GitHub-Release, Änderungen über den eigenen Pack-Host. |
| `self-host` | Immer eigener HTTP-Host. |
| `external` | Direkte ZIP-URL in `pack.external-url`; muss exakt der lokal erzeugten ZIP entsprechen. |
| `github` | Nur unveränderter Standard, eigene Dateien werden abgelehnt. |

Bei SRV, Docker/NAT oder abweichenden öffentlichen Adressen **`self-host.public-host`** und **`self-host.advertised-port`** setzen. `auto` verwendet den Verbindungsnamen des Spielers. Der eingebaute Server spricht HTTP; HTTPS erfordert einen eigenen TLS-Reverse-Proxy. Bind/Port nach dem Start ändern: Proxy neu starten.

`/serverhubproxy packcheck` prüft die angebotene URL, ZIP und SHA-1 **vom Proxy aus**; zusätzlich muss dieselbe Adresse von außen erreichbar sein. Ein clientseitiger Paketfehler wird mit der konkreten Downloadadresse protokolliert. Bei Schriftproblemen `font-problems.txt` prüfen. Ohne die konkrete Schrift und den Clientfehler lässt sich die Ursache eines früheren Fehlversuchs nicht eindeutig bestimmen.

Die bearbeitbare Quelle liegt in **`pack/`**. Der Reload erzeugt **`serverhub-pack.zip`** automatisch, mit neuer SHA-1 und Paket-ID. Eigene Änderungen bleiben erhalten; unveränderte Werksdateien werden bei Updates angepasst. Die vorherige Ausgabe wird als `serverhub-pack-backup-<hash>.zip` gesichert. Bei externer Auslieferung nach jedem Packumbau genau diese neue ZIP hochladen.

Ein Pflichtpaketangebot pro Netzwerkbeitritt: Backendwechsel und Reload senden kein zweites Angebot. Nach Änderungen neu verbinden. Bereits angebotene alte ZIP-Versionen bleiben am eingebauten Host für aktive Sitzungen erreichbar. Nur die erfolgreiche Anwendung des richtigen Packs schaltet das Menü frei.

## Neuigkeiten pro Server

```text
/sh news start Neue Welt verfügbar!\nEntdecke das neue Gebiet.
/sh news stop
/sh news start --server challenges Neue Challenge gestartet!
/sh news stop --server challenges
```

Ohne `--server` gilt der aktuell besuchte Server. Aus der Lobby kann ein anderes Ziel angegeben werden. Maximal 4096 Zeichen, `\n` fügt einen Zeilenumbruch ein; `&`-Farbcodes werden unterstützt. Ein News-Symbol erscheint rechts im Serverbutton. Solange News aktiv sind, öffnet ein Klick auf den markierten Button die Nachricht; **„Server betreten“** führt zur vorhandenen Verbindung bzw. ServerNavigator-Warteschlange, **„← Zurück“** zur Auswahl.

`serverhub.news` wird auf Paper und am Proxy benötigt; dort über das Berechtigungssystem vergeben. Die Proxykonsole kann `/serverhubproxy news start --server challenges Nachricht` und `... stop --server challenges` verwenden. Aktive Nachrichten liegen zentral in **`news.yml`** am Proxy und bleiben nach Neustarts erhalten. `news.enabled: false` blendet die Funktion im Menü aus. Ohne Proxy wird ausschließlich die lokale `news.yml` verwendet.

## Hoverinfos und optionale Präfixe

```yaml
hover:
  enabled: false
  text: |-
    {name}
    Status: {status}
    Spieler: {players}/{max_players}
prefix:
  enabled: false
  text: '{players} Spieler'
  placeholderapi: true
```

Hover ist standardmäßig aus. `servers.<ziel>.hover-enabled: false` deaktiviert es zusätzlich pro Server; `hover` überschreibt dort den globalen Text. Interne Zielnamen erscheinen nur, wenn die Vorlage `{target}` enthält. Unterstützte Platzhalter: `{name}`, `{target}`, `{status}`, `{players}`/`{online}`, `{max_players}`/`{max}`. Spielerzahlen kommen von den aktuellen Velocity-Verbindungen zum jeweiligen Ziel; die maximale Zahl vom letzten Status-Ping. Unbekannte Zahlen erscheinen als `?`.

Aktivierte Präfixe stehen vor dem Servernamen. Ein servereigenes `prefix` überschreibt `prefix.text`. Optional wird **PlaceholderAPI** auf dem **aktuellen Paper-Server** über dessen öffentliche API verwendet, ohne harte Abhängigkeit. Die dort benötigten Expansions müssen installiert sein. Spielerbezogene `%...%`-Werte beziehen sich auf den Betrachter und das Backend, auf dem er das Menü öffnet; sie lesen nicht automatisch fremde Backenddaten. Für zielbezogene Informationen `{players}` bzw. geeignete Netzwerk-Expansions verwenden. `prefix.placeholderapi: false` deaktiviert die Auflösung auch in Hovertexten.

## Status und ServerNavigator

| Farbe | Bedeutung |
|---|---|
| Grün | AMP meldet Ready oder eine gültige Pingantwort ohne Schlafsignal liegt vor. |
| Gelb | AMP/Paper schläft oder ServerNavigator startet/verbindet gerade. |
| Rot | AMP meldet gestoppt/Fehler oder der Status-Ping ist nicht erreichbar. |
| Grau | Daten fehlen, sind zu alt oder die AMP-Zuordnung ist nicht eindeutig. |

`servers.<ziel>.status-override`: `auto`, `online`, `offline`, `sleeping`. Ein Override hat Vorrang. **0 Spieler, 0 CPU oder ein erfolgreicher Porttest sind allein kein eindeutiger Schlafnachweis.** `sleep-motd-contains` kann für andere Schlafanbieter mit eindeutiger Kennung verwendet werden.

**Native Paper-Pause** bleibt separat unterstützt. Das Backend liest `Server.isPaused()` und sendet authentifizierte Meldungen ohne Spielertransport an den Proxy. In `servers.yml` am Dateianfang stehen `status.ping-interval-seconds` (1), `timeout-ms` (1000), `heartbeat-interval-seconds` (1) und `heartbeat-timeout-seconds` (5). Der interne **Statusport 8125/TCP** muss nur vom Backend zum Proxy erreichbar sein und ist vom öffentlichen Packport 8124 getrennt. `report-host: auto` nutzt die Proxy-IP aus der Statusverbindung. Bei anderem Routing zentral einen Host setzen; explizite abweichende Hosts werden beim nächsten Besuch jedes Backends über den privaten Proxykanal bestätigt. Danach funktioniert der gespeicherte Meldeweg auch ohne Spieler. `status-secret.bin` und `proxy-status.properties` enthalten interne Zugangsdaten und werden automatisch verwaltet.

Geöffnete Menüs werden nur bei geänderten Werten erneuert. Der Client kann dabei die Scrollposition zurücksetzen. `status.refresh-open-menu: false` zeigt Änderungen erst beim erneuten Öffnen. Der feste Titel bleibt außerhalb der Scrollliste.

**ServerNavigator ab 1.2.0 auf dem Proxy** wird automatisch genutzt (`integration.servernavigator: true`). Dessen Wakeup, Startprüfung, Verzögerung, Warteschlange, Berechtigungen, Cooldowns und Abbruch bleiben zuständig. ServerHub umgeht diese Abläufe nicht durch einen parallelen Direktwechsel. Während der Wartephase ruht der Shift-F-Actionbarhinweis. Die Statusfarbe sperrt keine Verbindung; ein schlafendes Ziel kann weiter über Navigator geweckt werden. Ohne Navigator bzw. für dort nicht verwaltete Ziele gilt der normale Velocity-Wechsel.

## Befehle und Rechte

| Aktion | Befehl / Recht |
|---|---|
| Menü | `/sh`, `/serverhub`, `/extramenu`, `/extramenü`, `/extramen`; `serverhub.use` (alle) |
| News setzen/entfernen | `/sh news start ...`, `/sh news stop`; `serverhub.news` auf Paper und Proxy |
| Proxy laden | `/serverhubproxy reload`; `serverhub.proxy.reload` |
| Diagnose | `/serverhubproxy status`, `fonts`, `packcheck`; gleiches Proxyrecht |
| Standalone laden | `/sh reload`; `serverhub.reload` |
| Ziel sperren | `serverhub.connect.<ziel>` am Proxy ausdrücklich `false`, zusätzlich `permission` pro Ziel möglich |

Shift + F öffnet ebenfalls die Auswahl. Der Holzbrett-Hinweis Shift / + / F rechts neben der Hotbar belegt keinen Slot. Position über `shift-f-hint.x-offset` und `y-offset`; andere Actionbar-Plugins können ihn überlagern, F1 blendet ihn aus. Optional ein zusätzlicher Gegenstand mit `hotbar-icon.enabled: true` und `slot: 8`.

## Build, Quellen und Prüfung

```sh
python3 tools/package_pack.py
mvn -B clean verify
python3 tools/prepare_release.py
```

Java 25, Maven 3.9+, Python 3. Universal-JAR: `universal/target/ServerHub-2.5.1.jar`; Modul-JARs sind interne Builddateien. GitHub Actions prüft beide Plattformen, private Bibliotheken, AMP-API-Fixtures, News, Schriftimporte, Layoutmetriken, HTTP-Download und Paketlebenszyklus. Nach Veröffentlichung werden die öffentlichen Downloads und eingebetteten Packbytes samt Prüfsummen geprüft. Details in `PRUEFUNG.txt`.

**Kein vollständiger Ingame-Test und kein Zugriff auf die konkrete AMP-Installation.** Vorschau ist eine Designreferenz. Die AMP-Abfrage ist gegen lokale API-Fixtures geprüft; Panelrechte und Zustandsdaten des eigenen Panels mit `/serverhubproxy status` kontrollieren. Bedrock/Geyser und andere Minecraft-Versionen sind nicht geprüft.

Normale Builds verpacken eingecheckte Assets unverändert. Neugenerierung mit Pillow: `tools/build_pack.py` und `tools/render_preview.py`. Schriften werden zur Laufzeit mit Java AWT ohne Desktopfenster gerastert. SnakeYAML und Gson sind privat in die Universal-JAR gepackt. DejaVu-Schriftlizenz liegt im Pack und unter `art-source/FONT-LICENSE.txt`. Die neue Pergamentgrafik ist eine originale generierte Grafik, keine Kopie des verlinkten Stockfotos; Quelle und Prompt in [BANNER-SOURCE.md](art-source/BANNER-SOURCE.md).

- [CubeCoders AMP API](https://github.com/CubeCoders/ampapi-node)
- [AMP Proteus: Schlafmodus und gecachte Statusantworten](https://discourse.cubecoders.com/t/amp-proteus-2-8-0-release-notes/40953)
- [Paper Dialog-API](https://docs.papermc.io/paper/dev/dialogs/)
- [Mojang: Dialogkopf und Warnsymbol](https://feedback.minecraft.net/hc/en-us/articles/36627959856141-Minecraft-Java-Edition-Snapshot-25w20a)
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)
