package com.example.Loark.Repository;

import com.example.Loark.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>{
    Optional<User> findByGoogleSub(String googleSub);
    boolean existsByGoogleSub(String googleSub);
    Optional<User> findByMainCharacter(String mainCharacter);
    Optional<User> findByMainCharacterIgnoreCase(String mainCharacter); // 대소문자 무시 검색
}
