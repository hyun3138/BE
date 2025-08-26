package com.example.Loark.Service;

import com.example.Loark.DTO.FactGateMetricsDto;
import com.example.Loark.Entity.*;
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

    @Transactional
    public PartyRun createPartyRun(UUID partyId, String raidName, User creator) {
        // 1. 파티 조회 및 파티장 권한 확인
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 파티입니다."));

        if (!party.getOwner().getUserId().equals(creator.getUserId())) {
            throw new IllegalStateException("파티장만 레이드를 생성할 수 있습니다.");
        }

        // 2. 파티장의 대표 캐릭터 이름으로 fact 테이블에서 최신 레이드 정보 조회
        String characterName = creator.getMainCharacter();
        FactGateMetricsDto factData = factGateMetricsRepository.findLatestByCharacterNameAndRaidName(characterName, raidName)
                .orElseThrow(() -> new IllegalStateException("해당 캐릭터와 레이드에 대한 정보를 찾을 수 없습니다."));

        // 3. PartyRun 생성 및 저장
        PartyRun partyRun = PartyRun.builder()
                .party(party)
                .raidName(factData.getRaidName())
                .gateNumber(factData.getGateNumber())
                .difficulty(factData.getDifficulty())
                .playTime(factData.getPlayTime())
                .createdBy(creator)
                .build();
        partyRunRepository.save(partyRun);

        // 4. 현재 파티 멤버들을 PartyRunMember로 복사
        List<PartyMember> currentMembers = partyMemberRepository.findByParty_PartyIdAndLeftAtIsNull(partyId);
        List<PartyRunMember> runMembers = currentMembers.stream()
                .map(member -> {
                    Long characterId = member.getUser().getUserId();
                    return PartyRunMember.builder()
                        .id(new PartyRunMemberId(partyRun.getPartyRunId(), member.getUser().getUserId()))
                        .partyRun(partyRun)
                        .user(member.getUser())
                        .characterId(characterId)
                        .subparty(member.getSubparty())
                        .role(member.getRole())
                        .build();
                })
                .collect(Collectors.toList());
        partyRunMemberRepository.saveAll(runMembers);

        // 5. 생성된 party_run_id를 fact_gate_metrics 테이블에 업데이트 (오차 범위 적용)
        if (factData.getPlayTime() != null && !currentMembers.isEmpty()) {
            List<String> memberNicknames = currentMembers.stream()
                    .map(member -> member.getUser().getMainCharacter())
                    .collect(Collectors.toList());

            // play_time을 기준으로 ±3초의 오차 범위를 설정
            Duration centerPlayTime = factData.getPlayTime();
            Duration startTimeDuration = centerPlayTime.minusSeconds(3);
            Duration endTimeDuration = centerPlayTime.plusSeconds(3);

            // Duration을 double 초 단위로 변환
            double startSeconds = startTimeDuration.toMillis() / 1000.0;
            double endSeconds = endTimeDuration.toMillis() / 1000.0;

            factGateMetricsRepository.updatePartyRunId(
                    partyRun.getPartyRunId(),
                    factData.getRaidName(),
                    factData.getGateNumber(),
                    factData.getDifficulty(),
                    startSeconds, // double 초 전달
                    endSeconds,   // double 초 전달
                    memberNicknames
            );
        }

        return partyRun;
    }

    public List<FactGateMetricsDto> getPartyRunCombatRecords(UUID partyRunId) {
        // partyRunId 존재 여부 확인 (선택적)
        if (!partyRunRepository.existsById(partyRunId)) {
            throw new IllegalStateException("존재하지 않는 레이드 기록입니다.");
        }
        // 이제 이 메소드는 party_run_id가 업데이트된 fact 테이블을 정상적으로 조회합니다.
        return factGateMetricsRepository.findAllByPartyRunId(partyRunId);
    }
}
