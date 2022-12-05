package dev.vankka.dsrvdownloader.route.v1;

import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.buf.HexUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@RestController
@Deprecated
public class V1Routes {

    private String releaseUrl;
    private String releaseVersion;
    private File releaseFile;

    private String snapshotHash;
    private String snapshotMessage;
    private long snapshotLastChecked = 0L;
    private File snapshotFile;
    private String snapshotBuild;
    private String previousSnapshotHash;

    private final List<String> mostRecentPushes = new CopyOnWriteArrayList<>();

    private final String discordWebhookUrl;
    private final String githubSecret;

    public V1Routes() {
        this.githubSecret = System.getenv("GITHUB_WEBHOOK_SECRET");
        this.discordWebhookUrl = System.getenv("DISCORD_WEBHOOK_URL");

        readyFiles();
    }

    @PostMapping(
            path = "/github-webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<?> githubWebhook(
            @RequestHeader(name = "X-Hub-Signature-256") String signatureHeader,
            @RequestHeader(name = "X-GitHub-Event") String event,
            @RequestBody byte[] body
    ) {
        try {
            byte[] bytes = hmac256(githubSecret.getBytes(StandardCharsets.UTF_8), body);
            if (bytes == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }

            String signature = "sha256=" + HexUtils.toHexString(bytes);
            if (!signature.equals(signatureHeader)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }

            if (event == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }

            JSONObject jsonObject = new JSONObject(body);
            if (event.equals("push")) {
                JSONObject headCommit = jsonObject.getJSONObject("head_commit");
                String id = headCommit.getString("id");
                if (mostRecentPushes.size() > 4) {
                    mostRecentPushes.remove(4);
                }
                mostRecentPushes.add(id);

                return ResponseEntity.noContent().build();
            } else if (event.equals("check_suite")) {
                JSONObject checkSuite = jsonObject.getJSONObject("check_suite");
                if (checkSuite.getString("status").equals("completed") && checkSuite.getString("head_branch").equals("develop")) {
                    JSONObject headCommit = checkSuite.getJSONObject("head_commit");
                    String hash = headCommit.getString("id");
                    if (!mostRecentPushes.contains(hash)) {
                        return ResponseEntity.ok("This check suite's head sha has not been pushed, ignoring");
                    }

                    String conclusion = checkSuite.getString("conclusion");
                    if (conclusion.equals("success")) {
                        snapshotHash = hash;
                        snapshotMessage = headCommit.getString("message");
                        logAndWebhook("New snapshot has been detected via check suite success: " + snapshotHash);
                    } else {
                        System.out.println("Check suite gave a non-success completion: " + conclusion);
                        postToWebhook("**FAILED** Check suite completed with non-success status, commit: `" + hash + "`, conclusion: `" + conclusion + "`");
                        return ResponseEntity.ok("Check suite did not succeed, not downloading new snapshot");
                    }
                } else {
                    return ResponseEntity.ok("Check suite status was non-completed or it was not set to head_branch develop");
                }
            } else if (event.equals("release")) {
                if (jsonObject.getString("action").equals("published")) {
                    JSONObject release = jsonObject.getJSONObject("release");
                    JSONArray assets = release.getJSONArray("assets");
                    logAndWebhook("New release detected, " + assets.length() + " assets");
                    for (Object obj : assets) {
                        JSONObject asset = (JSONObject) obj;
                        releaseUrl = asset.getString("browser_download_url");
                        releaseVersion = release.getString("tag_name").substring(1);
                        logAndWebhook("Release: " + releaseVersion + " (" + releaseUrl + ")");
                        break;
                    }
                }
            } else if (!event.equals("ping")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only check_suite, release, push and ping are accepted");
            }

            readyFiles();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/")
    public View rootRedirect() {
        return new RedirectView("/release");
    }

    @GetMapping(
            path = "/{type}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<?> getJar(@PathVariable String type) throws Exception {
        File file;
        switch (type.toLowerCase()) {
            case "release":
                file = releaseFile;
                break;
            case "snapshot":
                file = snapshotFile;
                break;
            default:
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (file != null && file.exists()) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(new InputStreamResource(new FileInputStream(file)));
        } else {
            // Service unavailable while we try get the file
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private byte[] hmac256(byte[] secretKey, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec sks = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(sks);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final SimpleDateFormat RFC822_FORMATTER = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'");
    private void readyFiles() {
        long currentTime = System.currentTimeMillis();

        File storage = new File("storage");
        if (!storage.exists()) {
            try {
                Files.createDirectory(storage.toPath());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        // (Initially get) Release
        String releaseBody = releaseVersion == null || !releaseFile.exists() ? getBody("https://api.github.com/repos/DiscordSRV/DiscordSRV/releases/latest", new HashMap<>()) : null;

        if (releaseBody != null) {
            JSONObject releaseJson = new JSONObject(releaseBody);
            String tag = releaseJson.getString("tag_name").substring(1);
            if (!tag.equals(releaseVersion)) {
                releaseVersion = tag;

                JSONArray assets = releaseJson.getJSONArray("assets");
                for (Object obj : assets) {
                    JSONObject asset = (JSONObject) obj;
                    releaseUrl = asset.getString("browser_download_url");
                    break;
                }
            }
        }

        // Release jar download

        String releaseFileName = "DiscordSRV-Build-" + releaseVersion + ".jar";
        if (releaseVersion != null && releaseUrl != null
                && (releaseFile == null || !releaseFile.getName().equals(releaseFileName))) {
            File file = new File(storage, releaseFileName);
            if (file.exists()) {
                releaseFile = file;
            } else {
                boolean success = getToFile(releaseUrl, file);
                if (success) {
                    boolean webhook = releaseFile != null; // don't post this if we're getting it after a restart
                    releaseFile = file;

                    if (webhook) {
                        postToWebhook("Release `" + releaseVersion + "` is now available");
                    }
                } else {
                    postToWebhook("**FAILED** to download release from Github");

                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // (Initially get) Snapshot Hash
        String snapshotHashBody = snapshotHash == null || !snapshotFile.exists() ? getBody("https://api.github.com/repos/DiscordSRV/DiscordSRV/git/refs/heads/develop?per_page=1", new HashMap<>()) : null;

        if (snapshotHashBody != null) {
            JSONObject snapshotJson = new JSONObject(snapshotHashBody);
            snapshotHash = snapshotJson.getJSONObject("object").getString("sha");
        }

        // Snapshot version

        if (snapshotLastChecked + TimeUnit.SECONDS.toMillis(10) < currentTime) {
            Map<String, String> snapshotHeaders = new HashMap<>();
            if (snapshotLastChecked > 0) {
                snapshotHeaders.put("If-Modified-Since", RFC822_FORMATTER.format(snapshotLastChecked));
            }
            String snapshotBody = getBody("https://raw.githubusercontent.com/DiscordSRV/DiscordSRV/develop/pom.xml", snapshotHeaders);
            snapshotLastChecked = currentTime;

            if (snapshotBody != null) {
                snapshotBuild = snapshotBody.split("<version>")[1].split("</version>")[0];
                previousSnapshotHash = null;
            }
        }

        // Snapshot jar download

        if (!String.valueOf(previousSnapshotHash).equals(snapshotHash)) {
            String snapshotFileName = "DiscordSRV-Build-" + snapshotBuild + "-" + snapshotHash.substring(0, 7) + ".jar";

            File file = new File(storage, snapshotFileName);
            if (file.exists()) {
                snapshotFile = file;
                previousSnapshotHash = snapshotHash;
                return;
            }

            postToWebhook("Downloading snapshot artifact `" + snapshotFileName + "`...");
            boolean success = getToFile("https://nexus.scarsz.me/service/local/artifact/maven/redirect?r=snapshots&g=com.discordsrv&a=discordsrv&v=LATEST", file);
            if (success) {
                boolean webhook = snapshotFile != null; // don't send if we're getting this after a restart
                snapshotFile = file;
                previousSnapshotHash = snapshotHash;

                if (webhook) {
                    postToWebhook("New Snapshot for commit: `" + snapshotMessage.replace("`", "\\`") + "` (`" + snapshotHash + "`) is now available");
                }
            } else {
                postToWebhook("**FAILED** to download snapshot from nexus (" + snapshotHash + ")");

                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Delete files that are no longer required
        File[] files = storage.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) continue; // v2
                if (!file.getAbsolutePath().equals(snapshotFile.getAbsolutePath())
                        && !file.getAbsolutePath().equals(releaseFile.getAbsolutePath())) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String getBody(String plainUrl, Map<String, String> headers) {
        try {
            URL url = new URL(plainUrl);

            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.addRequestProperty("User-Agent", "DiscordSRV Downloader/1.0");
            headers.forEach(httpsURLConnection::setRequestProperty);
            httpsURLConnection.setDoOutput(true);

            if (httpsURLConnection.getResponseCode() == 304) {
                return null;
            }

            try (StringWriter stringWriter = new StringWriter()) {
                try (InputStream inputStream = httpsURLConnection.getInputStream()) {
                    IOUtils.copy(inputStream, stringWriter, StandardCharsets.UTF_8);
                }
                return stringWriter.toString();
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean getToFile(String plainUrl, File file) {
        try {
            URL url = new URL(plainUrl);

            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.addRequestProperty("User-Agent", "DiscordSRV Downloader/1.0");
            httpsURLConnection.addRequestProperty("Accept", "application/*");
            httpsURLConnection.setDoInput(true);

            if (!file.exists()) {
                Files.createFile(file.toPath());
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                try (InputStream inputStream = httpsURLConnection.getInputStream()) {
                    IOUtils.copy(inputStream, fileOutputStream);
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void logAndWebhook(String message) {
        System.out.println(message);
        postToWebhook(message);
    }

    private void postToWebhook(String content) {
        try {
            String webhookUrl = discordWebhookUrl;
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                return;
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("content", content);

            URL url = new URL(webhookUrl);

            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.addRequestProperty("User-Agent", "DiscordSRV Downloader/1.0");
            httpsURLConnection.addRequestProperty("Content-Type", "application/json");
            httpsURLConnection.setDoOutput(true);
            httpsURLConnection.setRequestMethod("POST");

            try (StringReader stringReader = new StringReader(jsonObject.toString())) {
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpsURLConnection.getOutputStream())) {
                    IOUtils.copy(stringReader, outputStreamWriter);
                }
            }

            System.out.println("Received response for webhook send " + httpsURLConnection.getResponseCode() + " / " + httpsURLConnection.getResponseMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
