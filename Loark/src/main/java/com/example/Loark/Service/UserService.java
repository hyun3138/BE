package com.example.Loark.Service;

import com.example.Loark.DTO.LoginRequest;
import com.example.Loark.DTO.RegisterRequest;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.Loark.DTO.LoginResponse;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LostarkApiClient lostarkApiClient;

    public void register(RegisterRequest request) {
        if(userRepository.existsByUserEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        
        boolean verified = lostarkApiClient.verifyNickname(request.getApiKey(), request.getNickname());
        if(!verified) {
            throw new IllegalArgumentException("닉네임 인증 실패 : API 키 오류 또는 닉네임 불일치");
        }

        User user = User.builder()
                .userEmail(request.getEmail())
                .userPassword(passwordEncoder.encode(request.getPassword()))
                .userApiKey(request.getApiKey())
                .userNickname(request.getNickname())
                .userCreatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUserEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));
        if (!passwordEncoder.matches(request.getPassword(), user.getUserPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtUtil.createToken(user.getUserEmail());

        return new LoginResponse(token, user.getUserNickname());
    }
}
