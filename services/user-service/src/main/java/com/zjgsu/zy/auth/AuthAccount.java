package com.zjgsu.zy.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "auth_accounts")
public class AuthAccount {
    public enum Role { USER, ADMIN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true, length = 40)
    public String username;
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Role role;
    @Column(nullable = false)
    public boolean enabled = true;
}
