package dev.vankka.dsrvdownloader;

import io.javalin.Javalin;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DiscordSRVDownloader {

    private static final SimpleDateFormat RFC822_FORMATTER = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'");

    public static void main(String[] args) {
        new DiscordSRVDownloader(args);
        RFC822_FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private String releaseUrl;
    private String releaseVersion;
    private File releaseFile;

    private String snapshotHash;
    private long snapshotLastChecked = 0L;
    private File snapshotFile;
    private String snapshotBuild;
    private String previousSnapshotHash;

    private final String discordWebhookUrl;

    public DiscordSRVDownloader(String[] args) {
        String secret = System.getenv("GITHUB_WEBHOOK_SECRET");
        if (secret == null || secret.isEmpty()) {
            if (args.length < 1) {
                System.err.println("The github webhook secret must be provided");
                System.exit(1);
            }
            secret = args[0];
        }

        String portEnv = System.getenv("DOWNLOADER_PORT");
        int port = 3829;
        if (portEnv != null && !portEnv.isEmpty()) {
            port = Integer.parseInt(portEnv);
        } else if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        Javalin javalin = Javalin.create().start(port);

        String webhookUrl = System.getenv("DISCORD_WEBHOOK_URL");
        if ((webhookUrl == null || !webhookUrl.isEmpty()) && args.length > 2) {
            webhookUrl = args[2];
        }

        this.discordWebhookUrl = webhookUrl;

        String finalSecret = secret;

        javalin.post("/github-webhook", ctx -> {
            try {
                byte[] bytes = hmac256(finalSecret.getBytes(StandardCharsets.UTF_8), ctx.body().getBytes(StandardCharsets.UTF_8));
                if (bytes == null) {
                    ctx.status(401);
                    return;
                }
                String signature = "sha256=" + hex(bytes);
                if (!signature.equals(ctx.header("X-Hub-Signature-256"))) {
                    ctx.status(401);
                    return;
                }

                String event = ctx.header("X-GitHub-Event");
                if (event == null) {
                    ctx.status(300);
                    return;
                }

                JSONObject jsonObject = new JSONObject(ctx.body());
                if (event.equals("check_suite")) {
                    JSONObject checkSuite = jsonObject.getJSONObject("check_suite");
                    if (checkSuite.getString("status").equals("completed") && checkSuite.getString("head_branch").equals("develop")) {
                        String hash = checkSuite.getJSONObject("head_commit").getString("id");

                        String conclusion = checkSuite.getString("conclusion");
                        if (conclusion.equals("success")) {
                            snapshotHash = hash;
                            System.out.println("New snapshot has via check suite success: " + snapshotHash);
                        } else {
                            System.out.println("Check suite gave a non-success completion: " + conclusion);
                            JSONObject webhookJson = new JSONObject();
                            webhookJson.put("content", "Check suite completed with non-success status, commit: `" + hash + "`, conclusion: `" + conclusion + "`");
                            postRequest(discordWebhookUrl, webhookJson);
                        }
                    }
                } else if (event.equals("release")) {
                    if (jsonObject.getString("action").equals("published")) {
                        System.out.println("New release publish detected");
                        for (Object obj : jsonObject.getJSONArray("assets")) {
                            JSONObject asset = (JSONObject) obj;
                            releaseUrl = asset.getString("browser_download_url");
                            releaseVersion = jsonObject.getString("tag_name").substring(1);
                            break;
                        }
                    }
                } else if (!event.equals("ping")) {
                    ctx.status(400);
                    ctx.result("Only check_suite, release and ping are accepted");
                    return;
                }

                ctx.status(204);
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });

        javalin.get("/:type", ctx -> {
            File file;
            switch (ctx.pathParam("type").toLowerCase()) {
                case "release":
                    file = releaseFile;
                    break;
                case "snapshot":
                    file = snapshotFile;
                    break;
                default:
                    ctx.status(404);
                    return;
            }
            if (file != null && file.exists()) {
                ctx.header("content-disposition", "attachment; filename=\"" + file.getName() + "\"");
                try (InputStream inputStream = new FileInputStream(file)) {
                    try (OutputStream outputStream = ctx.res.getOutputStream()) {

                        IOUtils.copy(inputStream, outputStream);
                    }
                }
            } else {
                ctx.status(503); // Service unavailable while we try get the file
            }
        });

        new Timer("DiscordSRVDownloader BackgroundWorker").scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                loop();
            }
        }, 0, TimeUnit.SECONDS.toMillis(5));
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

    private String hex(byte[] bytes) {
        try {
            final char[] hexArray = "0123456789abcdef".toCharArray();
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0, v; j < bytes.length; j++) {
                v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loop() {
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

        if (releaseVersion != null && releaseUrl != null && (releaseFile == null || !releaseFile.exists())) {
            String releaseFileName = "DiscordSRV-Build-" + releaseVersion + ".jar";
            File file = new File(storage, releaseFileName);
            if (file.exists()) {
                releaseFile = file;
            } else {
                boolean success = getToFile(releaseUrl, file);
                if (success) {
                    releaseFile = file;

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("content", "Release `" + releaseVersion + "` is now available");
                    postRequest(discordWebhookUrl, jsonObject);
                } else {
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
            System.out.println("Getting snapshot artifact: " + snapshotFileName);

            File file = new File(storage, snapshotFileName);
            if (file.exists()) {
                snapshotFile = file;
                previousSnapshotHash = snapshotHash;
                return;
            }

            boolean success = getToFile("https://nexus.scarsz.me/service/local/artifact/maven/redirect?r=snapshots&g=com.discordsrv&a=discordsrv&v=LATEST", file);
            if (success) {
                boolean webhook = snapshotFile != null;
                snapshotFile = file;
                previousSnapshotHash = snapshotHash;

                if (webhook) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("content", "Snapshot for commit `" + snapshotHash + "` is now available");
                    postRequest(discordWebhookUrl, jsonObject);
                }
            } else {
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
                if (!file.getAbsolutePath().equals(snapshotFile.getAbsolutePath()) && !file.getAbsolutePath().equals(releaseFile.getAbsolutePath())) {
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

    private void postRequest(String plainUrl, JSONObject body) {
        try {
            URL url = new URL(plainUrl);

            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.addRequestProperty("User-Agent", "DiscordSRV Downloader/1.0");
            httpsURLConnection.addRequestProperty("Content-Type", "application/json");
            httpsURLConnection.setDoOutput(true);
            httpsURLConnection.setRequestMethod("POST");

            try (StringReader stringReader = new StringReader(body.toString())) {
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
