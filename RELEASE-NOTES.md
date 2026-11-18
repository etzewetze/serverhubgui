ServerHub 2.5.2 behebt die gemeldeten Probleme mit Rollenkanten, News und ausgeschalteten Servern.

- **Rolle bündig:** Die linke und rechte Dekoration sitzen drei GUI-Pixel tiefer. Der Mittelstreifen und der feste Titel behalten ihre Position; die Papierkanten schließen oben ohne Versatz an.
- **AMP-Konsole:** Nach gültiger Anmeldung erscheint `AMP: Anmeldung erfolgreich.`. Danach wird die Zahl der gefundenen Serverinstanzen mit Anwendungsstatus ausgegeben. Änderungen der Anzahl werden gemeldet, unveränderte Statusabfragen erzeugen keinen Logspam. Fehler bei der Instanzabfrage bleiben separat sichtbar.
- **Kein Countdown für ausgeschaltete Ziele:** Der Proxy prüft den Backend-/AMP-Status vor dem ServerNavigator-Aufruf. Bei OFFLINE/Rot wird weder ein neuer Navigator-Auftrag noch ein Direktwechsel gestartet. Auch ein grüner Minecraft-Cache-Ping überstimmt AMP-Stopped nicht. Eine laufende Navigator-Phase überdeckt keinen roten Backendstatus. Schlafende gelbe Ziele bleiben über Navigator weckbar.
- **News aus der Lobby:** Das auf Paper geprüfte Recht `serverhub.news` gilt auch dann, wenn am Proxy kein eigenes Newsrecht gesetzt ist. Ein ausdrückliches Proxy-Verbot bleibt bestehen. Die Nachricht muss vom aktuellen Backend mit gültiger Sitzungskennung kommen. News sind von Verbindungsrechten getrennt und starten keinen Teleport. Fehler zeigen eigene News-Meldungen statt „Der Proxy hat den Wechsel nicht erlaubt“.

Befehle:

```text
/sh news start Neue Nachricht für den aktuellen Server
/sh news stop
/sh news start --server challenges Neue Challenge verfügbar!
/sh news stop --server challenges
```

**Update:** `ServerHub-2.5.2.jar` auf Proxy und allen Paper-Backends ersetzen und neu starten. Danach neu verbinden, damit das korrigierte Ressourcenpaket geladen wird. Vorhandene Konfigurationen, AMP-Zugangsdaten, Schriften, eigene Packdateien und News bleiben erhalten. Keine neuen AMP-Zugangsdaten oder zusätzlichen Proxyrechte erforderlich.

Java 25, Minecraft Java/Paper 26.3, Velocity 4.2/4.3; ServerNavigator ab 1.2.0. Tests prüfen unter anderem Offline-Pings, AMP-Stopped trotz grünem Ping, weiterhin erlaubte schlafende Ziele, News ohne Proxyrecht, explizite Sperren, fremde Sitzungskennungen und die tatsächlichen Rollenkanten im Pack. Kein vollständiger Ingame-Test und kein Zugriff auf die konkrete AMP-Installation.
