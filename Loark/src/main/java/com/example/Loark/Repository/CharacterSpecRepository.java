package com.example.Loark.Repository;

import com.example.Loark.Entity.CharacterSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CharacterSpecRepository extends JpaRepository<CharacterSpec, Long> {
    Optional<CharacterSpec> findByCharacter_CharacterId(Long characterId);

    Optional<CharacterSpec> findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(Long characterId);

    Optional<CharacterSpec> findFirstByCharacterCharacterIdAndUpdatedAtLessThanEqualOrderByUpdatedAtDesc(Long characterId, LocalDateTime updatedAt);

    Optional<CharacterSpec> findFirstByCharacterCharacterIdOrderByUpdatedAtAsc(Long characterId);

    Optional<CharacterSpec> findFirstByCharacterCharacterIdAndUpdatedAtBeforeOrderByUpdatedAtDesc(Long characterId, LocalDateTime recordedAt);
}
