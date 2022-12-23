package dev.vankka.dsrvdownloader.route.admin;

import dev.vankka.dsrvdownloader.manager.StatsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

@RestController
public class StatsController {

    private final StatsManager statsManager;

    public StatsController(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @GetMapping(path = "/admin/stats")
    public Object getStats(
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "repoOwner", required = false) String repoOwner,
            @RequestParam(name = "repoName", required = false) String repoName,
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "artifact", required = false) String artifact,
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "useragent", required = false) String useragent,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) throws SQLException {
        return statsManager.query(group, repoOwner, repoName, channel, artifact, version, useragent, from, to, limit);
    }
}
