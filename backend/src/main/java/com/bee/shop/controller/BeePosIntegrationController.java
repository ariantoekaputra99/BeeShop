package com.bee.shop.controller;

import com.bee.shop.dto.SalesExport;
import com.bee.shop.dto.StockSyncRequest;
import com.bee.shop.dto.StockSyncResult;
import com.bee.shop.service.StockSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/** Dipanggil server BeePos-App dengan header X-Integration-Key. */
@RestController
@RequestMapping("/api/integrations/beepos")
@RequiredArgsConstructor
public class BeePosIntegrationController {

    private final StockSyncService sync;

    @PutMapping("/stock")
    public StockSyncResult syncStock(@Valid @RequestBody StockSyncRequest request) {
        return sync.syncStock(request);
    }

    @GetMapping("/sales")
    public List<SalesExport> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        return sync.salesSince(since);
    }
}
