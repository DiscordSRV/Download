package dev.vankka.dsrvdownloader.route;

import dev.vankka.dsrvdownloader.model.ErrorModel;
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

        return new ErrorModel(status, statusDescription, errorMessage);
    }
}
