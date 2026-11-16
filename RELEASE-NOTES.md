# ServerHub 2.1.0

- **Eine Universal-JAR:** dieselbe `ServerHub-2.1.0.jar` auf Velocity und allen Paper-Backends verwenden. Beide Plattformen wählen ihren passenden Einstiegspunkt selbst.
- **Download über HTTPS:** das Menüpaket wird standardmäßig direkt aus diesem GitHub-Release geladen. Kein zusätzlicher offener HTTP-Port und keine selbst einzutragende URL erforderlich.
- **Genau ein ServerHub-Paketangebot pro Netzwerkbeitritt:** Backend-Wechsel, Paper-Handshakes und Reloads lösen keinen weiteren Download aus. Nach dem Trennen und erneuten Verbinden wird eine neue Sitzung angelegt; der Minecraft-Cache kann die vorhandene ZIP wiederverwenden.
- **Migration:** alte 2.0-HTTP-Einstellungen werden in `config-v2.0-backup.properties` gesichert und auf den GitHub-Modus umgestellt. Menü-/Serverkonfiguration auf Paper bleibt erhalten.
- **Diagnose:** `/serverhubproxy packcheck` prüft HTTP-Status, Weiterleitungen und den vollständigen SHA-1 vom Proxy aus. Downloadfehler protokollieren Status und Adresse.
- **Alternativen:** `self-host` unterstützt automatische öffentliche Host-Erkennung, IPv6, einen separaten öffentlichen Port und Hash-Adressen; `external` erlaubt eine eigene direkte ZIP-URL.
- Pflichtpaket, Stein-/Holzmenü, Shift+F, Hotbar-Icon, Hexfarben und konfigurierbare Serverauswahl bleiben enthalten.

**Installation:** alte ServerHub-JARs entfernen, diese eine JAR auf Proxy und Backends kopieren und das Netzwerk vollständig neu starten. Die Paket-ZIP muss im Standardmodus nicht manuell installiert werden. Paper 26.3, Velocity 4.2.0, Java 25.

**Prüfung:** automatisierte Tests einschließlich Backend-Wechsel, Reload, Wiederbeitritt, HTTP-Weiterleitungen und Plattform-Isolation. Kein vollständiger Spieltest mit verbundenem Minecraft-Client; die öffentliche Release-ZIP wird nach Veröffentlichung separat auf ihren Hash geprüft.

Andere Plugins können eigene Paketangebote senden. ServerHub entfernt oder ersetzt deren Pakete nicht; ExtraItems muss für sein eigenes netzwerkweites Verhalten separat konfiguriert werden.
