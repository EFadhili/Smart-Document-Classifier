package org.example.auth;

import java.util.ArrayList;
import java.util.List;

public class AdminService {
    // Hard-coded admin credentials
    private static final String ADMIN_EMAIL = "elton.mumalasi@strathmore.edu";
    private static final String ADMIN_PASSWORD = "admin123";

    // List of admin emails for quick checking
    private static final List<String> ADMIN_EMAILS = List.of(
            "elton.mumalasi@strathmore.edu",
            "administrator@legalapp.com"
    );

    public boolean isAdmin(String email) {
        if (email == null || email.isBlank()) return false;
        return ADMIN_EMAILS.contains(email.toLowerCase().trim());
    }

    public boolean validateAdminCredentials(String email, String password) {
        if (email == null || password == null) return false;
        return ADMIN_EMAIL.equalsIgnoreCase(email.trim()) &&
                ADMIN_PASSWORD.equals(password);
    }

    public List<String> getAdminEmails() {
        return new ArrayList<>(ADMIN_EMAILS);
    }
}