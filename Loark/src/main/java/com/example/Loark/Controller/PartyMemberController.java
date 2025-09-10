package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyMemberResponse;
import com.example.Loark.Entity.*;
import com.example.Loark.Entity.Character;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.CharacterSpecRepository;
import com.example.Loark.Repository.PartyRepository;
import com.example.Loark.Service.PartyMemberService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parties/{partyId}/members")
@RequiredArgsConstructor
public class PartyMemberController {

    private final PartyMemberService service;
    private final PartyRepository parties;
    private final CharacterRepository characterRepository;
    private final CharacterSpecRepository characterSpecRepository;

    @Data
    static class AddMemberRequest {
        private Long userId;
        private String nickname;
    }

    /** 멤버 목록 조회 */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable UUID partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));
        List<PartyMember> all = service.list(partyId);
        List<PartyMemberResponse> dtoList = all.stream()
                .map(member -> toMemberDto(member, party.getOwner().getUserId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    private PartyMemberResponse toMemberDto(PartyMember member, Long ownerId) {
        User user = member.getUser();
        String mainCharacterName = user.getMainCharacter();
        String server = null;
        BigDecimal itemLevel = null;
        String characterClass = null;

        if (mainCharacterName != null && !mainCharacterName.isBlank()) {
            Optional<Character> characterOpt = characterRepository.findByName(mainCharacterName);
            if (characterOpt.isPresent()) {
                Character character = characterOpt.get();
                server = character.getServer();
                characterClass = character.getClazz();

                // 최신 스펙에서 아이템 레벨 조회 (메서드 이름 수정)
                Optional<CharacterSpec> specOpt = characterSpecRepository.findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(character.getCharacterId());
                if (specOpt.isPresent()) {
                    itemLevel = specOpt.get().getItemLevel();
                }
            }
        }

        return PartyMemberResponse.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .pictureUrl(user.getPictureUrl())
                .position(member.getPosition())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt() != null ? member.getJoinedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null)
                .leftAt(member.getLeftAt() != null ? member.getLeftAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null)
                .owner(user.getUserId().equals(ownerId))
                .mainCharacterName(mainCharacterName)
                .server(server)
                .itemLevel(itemLevel)
                .characterClass(characterClass)
                .build();
    }

    /** 멤버 즉시 추가 (공대장만) */
    @PostMapping
    public ResponseEntity<?> addMember(@PathVariable UUID partyId,
                                       @AuthenticationPrincipal User me,
                                       @RequestBody AddMemberRequest req) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증 필요");
        }

        if (req.getUserId() != null) {
            service.addMemberById(partyId, me.getUserId(), req.getUserId());
        } else if (req.getNickname() != null && !req.getNickname().isBlank()) {
            service.addMemberByNickname(partyId, me.getUserId(), req.getNickname());
        } else {
            return ResponseEntity.badRequest().body("추가할 멤버의 ID 또는 닉네임을 입력해주세요.");
        }

        return ResponseEntity.ok("멤버를 추가했습니다.");
    }

    /** 퇴장(본인) — 공대장은 퇴장 불가 */
    @PostMapping("/leave")
    public ResponseEntity<?> leave(@PathVariable UUID partyId,
                                   @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.leave(partyId, me.getUserId());
        return ResponseEntity.ok("공대에서 퇴장했습니다.");
    }

    /** 추방(공대장만) */
    @PostMapping("/{userId}/kick")
    public ResponseEntity<?> kick(@PathVariable UUID partyId,
                                  @PathVariable Long userId,
                                  @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.kick(partyId, me.getUserId(), userId);
        return ResponseEntity.ok("해당 멤버를 추방했습니다.");
    }
}