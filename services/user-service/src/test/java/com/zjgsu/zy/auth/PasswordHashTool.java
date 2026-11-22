package com.zjgsu.zy.auth;

import java.io.Console;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashTool {
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("请从真实终端使用java -cp运行，不能用IDE管道");
        }
        char[] password = console.readPassword("Password (12+ characters): ");
        char[] confirm = console.readPassword("Confirm password: ");
        try {
            int bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining();
            if (password.length < 12 || bytes > 72
                    || !Arrays.equals(password, confirm)) {
                throw new IllegalArgumentException("密码至少12字符、最多72字节，两次输入一致");
            }
            System.out.println("BCrypt hash (cost=12):");
            System.out.println(new BCryptPasswordEncoder(12).encode(CharBuffer.wrap(password)));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }
}
