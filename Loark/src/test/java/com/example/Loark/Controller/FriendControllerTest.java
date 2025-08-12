package com.example.Loark.Controller;

import com.example.Loark.DTO.FriendResponse;
import com.example.Loark.Entity.FriendStatus;
import com.example.Loark.Service.FriendService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(FriendController.class)
public class FriendControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void 친구요청_API_200() throws Exception {
        String body = "{\n  \"targetUserId\": 2\n}";
        mvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 1)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("친구 요청 완료")));
    }

    @Test
    void 수락_거절_API_정상() throws Exception {
        mvc.perform(post("/api/friends/requests/10/accept")
                        .header("X-User-Id", 2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("수락")));

        mvc.perform(post("/api/friends/requests/11/decline")
                        .header("X-User-Id", 2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("거절")));
    }

    @Test
    void 삭제_API_정상() throws Exception {
        mvc.perform(delete("/api/friends/5")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삭제 완료")));
    }

    @Test
    void 목록_API_페이지네이션_정렬맵핑() throws Exception {
        FriendResponse dto = FriendResponse.builder()
                .friendId(100L)
                .otherUserId(2L)
                .otherNickname("B")
                .status(FriendStatus.ACCEPTED)
                .createdAt(LocalDateTime.now())
                .respondedAt(LocalDateTime.now())
                .build();

        mvc.perform(get("/api/friends")
                        .param("status", "ACCEPTED")
                        .param("query", "B")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk());
    }

    @Test
    void 차단_차단해제_API_정상() throws Exception {
        String blockBody = "{\n  \"blockedUserId\": 2\n}";
        mvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 1)
                        .content(blockBody))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("차단 완료")));

        mvc.perform(delete("/api/blocks/2")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("차단 해제")));
    }
}
