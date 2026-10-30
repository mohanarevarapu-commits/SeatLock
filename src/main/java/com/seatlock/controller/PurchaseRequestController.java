package com.seatlock.controller;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.PurchaseRequestResponse;
import com.seatlock.service.PurchaseRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Queued checkout", description = "Hold seats through the purchase queue during peak traffic")
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;

    public PurchaseRequestController(PurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
    }

    @Operation(summary = "Queue a request to hold seats; poll the returned request for the outcome")
    @ApiResponse(responseCode = "202")
    @PostMapping("/events/{id}/purchase-requests")
    public ResponseEntity<PurchaseRequestResponse> submit(@PathVariable Long id,
                                                          @Valid @RequestBody CreateHoldRequest request) {
        PurchaseRequestResponse queued = purchaseRequestService.submit(id, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/purchase-requests/" + queued.requestId()))
                .body(queued);
    }

    @Operation(summary = "Status of a queued request: QUEUED, HELD (with holdId), REJECTED or FAILED")
    @GetMapping("/purchase-requests/{requestId}")
    public PurchaseRequestResponse status(@PathVariable String requestId) {
        return purchaseRequestService.getStatus(requestId);
    }
}
