package com.example.Loark.Service;

import com.example.Loark.DTO.LoginRequest;
import com.example.Loark.DTO.LoginResponse;
import com.example.Loark.DTO.RegisterRequest;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void 회원가입_성공() {
        RegisterRequest request = new RegisterRequest("test@example.com", "1234", "api-key", "testNickname");
        when(userRepository.existsByUserEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("1234")).thenReturn("encoded1234");
        userService.register(request);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 회원가입_중복_이메일() {
        RegisterRequest request = new RegisterRequest("test@example.com", "1234", "api-key", "testNickname");
        when(userRepository.existsByUserEmail("test@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> userService.register(request));
    }

    @Test
    void 로그인_성공() {
        User user = User.builder()
                .userEmail("test@example.com")
                .userPassword("encoded1234")
                .userCreatedAt(LocalDateTime.now())
                .build();
        LoginRequest request = new LoginRequest("test@example.com", "1234");
        when(userRepository.findByUserEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "encoded1234")).thenReturn(true);
        when(jwtUtil.createToken("test@example.com")).thenReturn("jwt-token");
        LoginResponse response = userService.login(request);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getNickname()).isNull();
    }

    @Test
    void 로그인_비밀번호_불일치() {
        User user = User.builder()
                .userEmail("test@example.com")
                .userPassword("encoded1234")
                .build();
        LoginRequest request = new LoginRequest("test@example.com", "wrongpass");
        when(userRepository.findByUserEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wronpass", "encoded1234")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> userService.login(request));
    }

    @Test
    void 로그인_이메일_없음() {
        LoginRequest request = new LoginRequest("no_user@example.com", "1234");

        when(userRepository.findByUserEmail("no_user@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.login(request));
    }

    @Test
    void 회원가입_중복_이메일_예외() {
        RegisterRequest request = new RegisterRequest("dup@example.com", "1234", "apikey", "testNickname");
        when(userRepository.existsByUserEmail("dup@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.register(request));
    }

    @Test
    void 로그인_비밀번호_틀림() {
        User user = User.builder()
                .userEmail("test@example.com")
                .userPassword("encoded1234")
                .build();

        LoginRequest request = new LoginRequest("test@example.com", "wrongpass");

        when(userRepository.findByUserEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "encoded1234")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userService.login(request));
    }



}
