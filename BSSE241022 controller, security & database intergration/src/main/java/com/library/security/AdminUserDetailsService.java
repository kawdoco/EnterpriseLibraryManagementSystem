package com.library.security;

import com.library.entity.AdminUser;
import com.library.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown administrator"));
        String role = (admin.getRole() == null || admin.getRole().isBlank())
                ? "ADMIN" : admin.getRole().trim().toUpperCase();
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())      // BCrypt hash; never logged
                .roles(role)
                .disabled(!admin.isEnabled())
                .build();
    }
}
