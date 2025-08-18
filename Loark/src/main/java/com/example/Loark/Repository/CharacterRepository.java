package com.example.Loark.Repository;

import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {
    Optional<Character> findByName(String name);
    Optional<Character> findByUserAndMainTrue(User user);
    Optional<Character> findByUserAndName(User user, String name);
    void deleteByUserAndName(User user, String name);
    List<Character> findAllByUserOrderByMainDescUpdatedAtDesc(User user);
    boolean existsByUserAndName(User user, String name);
    List<Character> findAllByUser(User user);
    // User 객체로 해당 유저의 모든 캐릭터를 찾는 메소드
    List<Character> findByUser(User user);
}
