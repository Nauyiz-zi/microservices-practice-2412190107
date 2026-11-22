package com.zjgsu.zy.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "auth.bootstrap.enabled", havingValue = "true")
public class AuthBootstrap implements ApplicationRunner {
    private final AuthAccountRepository accounts;
    private final String adminName, adminHash, userName, userHash;

    public AuthBootstrap(AuthAccountRepository accounts,
                         @Value("${auth.bootstrap.admin-username}") String adminName,
                         @Value("${auth.bootstrap.admin-password-hash}") String adminHash,
                         @Value("${auth.bootstrap.user-username}") String userName,
                         @Value("${auth.bootstrap.user-password-hash}") String userHash) {
        this.accounts = accounts;
        this.adminName = adminName;
        this.adminHash = adminHash;
        this.userName = userName;
        this.userHash = userHash;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminName.equals(userName)) {
            throw new IllegalStateException("两个初始化账号名不能相同");
        }
        createIfMissing(adminName, adminHash, AuthAccount.Role.ADMIN);
        createIfMissing(userName, userHash, AuthAccount.Role.USER);
    }

    private void createIfMissing(String name, String hash, AuthAccount.Role role) {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,39}")
                || !hash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalStateException(
                    "初始化用户名无效或缺少工具生成的BCrypt cost=12哈希");
        }
        var existing = accounts.findByUsername(name);
        if (existing.isPresent()) {
            checkRole(existing.get(), role);
            return; // 不覆盖已有账号密码、启用状态或角色。
        }
        AuthAccount account = new AuthAccount();
        account.username = name;
        account.passwordHash = hash;
        account.role = role;
        try {
            // Repository事务在方法返回或抛错时结束，catch之后可重新查询。
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            // 两个实例并发初始化，唯一约束保证只创建一份。
            var winner = accounts.findByUsername(name).orElseThrow(() -> ex);
            checkRole(winner, role);
        }
    }

    private void checkRole(AuthAccount account, AuthAccount.Role role) {
        if (account.role != role) {
            throw new IllegalStateException("初始化账号已存在且角色不一致");
        }
    }
}
