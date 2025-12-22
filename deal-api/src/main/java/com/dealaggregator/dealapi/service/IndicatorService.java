package com.dealaggregator.dealapi.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IndicatorService {
    private static final String FLASK_URL = "http://localhost:5001";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> getAllIndicators(String ticker) throws Exception {
        // 1. Build request URL
        String url = FLASK_URL + "/indicators/all?ticker=" + ticker;

        // 2. Create HTTP GET request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        // 3. Send request and get response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 4. Check for errors
        if (response.statusCode() != 200) {
            throw new RuntimeException("Flask API error: " + response.body());
        }

        // 5. Parse JSON response body into Map
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
        });
    }
}
