package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyCreateRequest;
import com.example.Loark.DTO.PartyResponse;
import com.example.Loark.Entity.User;
import com.example.Loark.Service.PartyMapper;
import com.example.Loark.Service.PartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {
    private final PartyService partyService;

    /** 공대 생성 (공대장 = 로그인 유저) */
    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User me,
                                    @RequestBody PartyCreateRequest req) {
        var saved = partyService.create(me, req.getName(), req.getVisibility());
        return ResponseEntity.ok(PartyMapper.toDto(saved));
    }

    /** 내가 공대장인 공대 목록 */
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@AuthenticationPrincipal User me) {
        var list = partyService.listMine(me.getUserId())
                .stream().map(PartyMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
