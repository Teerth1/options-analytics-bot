package com.dealaggregator.dealapi.controller;

import com.dealaggregator.dealapi.entity.GexSnapshot;
import com.dealaggregator.dealapi.repository.GexSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/gex")
@CrossOrigin(origins = "*") // Allows web frontend to call this easily
public class GexController {

    private final GexSnapshotRepository gexSnapshotRepository;

    @Autowired
    public GexController(GexSnapshotRepository gexSnapshotRepository) {
        this.gexSnapshotRepository = gexSnapshotRepository;
    }

    /**
     * Gets all GEX snapshots for a given ticker on a specific date.
     * Useful for building intraday contour charts or historical playback sequences.
     * 
     * @param ticker The symbol (e.g., "SPX")
     * @param date   The date in YYYY-MM-DD format. Defaults to today.
     * @return List of GexSnapshots recorded during that day.
     */
    @GetMapping("/intraday")
    public ResponseEntity<List<GexSnapshot>> getIntradayGex(
            @RequestParam(defaultValue = "SPX") String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        if (date == null) {
            date = LocalDate.now();
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<GexSnapshot> snapshots = gexSnapshotRepository
                .findByTickerAndTimestampBetweenOrderByTimestampAsc(ticker, startOfDay, endOfDay);

        if (snapshots.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(snapshots);
    }

    /**
     * Retrieves the absolute latest snapshot available.
     * 
     * @param ticker The symbol (e.g., "SPX")
     * @return The latest GexSnapshot.
     */
    @GetMapping("/latest")
    public ResponseEntity<GexSnapshot> getLatestGex(@RequestParam(defaultValue = "SPX") String ticker) {
        GexSnapshot latest = gexSnapshotRepository.findTopByTickerOrderByTimestampDesc(ticker);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }
}
