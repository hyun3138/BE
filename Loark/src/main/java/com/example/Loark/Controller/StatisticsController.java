package com.example.Loark.Controller;

import com.example.Loark.DTO.BoxplotDto;
import com.example.Loark.DTO.HistogramDto;
import com.example.Loark.DTO.PercentileDto;
import com.example.Loark.Service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/histogram")
    public ResponseEntity<HistogramDto> getHistogram(
            @RequestParam String x,
            @RequestParam String metric,
            @RequestParam String role,
            @RequestParam String raid,
            @RequestParam String gate,
            @RequestParam String diff) {
        HistogramDto histogram = statisticsService.getHistogram(x, metric, role, raid, gate, diff);
        return ResponseEntity.ok(histogram);
    }

    @GetMapping("/boxplot")
    public ResponseEntity<BoxplotDto> getBoxplot(
            @RequestParam String x,
            @RequestParam String metric) {
        BoxplotDto boxplot = statisticsService.getBoxplot(x, metric);
        return ResponseEntity.ok(boxplot);
    }

    @GetMapping("/percentile")
    public ResponseEntity<PercentileDto> getPercentile(
            @RequestParam String x,
            @RequestParam String metric) {
        PercentileDto percentile = statisticsService.getPercentile(x, metric);
        return ResponseEntity.ok(percentile);
    }
}
