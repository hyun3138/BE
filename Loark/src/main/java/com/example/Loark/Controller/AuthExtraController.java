package com.example.Loark.Controller;

import com.example.Loark.DTO.ApiKeyRequest;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Service.LostarkApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthExtraController {
    private final UserRepository userRepository;
    private final LostarkApiClient loa;

    @PostMapping("/api-key")
    public ResponseEntity<?> saveApiKey(@RequestBody ApiKeyRequest req,
                                        @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        if (req.getApiKey() == null || req.getApiKey().isBlank()) {
            return ResponseEntity.badRequest().body("API Key를 입력하세요.");
        }
        me.setUserApiKey(req.getApiKey());
        userRepository.save(me);
        return ResponseEntity.ok("API Key 저장 완료");
    }
}
