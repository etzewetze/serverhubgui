package de.serverhub.velocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.serverhub.common.PackData;
import de.serverhub.common.PackUrls;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

/** Publishes the exact locally built ZIP before the new pack settings become visible. */
final class PortalPublisher {
    private static final int CHUNK = 2 * 1024 * 1024;
    private PortalPublisher() {}

    static String publish(Path directory, Properties config, PackData pack, String filename) throws IOException {
        String base = config.getProperty("pack.portal-url", "").trim().replaceAll("/+$", "");
        URI portal;
        try { portal = URI.create(base); }
        catch (IllegalArgumentException e) { throw new IOException("pack.portal-url ist ungültig", e); }
        if (!"https".equalsIgnoreCase(portal.getScheme()) || portal.getHost() == null ||
            portal.getUserInfo() != null || portal.getFragment() != null || portal.getRawQuery() != null)
            throw new IOException("pack.portal-url muss eine HTTPS-Adresse ohne Zugangsdaten sein");
        int owner, folder;
        try {
            owner = Integer.parseInt(config.getProperty("pack.portal-owner-id", ""));
            folder = Integer.parseInt(config.getProperty("pack.portal-folder-id", "0"));
        } catch (NumberFormatException e) { throw new IOException("Portal-Nutzer-ID oder Ordner-ID ungültig", e); }
        if (owner < 1 || folder < 0) throw new IOException("Portal-Nutzer-ID oder Ordner-ID ungültig");
        String variable = config.getProperty("pack.portal-token-env", "PORTAL_TOKEN").trim();
        if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("pack.portal-token-env ungültig");
        String token = System.getenv(variable);
        if (token == null || !token.matches("[a-f0-9]{64}"))
            throw new IOException("Portal-Zugangsschlüssel fehlt in der Umgebungsvariable " + variable);
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) throw new IOException("Pack-Dateiname ungültig");

        Path stateFile = directory.resolve("portal-upload.properties");
        Properties state = new Properties();
        if (Files.isRegularFile(stateFile)) try (var input = Files.newInputStream(stateFile)) { state.load(input); }
        if (pack.sha1().equals(state.getProperty("sha1")) && base.equals(state.getProperty("portal")) &&
            Integer.toString(owner).equals(state.getProperty("owner")) && filename.equals(state.getProperty("filename"))) {
            String saved = state.getProperty("url", "");
            if (samePortalDownload(portal, saved)) return PackUrls.external(saved);
        }

        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var start = new JsonObject();
            start.addProperty("name", filename); start.addProperty("size", pack.bytes().length);
            start.addProperty("owner", owner); start.addProperty("folder", folder); start.addProperty("sha1", pack.sha1());
            String id = post(client, base, token, "start", start.toString().getBytes(StandardCharsets.UTF_8), "application/json")
                .get("id").getAsString();
            if (!id.matches("[a-f0-9]{32}")) throw new IOException("Portal lieferte keine gültige Upload-ID");
            for (int offset = 0; offset < pack.bytes().length; offset += CHUNK) {
                byte[] block = Arrays.copyOfRange(pack.bytes(), offset, Math.min(pack.bytes().length, offset + CHUNK));
                post(client, base, token, "chunk&id=" + id + "&offset=" + offset, block, "application/octet-stream");
            }
            var finish = new JsonObject(); finish.addProperty("id", id);
            JsonObject completed = post(client, base, token, "complete", finish.toString().getBytes(StandardCharsets.UTF_8), "application/json");
            if (!pack.sha1().equals(completed.get("sha1").getAsString())) throw new IOException("Portal-SHA-1 weicht vom lokalen Paket ab");
            var share = new JsonObject(); share.addProperty("file_id", completed.get("file_id").getAsLong()); share.addProperty("mode", "version");
            String url = post(client, base, token, "share", share.toString().getBytes(StandardCharsets.UTF_8), "application/json")
                .get("url").getAsString();
            if (!samePortalDownload(portal, url)) throw new IOException("Portal lieferte keine gültige Downloadadresse");
            url = PackUrls.external(url);
            Properties next = new Properties();
            next.setProperty("sha1", pack.sha1()); next.setProperty("portal", base);
            next.setProperty("owner", Integer.toString(owner)); next.setProperty("filename", filename);
            next.setProperty("url", url);
            Path temporary = Files.createTempFile(directory, "portal-upload-", ".tmp");
            try {
                try { Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
                catch (UnsupportedOperationException ignored) { /* Other platforms use their default permissions. */ }
                try (var output = Files.newOutputStream(temporary)) { next.store(output, "ServerHub portal share"); }
                try { Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            return url;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Portal-Upload unterbrochen", e);
        } catch (RuntimeException e) { throw new IOException("Portal-Antwort ungültig: " + e.getMessage(), e); }
    }

    private static JsonObject post(HttpClient client, String base, String token, String action, byte[] data, String type)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api.php?action=" + action))
            .timeout(Duration.ofSeconds(120)).header("X-Portal-Token", token).header("Content-Type", type)
            .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Portal-Upload HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(200, response.body().length())));
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Portal-Antwort ist kein JSON-Objekt", e); }
    }

    private static boolean samePortalDownload(URI portal, String raw) {
        try {
            URI url = URI.create(raw);
            String prefix = portal.getPath().replaceAll("/+$", "") + "/download.php";
            return "https".equalsIgnoreCase(url.getScheme()) && portal.getHost().equalsIgnoreCase(url.getHost()) &&
                portal.getPort() == url.getPort() && prefix.equals(url.getPath()) && url.getRawQuery() != null &&
                url.getRawQuery().matches("t=[a-f0-9]{64}") && url.getUserInfo() == null && url.getFragment() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
