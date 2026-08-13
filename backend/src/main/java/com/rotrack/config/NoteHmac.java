package com.rotrack.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class NoteHmac {
    private final byte[] secret;
    private final boolean writesEnabled;

    public NoteHmac(String secret) {
        this(secret, true);
    }

    NoteHmac(String secret, boolean writesEnabled) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.writesEnabled = writesEnabled;
    }

    public void requireWritesEnabled() {
        if (!writesEnabled) throw new IllegalStateException("Notes writes are disabled");
    }

    public byte[] fingerprint(String title, String serializedContent, String attachment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String titlePart = title == null ? "N;" : "S" + title.length() + ":" + title;
            String attachmentPart = attachment == null ? "N;" : "S" + attachment.length() + ":" + attachment;
            String payload = titlePart + "C" + serializedContent.length() + ":" + serializedContent + attachmentPart;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not fingerprint note request", exception);
        }
    }

    public boolean matches(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }
}
