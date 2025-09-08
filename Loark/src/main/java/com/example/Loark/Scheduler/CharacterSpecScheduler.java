package com.example.Loark.Scheduler;

import com.example.Loark.Service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CharacterSpecScheduler {

    private final CharacterService characterService;

    @Scheduled(cron = "0 0 * * * *") // 매 정각에 실행
    public void updateCharacterSpecs() {
        characterService.updateAllCharacterSpecs();
    }
}
