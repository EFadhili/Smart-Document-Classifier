package org.example.auth;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import org.example.auth.AuthManager;
import org.example.auth.TokenManager;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * Minimal desktop OAuth UI: opens browser to prompt user, asks them to paste code.
 * On success stores credential under a generated userId and notifies via callback.
 */
public class OAuthUI extends JFrame {
    private final JButton signInBtn = new JButton("Sign in with Google");
    private final JLabel status = new JLabel("Not signed in");
    private final AuthManager authManager;
    private final TokenManager tokenManager;
    private final Runnable onSuccess; // called with user signed in

    public OAuthUI(AuthManager authManager, TokenManager tokenManager, Runnable onSuccess) {
        this.authManager = authManager;
        this.tokenManager = tokenManager;
        this.onSuccess = onSuccess;

        setTitle("Sign in");
        setSize(420, 140);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        p.add(status, BorderLayout.CENTER);
        p.add(signInBtn, BorderLayout.SOUTH);
        add(p);

        signInBtn.addActionListener(ev -> startFlow());

        setVisible(true);
    }

    private void startFlow() {
        try {
            // use OOB redirect for desktop apps
            AuthorizationCodeRequestUrl authUrl = authManager.getFlow()
                    .newAuthorizationUrl()
                    .setRedirectUri("urn:ietf:wg:oauth:2.0:oob");

            Desktop.getDesktop().browse(new URI(authUrl.build()));
            String code = JOptionPane.showInputDialog(this, "Paste the authorization code here:");
            if (code == null || code.isBlank()) {
                status.setText("Sign-in cancelled");
                return;
            }

            // Exchange code for tokens
            AuthorizationCodeTokenRequest tokenRequest = authManager.getFlow()
                    .newTokenRequest(code)
                    .setRedirectUri("urn:ietf:wg:oauth:2.0:oob");
            TokenResponse tokenResponse = tokenRequest.execute();
            // create and store Credential in the flow's DataStore with generated userId
            String userId = "user-" + System.currentTimeMillis();
            var cred = authManager.getFlow().createAndStoreCredential(tokenResponse, userId);

            tokenManager.setCurrentUserId(userId);
            status.setText("Signed in as: " + userId);
            JOptionPane.showMessageDialog(this, "Signed in successfully as " + userId);
            // close this window and notify
            dispose();
            if (onSuccess != null) onSuccess.run();
        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("Sign-in failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Sign in failed: " + ex.getMessage());
        }
    }
}
