package dev.vankka.dsrvdownloader.manager;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
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

@Service
@SuppressWarnings("SpellCheckingInspection")
public class StatsManager {

    private final ScheduledExecutorService executorService;
    private final Connection connection;
    private final Map<Artifact, AtomicInteger> artifacts = new LinkedHashMap<>();

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
                                      + "date date,"
                                      + "count int,"
                                      + "primary key (statsid),"
                                      + "foreign key (artifactid) references artifact(artifactid)"
                                      + ");");
        }

        executorService.scheduleAtFixedRate(this::flushStats, 10, 10, TimeUnit.SECONDS);
    }

    public void increment(VersionChannel versionChannel, String artifactIdentifier, String version) {
        VersionChannelConfig config = versionChannel.getConfig();
        Artifact artifact = new Artifact(config.repoOwner, config.repoName, config.name, artifactIdentifier, version);
        alter(artifact, AtomicInteger::getAndIncrement);
    }

    private void alter(Artifact artifact, Consumer<AtomicInteger> consumer) {
        synchronized (artifacts) {
            consumer.accept(
                    artifacts.computeIfAbsent(artifact, key -> new AtomicInteger(0))
            );
        }
    }

    private void flushStats() {
        Map<Artifact, AtomicInteger> artifacts;
        synchronized (this.artifacts) {
            artifacts = new ConcurrentHashMap<>(this.artifacts);
            this.artifacts.clear();
        }

        Date today = new Date(System.currentTimeMillis());

        try {
            for (Map.Entry<Artifact, AtomicInteger> entry : artifacts.entrySet()) {
                Artifact artifact = entry.getKey();
                AtomicInteger amount = entry.getValue();

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

                try (PreparedStatement statement = connection.prepareStatement(
                        "select statsid, count from stats where artifactid = ? and version = ? and date = ?")) {
                    statement.setLong(1, artifactId);
                    statement.setString(2, artifact.getVersion());
                    statement.setDate(3, today);
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
                                    "insert into stats (artifactid, version, date, count) values (?, ?, ?, ?)")) {
                                insert.setLong(1, artifactId);
                                insert.setString(2, artifact.getVersion());
                                insert.setDate(3, today);
                                insert.setInt(4, amount.get());

                                int rows;
                                if ((rows = insert.executeUpdate()) != 1) {
                                    throw new SQLException("Failed to update exactly 1 row, actually: " + rows);
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
                Pair.of("version", version)
        )) {
            String value = pair.getValue();
            if (value == null) {
                continue;
            }

            requirements.add(pair.getKey() + " = ?");
            preparation.add((statement, i) -> statement.setString(i, value));
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

    public Map<String, Integer> query(
            String group,
            String repoOwner,
            String repoName,
            String channel,
            String artifact,
            String version,
            String from,
            String to,
            int limit
    ) throws SQLException {
        if (group != null && !Pattern.compile("[a-zA-Z]+").matcher(group).matches()) {
            throw new IllegalArgumentException("group is not a-Z");
        }

        Map<String, Integer> results = new LinkedHashMap<>();

        Pair<String, List<PreparedConsumer>> where = where(repoOwner, repoName, channel, artifact, version, from, to);
        String sql = "select " + (group != null ? group + " as grouping," : "") + " sum(count) as sum from stats "
                + "inner join artifact on artifact.artifactid = stats.artifactid "
                + where.getKey() + " " + (group != null ? "group by " + group : "") + " limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 0;
            for (PreparedConsumer consumer : where.getValue()) {
                consumer.consume(statement, ++i);
            }
            statement.setInt(++i, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (group != null) {
                        results.put(resultSet.getString("grouping").trim(), resultSet.getInt("sum"));
                    } else {
                        results.put("sum", resultSet.getInt("sum"));
                    }
                }
            }
        }

        return results;
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
