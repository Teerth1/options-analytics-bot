package com.dealaggregator.dealapi.controller;

import com.dealaggregator.dealapi.service.SchwabApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/auth/schwab")
public class SchwabAuthController {

    @Autowired
    private SchwabApiService schwabApiService;

    /**
     * Start the OAuth flow.
     * Visit this URL in your browser to log in to Schwab.
     */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String authUrl = schwabApiService.getAuthorizationUrl();
        response.sendRedirect(authUrl);
    }

    /**
     * The callback that Schwab redirects to.
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam("code") String code) {
        boolean success = schwabApiService.exchangeCodeForTokens(code);
        
        if (success) {
            return ResponseEntity.ok()
                .body("<h1>✅ Schwab Authentication Successful!</h1>" +
                      "<p>Your tokens have been updated and saved to the database.</p>" +
                      "<p>You can now close this window.</p>");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("<h1>❌ Authentication Failed</h1>" +
                      "<p>Check the server logs for more details.</p>");
        }
    }
}
