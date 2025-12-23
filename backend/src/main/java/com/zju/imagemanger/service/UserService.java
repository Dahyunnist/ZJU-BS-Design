package com.zju.imagemanger.service;

import com.zju.imagemanger.dto.AuthRequest;
import com.zju.imagemanger.dto.AuthResponse;
import com.zju.imagemanger.entity.User;
import com.zju.imagemanger.repository.UserRepository;
import com.zju.imagemanger.config.JwtConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtConfig jwtConfig;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    @Transactional
    public AuthResponse register(AuthRequest request) {
        // 验证用户名
        if (request.getUsername().getBytes().length < 6) {
            throw new IllegalArgumentException("用户名长度必须至少6字节");
        }

        // 验证密码
        if (request.getPassword().getBytes().length < 6) {
            throw new IllegalArgumentException("密码长度必须至少6字节");
        }

        // 验证邮箱格式
        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        // 检查用户名和邮箱唯一性
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user = userRepository.save(user);

        // 生成token
        String token = jwtConfig.generateToken(user.getUsername());

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail());

        return new AuthResponse(token, userInfo);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtConfig.generateToken(user.getUsername());

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail());

        return new AuthResponse(token, userInfo);
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
}

