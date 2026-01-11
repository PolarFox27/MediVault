package ssd.medivault.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class HCaptchaService {

        private static final Logger logger = LoggerFactory.getLogger(HCaptchaService.class);

        private final String secret;
        private final RestTemplate restTemplate;

        private static final String VERIFY_URL = "https://hcaptcha.com/siteverify";

        public HCaptchaService(@Value("${hcaptcha.secret}") String secret,
                                                  RestTemplate restTemplate) {
                this.secret = secret;
                this.restTemplate = restTemplate;
        }

        public boolean verify(String token, String remoteIp) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("secret", secret);
                body.add("response", token);
                if (remoteIp != null) {
                        body.add("remoteip", remoteIp);
                }

                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

                Map response = restTemplate.postForObject(VERIFY_URL, request, Map.class);

                if (logger.isDebugEnabled()) {
                        logger.debug("hCaptcha verify response for remoteIp={}: {}", remoteIp, response);
                }

                return response != null && Boolean.TRUE.equals(response.get("success"));
        }
}
