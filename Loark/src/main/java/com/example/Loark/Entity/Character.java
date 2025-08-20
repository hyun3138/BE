package com.example.Loark.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="characters",
        uniqueConstraints = @UniqueConstraint(name="ux_characters_user_name",
                columnNames={"user_id", "character_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Character {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="character_id")
    private Long characterId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="character_name", nullable=false, length=100)
    private String name;

    @Column(name="character_class", nullable=false, length=100)
    private String clazz;

    @Column(name="character_server", nullable=false, length=100)
    private String server;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @Column(name="is_main", nullable=false)
    private boolean main;

    @PrePersist @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    @JsonIgnore
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CharacterSpec> specs = new ArrayList<>();
}
