package com.invoice.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Password hash generator for test data only
 */
public class PasswordHashGenerator {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String adminPassword = "admin123";
        String userPassword = "user123";
        String clerkPassword = "clerk123";
        
        String adminHash = encoder.encode(adminPassword);
        String userHash = encoder.encode(userPassword);
        String clerkHash = encoder.encode(clerkPassword);
        
        System.out.println("Admin password hash: " + adminHash);
        System.out.println("User password hash: " + userHash);
        System.out.println("Clerk password hash: " + clerkHash);
        
        System.out.println("\nVerify admin123: " + encoder.matches(adminPassword, adminHash));
        System.out.println("Verify user123: " + encoder.matches(userPassword, userHash));
        System.out.println("Verify clerk123: " + encoder.matches(clerkPassword, clerkHash));
    }
}