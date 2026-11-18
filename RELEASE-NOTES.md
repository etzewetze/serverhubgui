ServerHub 2.5.3 macht die Serverliste übersichtlicher und übernimmt den optionalen Portal-Upload aus Pull Request #1 mit zusätzlichen Prüfungen.

- **Getrennte Dateien:** `servers.yml` enthält nur aktive, registrierte Server. Allgemeine Menü-, Schrift-, Status-, HUD- und Meldungseinstellungen liegen in `config.yml`. Ausgeblendete oder entfernte Ziele bleiben dort unter `inactive-servers` erhalten und lassen sich wiederherstellen. Die bisherigen Dateien werden automatisch mit Sicherung migriert. Pack- und Portal-Einstellungen bleiben in `config.properties`; AMP und News behalten ihre eigenen Dateien.
- **Sortierung:** `sort: 1`, `sort: 2`, ... bestimmt die numerische Reihenfolge. Gleiche Werte werden alphabetisch aufgelöst. Bisheriges `order` wird übernommen; ein ausdrücklich gesetztes `sort` hat Vorrang. Neue Ziele werden hinten angefügt.
- **Banner:** Höhe von 32 auf 30 GUI-Pixel reduziert. Die sichtbaren Ober- und Unterkanten schließen an beiden Enden bündig am Mittelstreifen an.
- **Optionaler Portal-Uploader:** HTTPS-Upload der erzeugten ZIP vor der Freigabe für neue Spieler. API-Schlüssel aus einer Umgebungsvariable. SHA-1-Prüfung, begrenzte Antworten, geschützte Statusdatei und getrennte Dateinamen je Packhash. Alte aktive Downloadlinks werden durch weitere Uploads nicht ersetzt. Fehlgeschlagene Uploads lassen die bisher aktive Packkonfiguration bestehen. Der Upload ist standardmäßig ausgeschaltet.

Beispiel in `servers.yml`:

```yaml
servers:
  lobby:
    name: Lobby
    sort: 1
  survival:
    name: Survival
    sort: 2
```

Danach `/serverhubproxy reload` ausführen. Für den Portal-Uploader wird die separat bereitgestellte Down-&-Up-PHP-App benötigt; Einrichtung und Umgebungsvariable stehen in der README. Die Integration ist gegen Protokoll-Fixtures geprüft, nicht gegen die konkrete Portal-Installation.

**Update:** Dieselbe `ServerHub-2.5.3.jar` auf Proxy und Paper-Backends installieren, neu starten und für das geänderte Pack neu verbinden. Vorhandene Einstellungen, News, AMP-Zugangsdaten und eigene Packdateien bleiben erhalten. Das Original der bisherigen Serverdatei wird als `servers-v2.5.2-backup.yml` gesichert. Zum Wiedereinblenden eines archivierten Servers in `config.yml` unter `inactive-servers` dessen `hidden` auf `false` setzen und sicherstellen, dass Velocity das Ziel registriert hat.

Java 25, Minecraft Java/Paper 26.3, Velocity 4.2/4.3; ServerNavigator ab 1.2.0. Kein vollständiger Ingame-Test.
