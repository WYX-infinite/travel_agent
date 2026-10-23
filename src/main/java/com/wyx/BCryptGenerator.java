package com.wyx;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


public class BCryptGenerator {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: BCryptGenerator <password>");
        }
        String hash = new BCryptPasswordEncoder().encode(args[0]);
        System.out.println(hash);
    }
}
