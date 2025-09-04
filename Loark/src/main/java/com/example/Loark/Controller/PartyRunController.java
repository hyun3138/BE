package com.example.Loark.Controller;

import com.example.Loark.DTO.FactGateMetricsDto;
import com.example.Loark.DTO.PartyRunResponseDto;
import com.example.Loark.Entity.PartyRun;
import com.example.Loark.Entity.User;
import com.example.Loark.Service.PartyRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parties/{partyId}/runs")
@RequiredArgsConstructor
public class PartyRunController {

    private final PartyRunService partyRunService;

    @PostMapping
    public ResponseEntity<?> createPartyRun(@PathVariable UUID partyId,
                                            @AuthenticationPrincipal User me) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증이 필요합니다.");
        }
        try {
            PartyRun partyRun = partyRunService.createPartyRun(partyId, me);
            PartyRunResponseDto responseDto = PartyRunResponseDto.fromEntity(partyRun);
            return ResponseEntity.ok(responseDto);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{runId}/combat-records")
    public ResponseEntity<?> getCombatRecords(@PathVariable UUID partyId,
                                              @PathVariable("runId") UUID partyRunId,
                                              @AuthenticationPrincipal User me) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증이 필요합니다.");
        }
        try {
            List<FactGateMetricsDto> combatRecords = partyRunService.getPartyRunCombatRecords(partyRunId);
            return ResponseEntity.ok(combatRecords);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
