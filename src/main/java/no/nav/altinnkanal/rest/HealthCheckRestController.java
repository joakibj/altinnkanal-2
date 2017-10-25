package no.nav.altinnkanal.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Controller
public class HealthCheckRestController {

    private static final String APPLICATION_ALIVE = "Application is alive";
    private static final String APPLICATION_READY = "Application is ready";
    private static final String BASE_URL = "http://localhost:8080/";
    private static final String WSDL_URL = BASE_URL + "altinnkanal/OnlineBatchReceiverSoap?wsdl";
    private static final String CONFIGURATION_URL = BASE_URL + "configuration";

    private List<Boolean> checks;

    @ResponseBody
    @RequestMapping(value="isAlive", produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity isAlive() {
        return new ResponseEntity<>(APPLICATION_ALIVE, HttpStatus.OK);
    }

    @ResponseBody
    @RequestMapping(value="isReady", produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity isReady() {
        boolean ready = selfTest();
        if (!ready) {
            return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(APPLICATION_READY, HttpStatus.OK);
    }

    private boolean selfTest() {
        checks = new ArrayList<>();
        checks.add(httpUrlFetchTest(WSDL_URL));
        checks.add(httpUrlFetchTest(CONFIGURATION_URL));
        for (boolean check : checks) if (!check) return false;
        return true;
    }

    private boolean httpUrlFetchTest(String urlString) {
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = (HttpURLConnection) new URL(urlString).openConnection();
            return httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ioe) {
            // TODO: Log the exception
            return false;
        } finally {
            if (httpConnection != null) httpConnection.disconnect();
        }
    }
}
