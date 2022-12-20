package dev.vankka.dsrvdownloader.route.admin;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.Config;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConfigController {

    private final ConfigManager configManager;
    private final ChannelManager channelManager;

    public ConfigController(ConfigManager configManager, ChannelManager channelManager) {
        this.configManager = configManager;
        this.channelManager = channelManager;
    }

    @GetMapping(path = "/admin/config")
    public ResponseEntity<?> getConfig() {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Downloader.OBJECT_MAPPER.writeValueAsString(configManager.config()));
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to write config\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @PostMapping(path = "/admin/config")
    @ResponseStatus(code = HttpStatus.OK, reason = "Success")
    public void updateConfig(@RequestBody Config config) {
        try {
            configManager.replaceConfig(config);
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to save/load config\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @PostMapping(path = "/admin/reload-channels")
    @ResponseStatus(code = HttpStatus.OK, reason = "Reloaded")
    public void reloadChannels() {
        try {
            channelManager.reloadVersionChannels();
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to reload channels\n" + ExceptionUtils.getStackTrace(e));
        }
    }
}
