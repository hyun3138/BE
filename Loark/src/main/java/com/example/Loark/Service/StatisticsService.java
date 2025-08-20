package com.example.Loark.Service;

import com.example.Loark.DTO.BoxplotDto;
import com.example.Loark.DTO.HistogramDto;
import com.example.Loark.DTO.PercentileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final RestTemplate restTemplate;

    @Value("${statistics.api.base-url}")
    private String statisticsApiUrl;

    public HistogramDto getHistogram(String x, String metric, String role, String raid, String gate, String diff) {
        URI uri = UriComponentsBuilder
                .fromUriString(statisticsApiUrl)
                .path("/v1/histogram")
                .queryParam("x", x)
                .queryParam("metric", metric)
                .queryParam("role", role)
                .queryParam("raid", raid)
                .queryParam("gate", gate)
                .queryParam("diff", diff)
                .build()
                .toUri();

        return restTemplate.getForObject(uri, HistogramDto.class);
    }

    public BoxplotDto getBoxplot(String x, String metric) {
        URI uri = UriComponentsBuilder
                .fromUriString(statisticsApiUrl)
                .path("/v1/boxplot")
                .queryParam("x", x)
                .queryParam("metric", metric)
                .build()
                .toUri();

        return restTemplate.getForObject(uri, BoxplotDto.class);
    }

    public PercentileDto getPercentile(String x, String metric) {
        URI uri = UriComponentsBuilder
                .fromUriString(statisticsApiUrl)
                .path("/v1/percentile")
                .queryParam("x", x)
                .queryParam("metric", metric)
                .build()
                .toUri();

        return restTemplate.getForObject(uri, PercentileDto.class);
    }
}
