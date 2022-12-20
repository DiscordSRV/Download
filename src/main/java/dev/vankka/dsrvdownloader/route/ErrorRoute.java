package dev.vankka.dsrvdownloader.route;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.Downloader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

@RestController
public class ErrorRoute implements ErrorController {

    @RequestMapping(path = "/error")
    @ResponseBody
    public Object error(HttpServletRequest request) {
        int status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        String statusDescription = null;
        try {
            HttpStatus httpStatus = HttpStatus.valueOf(status);
            statusDescription = httpStatus.getReasonPhrase();
        } catch (IllegalArgumentException ignored) {}

        ObjectNode json = Downloader.OBJECT_MAPPER.createObjectNode();
        json.put("status_code", status);
        json.put("status_description", statusDescription);
        json.put("error_message", StringUtils.isNotEmpty(errorMessage) ? errorMessage : statusDescription);

        return json;
    }
}
