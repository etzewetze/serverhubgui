# ServerHub GUI 2.2.0

**Automatische Velocity-Serverliste, echte Dialoge und ein Shift/+/F-Hinweis neben der Hotbar.**

[Universal-JAR herunterladen](https://github.com/etzewetze/serverhubgui/releases/download/v2.2.0/ServerHub-2.2.0.jar) · [Release und Komplettpaket](https://github.com/etzewetze/serverhubgui/releases/tag/v2.2.0)

Für **Minecraft Java / Paper 26.3**, **Velocity 4.2.0**, **Java 25**. Auf Proxy und Backends wird dieselbe JAR installiert. Kein zusätzlicher Client-Mod erforderlich.

![Designreferenz des Dialogs und schematische HUD-Position; kein Ingame-Screenshot](preview/Menue-Vorschau.png)

## Änderungen in 2.2

- Die Ziele werden direkt mit der Velocity-API aus allen aktuell registrierten Servern gelesen. Auch nachträglich registrierte oder entfernte Server werden beim nächsten Abgleich berücksichtigt.
- Das Menü verwendet die native Dialog-API. Es öffnet weder ein Truheninventar noch ein Spielerinventar. Hinter den Holzbuttons liegen echte Dialog-Aktionen, keine versteckten Inventarslots.
- Breite Holzbuttons und eine neue, kräftige, nicht kondensierte Sans-Schrift ersetzen die kleinen alten Beschriftungen. Standardgröße 12; Umlaute bleiben unterstützt.
- „Serverauswahl“ steht mittig auf einer eigenen Pergamentrolle mit dunkler Schrift.
- Drei beschriftete Holzbretter zeigen **Shift**, **+**, **F** rechts neben der Hotbar. Dafür wird kein Inventarplatz belegt.
- Die bestehende Paketpflicht bleibt erhalten: genau ein Angebot pro Netzwerkverbindung, auch bei Backend-Wechseln und Reloads.

## Installation und Update

1. Velocity und alle Paper-Backends beenden. Alte ServerHub-JARs entfernen; pro Prozess nur eine ServerHub-JAR verwenden.
2. Dieselbe `ServerHub-2.2.0.jar` in `plugins/` des Proxys und aller gewünschten Backends kopieren.
3. Das Netzwerk vollständig neu starten. Modern Forwarding muss funktionieren; Backends dürfen nur über den vertrauenswürdigen Proxy erreichbar sein.
4. Bei einem Update von 2.1 wird die Paper-Konfiguration unter `config-v2.1-backup.yml` gesichert. Automatische Servererkennung, neue Schriftgröße und HUD-Hinweis werden aktiviert; eigene Servernamen, Ziele und Rechte bleiben erhalten. Der zusätzliche Hotbar-Gegenstand wird deaktiviert.
5. Im GitHub-Standardmodus wird die unveränderte Paket-ZIP aus 2.1 auf dem Proxy gesichert und durch die neue eingebettete ZIP ersetzt. Eigene Paketdateien werden nicht überschrieben. Bei `external` oder `self-host` die neue Release-ZIP selbst als `pack.file` und am eigenen Downloadziel bereitstellen.
6. In der Proxy-Konsole `/serverhubproxy packcheck` ausführen. Erwartet: HTTP 200 und passender SHA-1. Anschließend neu verbinden, damit die neue Paketsitzung beginnt.

Im Standardmodus lädt der Client das Pflichtpaket direkt über HTTPS aus dem versionsgebundenen GitHub-Release. Zusätzlicher HTTP-Port und manuelle Paketadresse sind nicht nötig. Die lokale Proxy-Datei liegt unter `plugins/serverhub-velocity/serverhub-pack.zip`.

## Automatische Serverliste

`server-discovery.enabled: true` ist Standard. Paper fragt den Proxy beim Beitritt und etwa alle fünf Sekunden nach der Liste. Ein erneutes Öffnen oder Blättern verwendet den zuletzt erhaltenen Stand. Ein bereits offener Dialog wird durch Abgleiche nicht unaufgefordert neu geöffnet.

Es werden bis zu 256 bei Velocity registrierte Ziele mit Namen aus Buchstaben, Zahlen, Punkt, Unterstrich und Bindestrich angezeigt, maximal 64 Zeichen pro Ziel. Die Liste ist alphabetisch sortiert. Registriert bedeutet nicht zwingend online: ein gerade abgeschaltetes Backend kann weiterhin in Velocity registriert und damit sichtbar sein. Verbindungsfehler werden gemeldet.

Ohne Anpassungen genügt auf Paper:

```yaml
server-discovery:
  enabled: true
servers: {}
```

Einträge unter `servers` sind optionale Anpassungen für ein registriertes Ziel:

```yaml
servers:
  lobby:
    target: lobby
    name: 'Treffpunkt'
    color: '#A8E091'
    font-size: 12
    description: ['Die gemeinsame Lobby']
  test:
    target: testserver
    name: 'Testserver'
    permission: serverhub.test
  intern:
    target: internal
    hidden: true
```

Nicht mehr registrierte Ziele erscheinen im Automatikmodus nicht. Ein ausdrückliches `false` für `serverhub.connect.<target>` am Proxy blendet das Ziel bereits vor der Übertragung aus und sperrt auch die Verbindung. Paper prüft zusätzlich optionale Eintragsrechte. Es werden nur Zielnamen übertragen, keine Backend-Adressen oder Ports. Die Nachrichten sind an die jeweilige Backend-Sitzung gebunden und werden niemals an Clients weitergeleitet.

`server-discovery.enabled: false` verwendet ausschließlich die manuell eingetragenen Ziele. Änderungen mit `/extramenü reload` laden und das Menü erneut öffnen. Konfigurations-Reloads entwerten alte Menüaktionen, damit sie keine veralteten Ziele mehr auslösen.

## Menü und HUD

**Shift + F** öffnet das Menü. Bei umbelegtem Handtausch gilt die neue Handtausch-Taste. Alternativ `/extramenü`, `/extramenu`, `/extramen` oder `/serverhub`. Der Hinweis zeigt die angefragte Standardkombination; der Client übermittelt dem Plugin keine individuellen Tastenbelegungen.

Der Dialog enthält zwei Spalten mit bis zu sechs Servern pro Seite. `Zurück` und `Weiter` blättern, `Schließen` und Escape führen zurück ins Spiel. Lange Namen werden passend gekürzt; der Tooltip enthält den vollständigen Namen und die Beschreibung. Das Öffnen schließt zuvor offene Container, damit nach dem Dialog kein altes Inventar wieder erscheint. Inventarinhalte werden für das Menü nicht verändert.

Das HUD-Symbol wird als verschobene Actionbar-Fontgrafik angezeigt und jede Sekunde erneuert. Die Position bezieht sich auf die Bildschirmmitte und skaliert mit der normalen GUI-Skalierung:

```yaml
shift-f-hint:
  enabled: true
  interval-seconds: 1
  x-offset: 106
  y-offset: 0
```

`x-offset` gibt GUI-Pixel rechts der Mitte an (94–512), positive `y-offset`-Werte verschieben nach unten (−32 bis 32). Die Standardposition ist für die normale Java-Hotbar vorgesehen. HUD-Mods, ungewöhnliche Skalierungen oder der Hotbar-Angriffsindikator können eine Anpassung erfordern. F1 blendet die normale HUD-Ausgabe aus. Andere Plugins, die ebenfalls Actionbars senden, können den Hinweis vorübergehend überschreiben; Minecraft stellt dafür keinen eigenen zusätzlichen HUD-Kanal bereit.

Die Grafik ist ein Tastaturhinweis, keine frei anklickbare HUD-Schaltfläche. Die native Dialog-API steuert weiterhin das Verhalten von Fokus, Escape und Tooltips. Es werden keine globalen Minecraft-Menütexturen oder Shader ersetzt. Ein beliebig programmierbares eigenes Client-Fenster würde einen Client-Mod erfordern.

Ein zusätzlicher Rechtsklick-Gegenstand **in** der Hotbar ist auf Wunsch weiter möglich:

```yaml
hotbar-icon:
  enabled: true
  slot: 8
```

Er ist standardmäßig aus. Vorhandene Gegenstände werden bei Aktivierung nur in einen freien normalen Inventarplatz umgelagert. Bei vollem Inventar wird kein Gegenstand überschrieben. Shift+F und der HUD-Hinweis funktionieren unabhängig davon.

## Paketpflicht und Download

Die Proxy-Sitzung bleibt vom ersten Backend-Beitritt bis zum echten Verbindungsende bestehen. Backend-Wechsel, Handshakes und Reloads versenden kein weiteres ServerHub-Paketangebot. Ein Wiederbeitritt beginnt eine neue Sitzung; der Minecraft-Cache kann die identische ZIP wiederverwenden.

Nur die erfolgreiche Anwendung des genau zugehörigen Pakets schaltet das Menü frei. Ablehnung, Fehler, Verwerfen oder Timeout führen zum Kick. Andere Plugins können eigene Paketangebote senden; ServerHub entfernt deren Pakete nicht. Änderungen an Paketdatei oder URL gelten für neue Verbindungen.

Proxy-Einstellungen: `plugins/serverhub-velocity/config.properties`.

| Modus | Verhalten |
|---|---|
| `pack.mode=github` | Standard: exakte mitgelieferte ZIP aus dem HTTPS-Release. |
| `pack.mode=self-host` | Eigener HTTP-Server auf dem Proxy; öffentlichen Host und Port erreichbar machen. |
| `pack.mode=external` | Eigene direkte HTTP(S)-ZIP-Adresse unter `pack.external-url`. |

Beispiel für Selbsthosting:

```properties
pack.mode=self-host
self-host.bind=0.0.0.0
self-host.port=8124
self-host.public-host=auto
self-host.scheme=http
self-host.advertised-port=8124
```

TCP-Port 8124 muss zum Velocity-Host freigegeben sein. `auto` verwendet die öffentliche Beitrittsadresse. Bei SRV, Tunnels oder NAT einen festen öffentlichen Host und den tatsächlich erreichbaren Port setzen. IPv6 wird unterstützt. HTTPS beim Selbsthosting benötigt einen vorgeschalteten TLS-Proxy. Änderungen an HTTP-Bind, Port oder Aktivierung erfordern einen Neustart.

`/serverhubproxy packcheck` folgt Weiterleitungen und prüft HTTP-Code sowie den vollständigen SHA-1 vom Proxy aus. HTML-Seiten oder abweichende ZIP-Bytes werden abgewiesen. Das bestätigt die Erreichbarkeit vom Proxy, nicht von jedem Spieleranschluss.

## Befehle und Rechte

| Aktion | Befehl / Recht |
|---|---|
| Menü öffnen | `/extramenü`; `serverhub.use` (Standard alle) |
| Paper-Konfiguration laden | `/extramenü reload`; `serverhub.reload` (Standard OP) |
| Proxy-Konfiguration laden | `/serverhubproxy reload`; `serverhub.proxy.reload` |
| Proxy-Status / Download prüfen | `/serverhubproxy status` / `packcheck`; gleiches Proxy-Recht |
| Ein Ziel sperren | Am Proxy `serverhub.connect.<target>` ausdrücklich auf `false` setzen |

## Build und Prüfung

Java 25, Maven 3.9+ und Python 3:

```sh
python3 tools/package_pack.py
mvn -B clean verify
python3 tools/prepare_release.py
```

Die installierbare Datei liegt in `universal/target/ServerHub-2.2.0.jar`. Modul-JARs sind interne Zwischenergebnisse. Normale Builds verpacken die eingecheckten Grafiken unverändert. Neugenerierung: Pillow installieren, dann `python3 tools/build_pack.py` und `python3 tools/render_preview.py`.

GitHub Actions prüft Protokollgrenzen, dynamische Serverlisten und Rechte, Konfigurations-/Paketmigration, Paketlebenszyklus, HTTP-Weiterleitungen und die gepackte JAR mit getrennten Plattform-Klassenladern. Auf `main` wird das bezeichnete Release mit JAR, Paket-ZIP, Prüfsummen, Prüfbericht, Komplettpaket und Designreferenz veröffentlicht. Danach werden die öffentlichen JAR-/ZIP-Downloads einschließlich Weiterleitungen, SHA-1, SHA-256 und eingebetteten Paketbytes geprüft. Bestehende Releases werden nicht überschrieben.

Die Vorschau ist eine Designreferenz aus den echten Texturen, kein Screenshot eines verbundenen Minecraft-Clients. Darstellung, native Dialogausrichtung, HUD-Position und Zusammenspiel mit Fremdplugins müssen im Spiel geprüft werden. Bedrock/Geyser und abweichende Spielversionen sind nicht geprüft.

## Grafik und API

Die Holztextur stammt aus der bereitgestellten Vorlage. Die neue Pergamentrolle wird aus dem eingecheckten Generator aufgebaut. Die Schrift ist ein Latin-1-Subset von DejaVu Sans Bold; Lizenz liegt bei. Die frühere kondensierte Schrift und Stein-Grafikquelle bleiben als Ausgangsmaterial im Repository erhalten.

- [Paper Dialog-API](https://docs.papermc.io/paper/dev/dialogs/)
- [Velocity ProxyServer](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/ProxyServer.html)
- [Velocity Plugin Messaging](https://docs.papermc.io/velocity/dev/plugin-messaging/)
