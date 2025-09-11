package com.example.Loark.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {

    @PersistenceContext
    private final EntityManager entityManager;

    // 한글 클래스명 -> 영문 클래스명 매핑
    private static final Map<String, String> KOREAN_TO_ENGLISH_CLASS_MAP = Map.ofEntries(
        Map.entry("버서커", "Berserker"),
        Map.entry("디스트로이어", "Destroyer"),
        Map.entry("워로드", "Gunlancer"),
        Map.entry("홀리나이트", "Paladin"),
        Map.entry("슬레이어", "Slayer"),
        Map.entry("배틀마스터", "Wardancer"),
        Map.entry("인파이터", "Scrapper"),
        Map.entry("기공사", "Soulfist"),
        Map.entry("창술사", "Glaivier"),
        Map.entry("스트라이커", "Striker"),
        Map.entry("브레이커", "Breaker"),
        Map.entry("데빌헌터", "Deadeye"),
        Map.entry("블래스터", "Artillerist"),
        Map.entry("호크아이", "Sharpshooter"),
        Map.entry("스카우터", "Machinist"),
        Map.entry("건슬링어", "Gunslinger"),
        Map.entry("바드", "Bard"),
        Map.entry("서머너", "Summoner"),
        Map.entry("아르카나", "Arcanist"),
        Map.entry("소서리스", "Sorceress"),
        Map.entry("데모닉", "Shadowhunter"),
        Map.entry("블레이드", "Deathblade"),
        Map.entry("리퍼", "Reaper"),
        Map.entry("소울이터", "Souleater"),
        Map.entry("도화가", "Artist"),
        Map.entry("기상술사", "Aeromancer"),
        Map.entry("발키리", "Valkyrie"),
        Map.entry("환수사", "Wildsoul")
    );

    /**
     * 한글 클래스명을 기반으로 역할을 조회합니다.
     * @param koreanClassName 캐릭터의 한글 클래스명 (e.g., "바드")
     * @return 역할 (e.g., "support") 또는 null
     */
    public String getRoleByKoreanClassName(String koreanClassName) {
        if (koreanClassName == null || koreanClassName.isBlank()) {
            return null;
        }

        String englishClassName = KOREAN_TO_ENGLISH_CLASS_MAP.get(koreanClassName);
        if (englishClassName == null) {
            // 매핑에 없는 경우, 일단 null 반환
            return null;
        }

        // EntityManager를 사용하여 Native Query 실행
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT role FROM statistic.dim_classes WHERE class_name = :className"
            );
            query.setParameter("className", englishClassName);
            return (String) query.getSingleResult();
        } catch (NoResultException e) {
            // dim_classes 테이블에 해당 클래스가 없는 경우
            return null;
        }
    }
}
