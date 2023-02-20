package dev.vankka.dsrvdownloader.model.channel;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.SecurityConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.discord.DiscordMessage;
import dev.vankka.dsrvdownloader.discord.DiscordWebhook;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.VersionCheck;
import dev.vankka.dsrvdownloader.model.VersionResponse;
import dev.vankka.dsrvdownloader.util.UrlUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractVersionChannel implements VersionChannel {

    protected final ConfigManager configManager;
    protected final DiscordWebhook discordWebhook;
    protected final VersionChannelConfig config;
    protected final Map<String, Version> versions;
    protected final List<Version> versionsInOrder;
    protected final Map<String, DiscordMessage> messages;

    public AbstractVersionChannel(ConfigManager configManager, DiscordWebhook discordWebhook, VersionChannelConfig config) {
        this.configManager = configManager;
        this.discordWebhook = discordWebhook;
        this.config = config;
        this.versions = new ConcurrentHashMap<>();
        this.versionsInOrder = new CopyOnWriteArrayList<>();
        this.messages = new ConcurrentHashMap<>();
    }

    public void cleanupDirectory(boolean ignoreVersions) {
        try {
            Path store = store();

            try (Stream<Path> folders = Files.list(store)) {
                for (Path folder : folders.toList()) {
                    List<Path> toDelete;
                    try (Stream<Path> files = Files.list(folder)) {
                        toDelete = files
                                .filter(path -> ignoreVersions || versions.values().stream()
                                        .noneMatch(ver -> ver.getArtifactsByIdentifier().values()
                                                .stream()
                                                .anyMatch(art -> path.equals(art.getFile()) || path.equals(art.getMetaFile()))
                                        )
                                )
                                .collect(Collectors.toList());
                    }
                    for (Path path : toDelete) {
                        Files.delete(path);
                    }
                }
            }

            try (Stream<Path> folders = Files.list(store)) {
                folders.filter(folder -> {
                    try (Stream<Path> fileStream = Files.list(folder)) {
                        return fileStream.findAny().isEmpty();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to cleanup directory of " + describe(), e);
        }
    }

    protected String describe() {
        return repo() + ":" + config.name();
    }

    protected String repo() {
        return config.repoOwner() + "/" + config.repoName();
    }

    protected String baseRepoApiUrl() {
        return Downloader.GITHUB_URL + "/repos/" + repo();
    }

    protected Path store() throws IOException {
        Path path = Paths.get("storage", config.repoOwner(), config.repoName(), config.name());
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    protected void putVersion(Version version, boolean newVersion) {
        versions.put(version.getIdentifier(), version);
        if (newVersion) {
            versionsInOrder.add(0, version);
            newVersionAvailable(version);
        } else {
            versionsInOrder.add(version);
        }
    }

    @Override
    public VersionChannelConfig getConfig() {
        return config;
    }

    @Override
    public Map<String, Version> versionsByIdentifier() {
        return Collections.unmodifiableMap(versions);
    }

    @Override
    public Version latestVersion() {
        return versionsInOrder.isEmpty() ? null : versionsInOrder.get(0);
    }

    @Override
    public String getUrl(HttpServletRequest request) {
        return UrlUtil.getUrl(request) + "/v2/" + repo() + "/" + config.name();
    }

    private void securityCheck(
            List<String> securityFailures,
            AtomicBoolean vulnerability,
            List<SecurityConfig> securityConfigs,
            Predicate<SecurityConfig> check
    ) {
        for (SecurityConfig securityConfig : securityConfigs) {
            if (check.test(securityConfig)) {
                securityFailures.add(securityConfig.securityFailReason());
                if (securityConfig.vulnerability()) {
                    vulnerability.set(true);
                }
            }
        }
    }

    @Override
    public VersionCheck checkVersion(String comparedTo) {
        List<String> securityFailures = new ArrayList<>();
        AtomicBoolean vulnerability = new AtomicBoolean(false);
        List<SecurityConfig> securityConfigs = config.security() != null ? config.security() : Collections.emptyList();

        securityCheck(securityFailures, vulnerability, securityConfigs, config -> config.versionIdentifier().equals(comparedTo));

        int versionsBehind = versionsBehind(comparedTo, version ->
                securityCheck(securityFailures, vulnerability, securityConfigs, config -> {
                    String prefix = "<=";
                    String versionIdentifier = config.versionIdentifier();
                    return versionIdentifier.startsWith(prefix) && version.equals(versionIdentifier.substring(prefix.length()));
                })
        );

        VersionCheck versionCheck = new VersionCheck();
        if (versionsBehind == -1) {
            versionCheck.status = VersionCheck.Status.UNKNOWN;
        } else {
            versionCheck.status = versionsBehind == 0
                                  ? VersionCheck.Status.UP_TO_DATE
                                  : VersionCheck.Status.OUTDATED;
        }
        versionCheck.amount = versionsBehind;
        versionCheck.amountSource = VersionCheck.AmountSource.GITHUB;
        versionCheck.amountType = amountType(versionsBehind);

        versionCheck.securityIssues = securityFailures;
        versionCheck.insecure = vulnerability.get();

        return versionCheck;
    }

    protected abstract int versionsBehind(String comparedTo, Consumer<String> versionConsumer);
    protected abstract String amountType(int amount);

    @Override
    public VersionResponse versionResponse(HttpServletRequest request, boolean preferIdentifier) {
        String baseUrl = getUrl(request) + "/download/";

        List<VersionResponse.Version> versions = new ArrayList<>();
        for (Version version : versionsInOrder) {
            if (version.getExpiry() != null) {
                continue;
            }

            Map<String, VersionResponse.Artifact> artifacts = new LinkedHashMap<>();
            for (Map.Entry<String, Artifact> artifactEntry : version.getArtifactsByIdentifier().entrySet()) {
                String artifactIdentifier = artifactEntry.getKey();
                Artifact artifact = artifactEntry.getValue();


                VersionResponse.Hashes hashes = new VersionResponse.Hashes(
                        artifact.getSha256()
                );

                String url = baseUrl + version.getIdentifier() + "/" + (preferIdentifier ? artifactIdentifier : artifact.getFileName());
                artifacts.put(artifact.getIdentifier(), new VersionResponse.Artifact(
                        artifact.getFileName(),
                        artifact.getSize(),
                        url,
                        hashes
                ));
            }

            versions.add(new VersionResponse.Version(
                    version.getIdentifier(),
                    version.getDescription(),
                    artifacts
            ));
        }

        return new VersionResponse(baseUrl + "latest/", versions);
    }

    protected void expireOldestVersion() {
        int versionsToKeep = config.versionsToKeep();
        if (versionsInOrder.size() > versionsToKeep) {
            Version version = versionsInOrder.get(versionsToKeep);
            if (version == null) {
                return;
            }

            version.expireIn(System.currentTimeMillis() + EXPIRE_AFTER);
        }
    }

    @Override
    public void removeExpiredVersions() {
        List<Version> versionsToRemove = new ArrayList<>();
        List<String> identifiersToRemove = new ArrayList<>();
        long time = System.currentTimeMillis();

        for (Version version : versionsInOrder) {
            if (version.getExpiry() != null && version.getExpiry() < time) {
                versionsToRemove.add(version);
                identifiersToRemove.add(version.getIdentifier());
            }
        }

        versionsInOrder.removeAll(versionsToRemove);
        identifiersToRemove.forEach(versions::remove);

        for (Version version : versionsToRemove) {
            try {
                Path parent = null;
                for (Artifact artifact : version.getArtifactsByIdentifier().values()) {
                    Path file = artifact.getFile();
                    Files.delete(file);
                    Files.deleteIfExists(artifact.getMetaFile());
                    parent = file.getParent();
                }
                if (parent != null && !parent.equals(store())) {
                    Files.deleteIfExists(parent);
                }
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to delete expired version " + version.getIdentifier() + " for " + describe(), e);
            }
        }
    }

    protected void waiting(String identifier, String description, String waitReason) {
        setDiscordMessage(identifier, "\uD83D\uDD51 Waiting for `" + identifier + "` " + waitReason
                + " (`" + description + "`) [`" + describe() + "`]", null);
    }

    protected void processing(String identifier, String description) {
        setDiscordMessage(identifier, "\uD83D\uDD01 Processing `" + identifier + "` "
                + "(`" + description + "`) [`" + describe() + "`]", null);
    }

    protected void failed(String identifier, String description, String failReason) {
        failed(identifier, description, failReason, null);
    }

    protected void failed(String identifier, String description, String failReason, String longerMessage) {
        setLastDiscordMessage(identifier, "❌ Failed to include `" + identifier + "` "
                + "(`" + description + "`) because: \"" + failReason + "\" [`" + describe() + "`]", longerMessage);
    }

    protected void success(String identifier, String description) {
        setLastDiscordMessage(identifier, "✅ Successfully included `" + identifier + "` "
                + "(`" + description + "`) [`" + describe() + "`]", null);
    }

    protected void newVersionAvailable(Version version) {
        discordWebhook.processMessage(new DiscordMessage().setMessage(
                "\uD83D\uDCE5 New version "
                        + "`" + version.getIdentifier() + "` "
                        + "(`" + version.getDescription() + "`) is available [`" + describe() + "`]", null));
    }

    private void setDiscordMessage(String identifier, String message, String longerMessage) {
        discordWebhook.processMessage(
                messages.computeIfAbsent(identifier, key -> new DiscordMessage())
                        .setMessage(message, longerMessage)
        );
    }

    protected void setLastDiscordMessage(String identifier, String message, String longerMessage) {
        DiscordMessage discordMessage = messages.remove(identifier);
        if (discordMessage == null) {
            discordWebhook.processMessage(new DiscordMessage().setMessage(message, longerMessage));
            return;
        }

        discordWebhook.processMessage(discordMessage.setMessage(message, longerMessage));
    }
}
