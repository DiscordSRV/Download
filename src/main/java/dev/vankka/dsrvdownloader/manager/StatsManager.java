package dev.vankka.dsrvdownloader.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.UserAgent;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("SpellCheckingInspection")
public class StatsManager {

    private final ScheduledExecutorService executorService;
    private final Connection connection;
    private final Map<Artifact, EnumMap<UserAgent, AtomicInteger>> artifacts = new LinkedHashMap<>();

    public StatsManager() throws SQLException, ClassNotFoundException {
        Class.forName("org.h2.Driver");
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.connection = DriverManager.getConnection("jdbc:h2:./stats");

        try (Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists artifact ("
                                      + "artifactid bigint auto_increment,"
                                      + "repoowner character(256),"
                                      + "reponame character(256),"
                                      + "channel character(256),"
                                      + "artifact character(256),"
                                      + "primary key (artifactid)"
                                      + ");");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists stats ("
                                      + "statsid bigint auto_increment,"
                                      + "artifactid bigint,"
                                      + "version character(256),"
                                      + "useragent tinyint default -1,"
                                      + "date date,"
                                      + "count int,"
                                      + "primary key (statsid),"
                                      + "foreign key (artifactid) references artifact(artifactid)"
                                      + ");");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table stats add column if not exists useragent tinyint default -1");
        }

        executorService.scheduleAtFixedRate(this::flushStats, 30, 30, TimeUnit.SECONDS);
    }

    public void increment(VersionChannel versionChannel, String userAgent, String artifactIdentifier, String version) {
        VersionChannelConfig config = versionChannel.getConfig();
        Artifact artifact = new Artifact(config.repoOwner(), config.repoName(), config.name(), artifactIdentifier, version);
        alter(artifact, parseUA(userAgent), AtomicInteger::getAndIncrement);
    }

    private UserAgent parseUA(String userAgent) {
        if (userAgent == null) {
            return UserAgent.UNKNOWN;
        }

        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent

        if (userAgent.toLowerCase().contains("bot")) {
            return UserAgent.LIKELY_SCRAPER;
        } else if (userAgent.startsWith("Mozilla/")) {
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent/Firefox
            return UserAgent.LIKELY_MANUAL;
        }

        return UserAgent.LIKELY_AUTOMATED;
    }

    private void alter(Artifact artifact, UserAgent userAgent, Consumer<AtomicInteger> consumer) {
        synchronized (artifacts) {
            consumer.accept(
                    artifacts.computeIfAbsent(artifact, key -> new EnumMap<>(UserAgent.class))
                            .computeIfAbsent(userAgent, key -> new AtomicInteger(0))
            );
        }
    }

    private void flushStats() {
        Map<Artifact, EnumMap<UserAgent, AtomicInteger>> artifacts;
        synchronized (this.artifacts) {
            artifacts = new ConcurrentHashMap<>(this.artifacts);
            this.artifacts.clear();
        }

        Date today = new Date(System.currentTimeMillis());

        try {
            for (Map.Entry<Artifact, EnumMap<UserAgent, AtomicInteger>> artifactEntry : artifacts.entrySet()) {
                Artifact artifact = artifactEntry.getKey();

                Long artifactId = null;
                for (int attempt = 0; true; attempt++) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "select artifactid from artifact "
                                    + "where repoowner = ? "
                                    + "and reponame = ? "
                                    + "and channel = ? "
                                    + "and artifact = ?")) {
                        statement.setString(1, artifact.getRepoOwner());
                        statement.setString(2, artifact.getRepoName());
                        statement.setString(3, artifact.getChannel());
                        statement.setString(4, artifact.getArtifact());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                artifactId = resultSet.getLong("artifactid");
                            }
                        }
                    }
                    if (artifactId != null || attempt == 1) {
                        break;
                    }

                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into artifact (repoowner, reponame, channel, artifact) values (?, ?, ?, ?)")) {
                        statement.setString(1, artifact.getRepoOwner());
                        statement.setString(2, artifact.getRepoName());
                        statement.setString(3, artifact.getChannel());
                        statement.setString(4, artifact.getArtifact());

                        int rows;
                        if ((rows = statement.executeUpdate()) != 1) {
                            throw new SQLException("Failed to update exactly 1 row, actually: " + rows);
                        }
                    }
                }
                if (artifactId == null) {
                    throw new IllegalStateException("artifactId may not be null after loop");
                }

                for (Map.Entry<UserAgent, AtomicInteger> entry : artifactEntry.getValue().entrySet()) {
                    UserAgent userAgent = entry.getKey();
                    AtomicInteger amount = entry.getValue();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "select statsid, count from stats where artifactid = ? and version = ? and useragent = ? and date = ?")) {
                        statement.setLong(1, artifactId);
                        statement.setString(2, artifact.getVersion());
                        statement.setByte(3, userAgent.sql());
                        statement.setDate(4, today);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                long statsId = resultSet.getLong("statsid");
                                int count = resultSet.getInt("count");

                                try (PreparedStatement update = connection.prepareStatement(
                                        "update stats set count = ? where statsid = ?")) {
                                    update.setInt(1, count + amount.get());
                                    update.setLong(2, statsId);

                                    int rows;
                                    if ((rows = update.executeUpdate()) != 1) {
                                        throw new SQLException("Failed to update exactly 1 row, actually: " + rows);
                                    }
                                }
                            } else {
                                try (PreparedStatement insert = connection.prepareStatement(
                                        "insert into stats (artifactid, version, useragent, date, count) values (?, ?, ?, ?, ?)")) {
                                    insert.setLong(1, artifactId);
                                    insert.setString(2, artifact.getVersion());
                                    insert.setByte(3, userAgent.sql());
                                    insert.setDate(4, today);
                                    insert.setInt(5, amount.get());

                                    int rows;
                                    if ((rows = insert.executeUpdate()) != 1) {
                                        throw new SQLException("Failed to update exactly 1 row, actually: " + rows);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Downloader.LOGGER.error("Failed to flush stats", e);
        }
    }

    @SuppressWarnings("MagicConstant")
    private Date date(String date) {
        if (date == null) {
            return null;
        }

        String[] split = date.split("-", 3);
        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid date");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(
                Integer.parseInt(split[0]),
                Integer.parseInt(split[1]) - 1,
                Integer.parseInt(split[2]),
                0,
                0,
                0
        );

        return new Date(calendar.getTimeInMillis());
    }

    private Pair<String, List<PreparedConsumer>> where(
            String repoOwner,
            String repoName,
            String channel,
            String artifact,
            String version,
            String useragent,
            String from,
            String to
    ) {
        List<String> requirements = new ArrayList<>();
        List<PreparedConsumer> preparation = new ArrayList<>();

        for (Pair<String, String> pair : Arrays.asList(
                Pair.of("repoowner", repoOwner),
                Pair.of("reponame", repoName),
                Pair.of("channel", channel),
                Pair.of("artifact", artifact),
                Pair.of("version", version),
                Pair.of("useragent", useragent)
        )) {
            String column = pair.getKey();
            String value = pair.getValue();
            if (value == null) {
                continue;
            }

            String[] values = value.split(" ");
            requirements.add("(" + Arrays.stream(values).map(key -> column + " = ?").collect(Collectors.joining(" or ")) + ")");
            for (String v : values) {
                if (column.equals("useragent")) {
                    try {
                        v = ((Byte) UserAgent.valueOf(v).sql()).toString();
                    } catch (IllegalArgumentException ignored) {}
                }
                String finalValue = v;
                preparation.add((statement, i) -> statement.setString(i, finalValue));
            }
        }

        Date fromDate = date(from);
        if (fromDate != null) {
            requirements.add("date >= ?");
            preparation.add((statement, i) -> statement.setDate(i, fromDate));
        }

        Date toDate = date(to);
        if (to != null) {
            requirements.add("date <= ?");
            preparation.add((statement, i) -> statement.setDate(i, toDate));
        }

        if (requirements.isEmpty()) {
            return Pair.of("", preparation);
        }

        return Pair.of(
                "where " + String.join(" and ", requirements),
                preparation
        );
    }

    public JsonNode query(
            String group,
            String repoOwner,
            String repoName,
            String channel,
            String artifact,
            String version,
            String useragent,
            String from,
            String to,
            int limit
    ) throws SQLException {
        if (group != null && !Pattern.compile("[a-zA-Z ]+").matcher(group).matches()) {
            throw new IllegalArgumentException("group is not a-Z");
        }
        if (limit > 5000) {
            limit = 5000;
        }

        List<String> groups = group == null ? Collections.emptyList() : Arrays.asList(group.split(" "));
        StringBuilder groupSelect = new StringBuilder();
        if (!groups.isEmpty()) {
            for (int i = 0; i < groups.size(); i++) {
                groupSelect.append(groups.get(i)).append(" as group").append(i).append(", ");
            }
        }

        Pair<String, List<PreparedConsumer>> where = where(repoOwner, repoName, channel, artifact, version, useragent, from, to);
        String sql = "select " + groupSelect + " sum(count) as sum from stats "
                + "inner join artifact on artifact.artifactid = stats.artifactid "
                + where.getKey() + " " + (groups.isEmpty() ? "" : "group by " + (String.join(", ", groups))) + " limit ?";

        ObjectNode node = Downloader.OBJECT_MAPPER.createObjectNode();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int param = 0;
            for (PreparedConsumer consumer : where.getValue()) {
                consumer.consume(statement, ++param);
            }
            statement.setInt(++param, limit);

            int count = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    count++;
                    if (groups.isEmpty()) {
                        node.put("total_downloads", resultSet.getInt("sum"));
                    } else {
                        ObjectNode next = node;
                        for (int i = 0; i < groups.size(); i++) {
                            String value = resultSet.getString("group" + i).trim();

                            String groupName = groups.get(i);
                            if (groupName.equalsIgnoreCase("useragent")) {
                                try {
                                    value = UserAgent.getBySql(value).name();
                                } catch (IllegalArgumentException ignored) {}
                            }

                            if (groups.size() - 1 == i) {
                                // Last
                                next.put(value, resultSet.getInt("sum"));
                                continue;
                            }

                            // Create/get object node
                            JsonNode obj = next.get(value);
                            if (obj == null) {
                                obj = next.putObject(value);
                            }
                            next = ((ObjectNode) obj);
                        }
                    }
                }
            }
            node.put("rows", count);
        }

        return node;
    }

    @Bean(destroyMethod = "close")
    private Connection statsConnection() {
        return connection;
    }

    @Bean(destroyMethod = "shutdown")
    private ScheduledExecutorService statsExecutor() {
        return executorService;
    }

    @FunctionalInterface
    public interface PreparedConsumer {
        void consume(PreparedStatement statement, int index) throws SQLException;
    }

    public static final class Artifact {

        private final String repoOwner;
        private final String repoName;
        private final String channelIdentifier;
        private final String artifactIdentifier;
        private final String version;

        public Artifact(String repoOwner, String repoName, String channelIdentifier, String artifactIdentifier, String version) {
            this.repoOwner = repoOwner;
            this.repoName = repoName;
            this.channelIdentifier = channelIdentifier;
            this.artifactIdentifier = artifactIdentifier;
            this.version = version;
        }

        public String getRepoOwner() {
            return repoOwner;
        }

        public String getRepoName() {
            return repoName;
        }

        public String getChannel() {
            return channelIdentifier;
        }

        public String getArtifact() {
            return artifactIdentifier;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Artifact artifact = (Artifact) o;
            return Objects.equals(repoOwner, artifact.repoOwner)
                    && Objects.equals(repoName, artifact.repoName)
                    && Objects.equals(channelIdentifier, artifact.channelIdentifier)
                    && Objects.equals(artifactIdentifier, artifact.artifactIdentifier)
                    && Objects.equals(version, artifact.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repoOwner, repoName, channelIdentifier, artifactIdentifier, version);
        }
    }

}
