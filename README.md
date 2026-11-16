# ServerHub GUI 2.3.0

**Eine zentrale Proxy-Konfiguration für alle Backends. Automatische Serverliste, scrollbare Ansicht und Statuspunkte.**

[JAR herunterladen](https://github.com/etzewetze/serverhubgui/releases/download/v2.3.0/ServerHub-2.3.0.jar) · [Release und Komplettpaket](https://github.com/etzewetze/serverhubgui/releases/tag/v2.3.0)

Für **Minecraft Java / Paper 26.3**, **Velocity 4.2.0**, **Java 25**. Dieselbe JAR auf Proxy und Backends; kein Client-Mod erforderlich.

![Designreferenz der Listenansicht mit beispielhaften Statusfarben; kein Ingame-Screenshot](preview/Menue-Vorschau.png)

## Zentrale Datei

Auf dem Velocity-Proxy wird automatisch **`plugins/serverhub-velocity/servers.yml`** angelegt. Alle registrierten Ziele werden darin mit Anzeigename, Farbe und Schriftgröße ergänzt. Weitere erkannte Ziele kommen später hinzu. Vorhandene Anpassungen werden beim Ergänzen erhalten; entfernte Ziele bleiben als Einstellungen in der Datei, erscheinen aber nicht im Menü.

Nur diese Datei für Menü, Serverdarstellung und HUD bearbeiten. Anschließend **`/serverhubproxy reload`** am Proxy ausführen. Die Einstellungen werden automatisch an verbundene Backends verteilt; bei einem Beitritt wird die vollständige Konfiguration neu übertragen. Keine einzelnen Paper-Menükonfigurationen mehr bearbeiten.

Beispiel:

```yaml
menu:
  title: Serverauswahl
  title-color: '#3D2817'
  text-color: '#FFF4D6'
  font-size: 12
  layout: list
  navigation: scroll
  page-size: 6
  show-page-info: true
  show-server-count: false
  single-page-info: false
status:
  enabled: true
  ping-interval-seconds: 10
  timeout-ms: 2000
servers:
  lobby:
    name: Treffpunkt
    color: '#A8E091'
    font-size: 12
    permission: ''
    description: ['Die gemeinsame Lobby']
    hidden: false
    order: 0
    status-override: auto
    sleep-motd-contains: []
  survival:
    name: Überleben
    color: '#F2C06D'
    font-size: 10
    permission: ''
    description: []
    hidden: false
    order: 1
    status-override: auto
    sleep-motd-contains: ['[SLEEPING]']
```

`servers`-Schlüssel sind die echten bei Velocity registrierten Namen, auch Namen mit Punkten bleiben unverändert. Bis zu 256 Ziele werden angezeigt; Namen dürfen Buchstaben, Zahlen, Punkt, Unterstrich und Bindestrich enthalten und maximal 64 Zeichen lang sein. `font-size` unterstützt 8, 10 oder 12. Farben als `'#RRGGBB'` angeben. `order` sortiert aufsteigend; bei gleichem Wert gilt die alphabetische Reihenfolge.

`hidden: true` blendet ein Ziel aus. Ein gesetztes `permission` wird am Proxy und zusätzlich auf dem Backend geprüft. Ein ausdrückliches `false` für `serverhub.connect.<target>` am Proxy sperrt das Ziel ebenfalls. Beide Prüfungen gelten auch beim Verbindungsversuch; ein alter oder manipulierter Menüaufruf umgeht sie nicht.

Ungültige Reloads behalten die vorherige gültige Konfiguration. Die Datei wird dabei nicht durch Standardwerte ersetzt. Die automatische Ergänzung liest vorhandene Änderungen vor dem Speichern erneut ein. Backend-Adressen und Ports werden nicht an Spieler oder über die Menükonfiguration verteilt.

## Listen, Raster und Seiteninfo

| Einstellung | Bedeutung |
|---|---|
| `menu.layout: list` | Server stehen untereinander. Standard. |
| `menu.layout: grid` | Zwei Spalten. |
| `menu.navigation: scroll` | Alle freigegebenen Ziele in einem nativen Dialog; bei zu wenig Platz scrollbar. Standard. |
| `menu.navigation: pages` | Blättern mit Zurück/Weiter und `page-size` Zielen pro Seite. |
| `menu.show-page-info: false` | Seiteninfo vollständig ausblenden. |
| `menu.show-server-count: false` | Keine Angabe wie „6 Server“. Standard. |
| `menu.single-page-info: false` | Bei nur einer Seite keine Seiteninfo. Standard. |

Im Scrollmodus gibt es keine künstlichen Seiten und damit keine Anzeige „Seite 1/1“. Im Seitenmodus steht die optionale Info in einer separaten Informationszeile nach den Server-/Blätteraktionen und direkt vor „Schließen“. Der Schließen-Button bleibt im nativen Dialog-Fußbereich. Bei sehr kleinen Clientfenstern kann der Inhaltsbereich auch im Seitenmodus zusätzlich scrollen.

Das Menü verwendet native Dialoge mit eigenen Holzbuttons, kräftiger Schrift und einer Pergamentrolle. Es öffnet kein Truhen- oder Spielerinventar. Escape und „Schließen“ führen zurück ins Spiel. Vollständige Namen, Beschreibungen und Status stehen im Tooltip. Das Menü wird bei Statusänderungen nicht ungefragt neu geöffnet, damit eine Scrollposition nicht verloren geht; erneutes Öffnen zeigt den aktuellen Stand.

## Statuspunkte

| Punkt | Bedeutung im Automatikmodus |
|---|---|
| Grün | Der registrierte Server beantwortet den Status-Ping. |
| Rot | Kein gültiger Ping innerhalb des konfigurierten Zeitlimits; nicht erreichbar. |
| Gelb | Eine ausdrücklich konfigurierte Schlaf-Kennung wurde in MOTD oder Versionsbeschreibung gefunden. |
| Grau | Noch keine aktuelle Prüfung vorhanden. |

**Ein leerer Server ist nicht automatisch schlafend.** Ein normaler Minecraft-Status-Ping übermittelt keinen verlässlichen allgemeinen Schlafstatus. Ein pausierter Paper-Server oder ein Schlaf-Proxy kann weiterhin normale Pingantworten liefern. Deshalb werden null Spieler nicht als gelb interpretiert.

Für einen Schlaf-Host oder ein Schlaf-Plugin dessen eindeutige Kennung unter `sleep-motd-contains` eintragen. Groß-/Kleinschreibung spielt keine Rolle. Ohne konfigurierte Kennung gibt es keine automatische Schlaf-Vermutung. Alternativ kann die Anzeige zentral mit `status-override: sleeping` fest vorgegeben werden. Weitere Werte: `auto`, `online`, `offline`. Bei einer festen Vorgabe wird das Ziel nicht zusätzlich gepingt; sie bleibt bis zur nächsten Änderung bestehen.

Rot beweist keine abgeschaltete Maschine: auch Firewall, Netzwerkstörung oder deaktivierte Pingantworten können die Ursache sein. Die Statusanzeige blockiert keine Verbindungsversuche, damit beispielsweise ein Schlaf-Host beim Beitritt aufwachen kann. `status.enabled: false` blendet die Punkte aus und beendet die regelmäßigen Status-Pings.

## Netzwerkerkennung und Update

1. Velocity und alle Paper-Backends beenden. Alte ServerHub-JARs entfernen und dieselbe neue `ServerHub-2.3.0.jar` auf Proxy und Backends installieren.
2. Netzwerk vollständig neu starten. Bereits eingerichtetes Modern Forwarding bleibt Voraussetzung; Backends dürfen nur über den vertrauenswürdigen Proxy erreichbar sein.
3. Die erzeugte `plugins/serverhub-velocity/servers.yml` am Proxy bearbeiten und `/serverhubproxy reload` ausführen.
4. `/serverhubproxy packcheck` prüfen und neu verbinden, damit das neue Ressourcenpaket geladen wird.

Paper erkennt vorhandene Velocity-/Bungee-Forwarding-Einstellungen automatisch. Im Netzwerkmodus verwendet es die bestätigte Proxy-Konfiguration pro Sitzung. Alte lokale Namen, Farben, Schriftgrößen und HUD-Einstellungen werden dort nicht mehr angewendet. Gewünschte frühere Anpassungen einmal in die zentrale Datei übernehmen; die alten Backend-Dateien bleiben erhalten.

Bei einem eigenständigen Paper-Server werden Proxy-Handshake und Netzwerk-Auswahl deaktiviert; Spieler werden nicht wegen eines fehlenden Proxys gekickt. Dies ist kein eigenständiger serverübergreifender Serverbrowser. Für besondere Installationen gibt es lokal ausschließlich den Modus-Override `network.mode: proxy` bzw. `standalone`; Standard ist `auto`. Die automatische Erkennung liest die normalen `config/paper-global.yml`-/`spigot.yml`-Pfade bzw. das alte `paper.yml`.

Bei unveränderten Standardpaketen aus 2.1 oder 2.2 sichert der Proxy die bisherige ZIP und installiert die neue eingebettete ZIP automatisch. Eigene Paketdateien werden nicht überschrieben. Bei `self-host` oder `external` die neue Release-ZIP selbst als lokale Paketdatei und am Downloadziel bereitstellen.

## Bedienung und HUD

**Shift + F** öffnet das Menü, ebenso `/extramenü`, `/extramenu`, `/extramen` oder `/serverhub`. Bei umbelegtem Handtausch gilt die neue Taste; die Grafik zeigt die Standardkombination.

Auch diese Einstellungen stehen zentral in `servers.yml`:

```yaml
shift-f-hint:
  enabled: true
  interval-seconds: 1
  x-offset: 106
  y-offset: 0
hotbar-icon:
  enabled: false
  slot: 8
```

Drei Holzbretter zeigen Shift / + / F rechts neben der Hotbar, ohne einen Inventarplatz zu belegen. `x-offset` gibt GUI-Pixel rechts der Bildschirmmitte an (94–512), positive `y-offset`-Werte verschieben nach unten (−32 bis 32). Der Hinweis nutzt die Actionbar-Fontausgabe. Andere Actionbar-Plugins oder HUD-Mods können ihn überlagern; F1 blendet die normale HUD-Ausgabe aus. Die Grafik ist ein Tastaturhinweis, keine frei anklickbare HUD-Schaltfläche.

Optional ist ein zusätzlicher Rechtsklick-Gegenstand in der Hotbar möglich. Bei belegtem Slot wird nur in einen freien normalen Inventarplatz umgelagert; vorhandene Gegenstände werden nicht überschrieben. Er ist standardmäßig deaktiviert.

## Ressourcenpaket und Befehle

Download-/Netzwerkeinstellungen bleiben auf dem Proxy in `config.properties`. `pack.mode=github` lädt die exakte ZIP über HTTPS aus dem versionsgebundenen Release. Kein zusätzlicher HTTP-Port nötig. `self-host` verwendet einen eigenen HTTP-Port, `external` eine direkte HTTP(S)-ZIP-Adresse. Die lokale ZIP muss exakt zu den Downloadbytes passen.

Ein ServerHub-Paketangebot pro Netzwerkverbindung bleibt erhalten. Backend-Wechsel und Reload senden kein neues Angebot. Nur die erfolgreiche Anwendung des genau zugehörigen Pakets schaltet das Menü frei; Fehler, Ablehnung oder Timeout führen zum Kick. Fremde Pakete werden nicht entfernt.

| Aktion | Befehl / Recht |
|---|---|
| Menü öffnen | `/extramenü`; `serverhub.use` (Standard alle) |
| Zentrale Einstellungen laden | Am Proxy `/serverhubproxy reload`; `serverhub.proxy.reload` |
| Status / Paketdownload prüfen | `/serverhubproxy status` / `packcheck`; gleiches Proxy-Recht |
| Ziel sperren | `serverhub.connect.<target>` am Proxy ausdrücklich auf `false` setzen |

`/extramenü reload` auf einem Backend verweist im Netzwerkmodus auf den Proxy. Nach einem erfolgreichen zentralen Reload werden Änderungen ohne Backend-Neustarts verteilt. Bereits geöffnete alte Menüaktionen werden entwertet; das Menü erneut öffnen.

## Build und Prüfung

Java 25, Maven 3.9+ und Python 3:

```sh
python3 tools/package_pack.py
mvn -B clean verify
python3 tools/prepare_release.py
```

Die installierbare Universal-JAR liegt unter `universal/target/ServerHub-2.3.0.jar`. Modul-JARs sind interne Builddateien. Normale Builds verpacken eingecheckte Grafiken unverändert. Zum Neugenerieren Pillow installieren und `tools/build_pack.py` sowie `tools/render_preview.py` ausführen. SnakeYAML für die Proxy-Konfiguration wird in einen privaten Namespace gepackt.

Tests prüfen die zentrale Datei, Erhaltung von Anpassungen, ungültige Reloads, Übertragung und Größenlimits, Netzwerkerkennung, Status-/Schlafklassifikation, Berechtigungen und den bestehenden Paketlebenszyklus. Die Universal-JAR wird mit getrennten Plattform-Klassenladern geprüft. GitHub Actions veröffentlicht auf `main` JAR, ZIP, Prüfsummen, Prüfbericht, Beispielkonfiguration und Komplettpaket; danach prüft es die öffentlichen Downloads samt eingebetteter Paketbytes. Bestehende Releases bleiben unverändert.

Kein vollständiger Ingame-Test. Die Vorschau verwendet echte Texturen mit beispielhaften Statuswerten und ist keine Aufnahme eines laufenden Clients. Scrollverhalten, native Dialogausrichtung und HUD-Position im eigenen Client prüfen. Bedrock/Geyser und andere Spielversionen sind nicht geprüft.

Die Holztextur stammt aus der bereitgestellten Vorlage; Pergamentrolle, Infozeile und Statuspunkt werden lokal generiert. Schrift: DejaVu Sans Bold, Lizenz liegt bei.

- [Paper Dialog-API](https://docs.papermc.io/paper/dev/dialogs/)
- [Minecraft Dialoge und Scrolllisten](https://www.minecraft.net/de-de/article/minecraft-java-edition-1-21-6)
- [Velocity RegisteredServer-Pings](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/server/RegisteredServer.html)
