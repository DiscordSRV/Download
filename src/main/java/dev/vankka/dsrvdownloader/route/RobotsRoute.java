package dev.vankka.dsrvdownloader.route;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RobotsRoute {

    @RequestMapping("/robots.txt")
    @ResponseBody
    public String robots() {
        return "User-agent: *\nDisallow: /";
    }
}
