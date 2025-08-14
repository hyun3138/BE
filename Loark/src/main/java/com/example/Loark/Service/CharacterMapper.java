package com.example.Loark.Service;

import com.example.Loark.DTO.CharacterResponse;
import com.example.Loark.Entity.Character;

import java.util.function.Function;

public class CharacterMapper {
    public static final Function<Character, CharacterResponse> toDto = entity ->
            CharacterResponse.builder()
                    .characterId(entity.getCharacterId())
                    .name(entity.getName())
                    .server(entity.getServer())
                    .clazz(entity.getClazz())
                    .level(entity.getLevel())
                    .expeditionLevel(entity.getExpeditionLevel())
                    .itemLevel(entity.getItemLevel())
                    .combatPower(entity.getCombatPower())
                    .arkPassive(entity.getArkPassive())
                    .main(entity.isMain())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
}