package com.example.Loark.Service;

import com.example.Loark.DTO.FactGateMetricsDto;
import com.example.Loark.Entity.*;
import com.example.Loark.Entity.Character;
import com.example.Loark.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyRunService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyRunRepository partyRunRepository;
    private final PartyRunMemberRepository partyRunMemberRepository;
    private final FactGateMetricsRepository factGateMetricsRepository;
    private final CharacterRepository characterRepository; // 의존성 추가

    public List<PartyRun> getPartyRunsByPartyId(UUID partyId, User currentUser) {
        boolean isMember = partyMemberRepository.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, currentUser.getUserId());
        if (!isMember) {
            throw new IllegalStateException("해당 파티의 활동 기록을 조회할 권한이 없습니다.");
        }
        return partyRunRepository.findByParty_PartyIdOrderByCreatedAtDesc(partyId);
    }

    @Transactional
    public PartyRun createPartyRun(UUID partyId, User creator) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 파티입니다."));

        if (!party.getOwner().getUserId().equals(creator.getUserId())) {
            throw new IllegalStateException("파티장만 레이드를 생성할 수 있습니다.");
        }

        String leaderCharacterName = creator.getMainCharacter();
        if (leaderCharacterName == null || leaderCharacterName.isBlank()) {
            throw new IllegalStateException("공대장의 대표 캐릭터가 설정되어 있지 않습니다.");
        }
        FactGateMetricsDto leaderRecord = factGateMetricsRepository.findLatestByCharacterName(leaderCharacterName)
                .orElseThrow(() -> new IllegalStateException("공대장의 최신 전투 기록을 찾을 수 없습니다. 먼저 전투 분석 정보를 저장해주세요."));

        PartyRun partyRun = PartyRun.builder()
                .party(party)
                .raidName(leaderRecord.getRaidName())
                .gateNumber(leaderRecord.getGateNumber())
                .difficulty(leaderRecord.getDifficulty())
                .playTime(leaderRecord.getPlayTime())
                .createdBy(creator)
                .build();
        partyRunRepository.save(partyRun);

        // 4. 현재 파티 멤버들을 PartyRunMember로 복사 (character_id 조회 로직 수정)
        List<PartyMember> currentMembers = partyMemberRepository.findByParty_PartyIdAndLeftAtIsNull(partyId);
        List<PartyRunMember> runMembers = currentMembers.stream()
                .map(member -> {
                    // 올바른 character_id를 조회하는 로직
                    Long characterId = characterRepository.findByName(member.getUser().getMainCharacter())
                            .map(Character::getCharacterId)
                            .orElse(null); // 메인 캐릭터가 없거나, characters 테이블에 등록 안되어 있으면 null

                    return PartyRunMember.builder()
                            .id(new PartyRunMemberId(partyRun.getPartyRunId(), member.getUser().getUserId()))
                            .partyRun(partyRun)
                            .user(member.getUser())
                            .characterId(characterId) // 올바른 ID로 설정
                            .position(member.getPosition())
                            .role(member.getRole())
                            .build();
                })
                .collect(Collectors.toList());
        partyRunMemberRepository.saveAll(runMembers);

        // 5. 생성된 party_run_id를 fact_gate_metrics 테이블에 업데이트 (오차 범위 적용)
        if (leaderRecord.getPlayTime() != null && !currentMembers.isEmpty()) {
            List<String> memberNicknames = currentMembers.stream()
                    .map(member -> member.getUser().getMainCharacter())
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toList());

            if (memberNicknames.isEmpty()) {
                factGateMetricsRepository.updatePartyRunId(
                        partyRun.getPartyRunId(),
                        leaderRecord.getRaidName(),
                        leaderRecord.getGateNumber(),
                        leaderRecord.getDifficulty(),
                        leaderRecord.getPlayTime().toMillis() / 1000.0 - 1,
                        leaderRecord.getPlayTime().toMillis() / 1000.0 + 1,
                        List.of(leaderCharacterName)
                );
                return partyRun;
            }

            Duration centerPlayTime = leaderRecord.getPlayTime();
            double startSeconds = centerPlayTime.minusSeconds(3).toMillis() / 1000.0;
            double endSeconds = centerPlayTime.plusSeconds(3).toMillis() / 1000.0;

            factGateMetricsRepository.updatePartyRunId(
                    partyRun.getPartyRunId(),
                    leaderRecord.getRaidName(),
                    leaderRecord.getGateNumber(),
                    leaderRecord.getDifficulty(),
                    startSeconds,
                    endSeconds,
                    memberNicknames
            );
        }

        return partyRun;
    }

    public List<FactGateMetricsDto> getPartyRunCombatRecords(UUID partyRunId, User currentUser) {
        PartyRun partyRun = partyRunRepository.findById(partyRunId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 레이드 기록입니다."));

        Party party = partyRun.getParty();

        boolean isMember = partyMemberRepository.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(party.getPartyId(), currentUser.getUserId());
        if (!isMember) {
            throw new IllegalStateException("해당 레이드 기록을 조회할 권한이 없습니다.");
        }

        return factGateMetricsRepository.findAllByPartyRunId(partyRunId);
    }
}
