package dev.vankka.dsrvdownloader.route;

import dev.vankka.dsrvdownloader.manager.ConfigManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class RootRoute {

    private final ConfigManager configManager;

    public RootRoute(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @GetMapping(path = "/")
    public View rootRedirect() {
        return new RedirectView(configManager.config().rootRedirectUrl());
    }

    @RequestMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return "User-agent: *\nDisallow: /";
    }
}
