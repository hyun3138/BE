package com.example.Loark.Repository;

import com.example.Loark.Entity.PartyRun;
import com.example.Loark.Entity.PartyRunMember;
import com.example.Loark.Entity.PartyRunMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartyRunMemberRepository extends JpaRepository<PartyRunMember, PartyRunMemberId> {
    void deleteAllByPartyRunIn(List<PartyRun> partyRuns);
}
