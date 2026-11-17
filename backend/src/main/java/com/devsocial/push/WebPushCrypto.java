package com.devsocial.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * The cryptography behind Web Push, using only the JDK:
 * <ul>
 *   <li>RFC 8291 message encryption ("aes128gcm" content coding, RFC 8188), so only the
 *       subscriber's browser can read a notification;</li>
 *   <li>RFC 8292 VAPID: an ES256-signed token that proves the push came from this server.</li>
 * </ul>
 * Keys use the formats browsers and other Web Push libraries use: a public key is an uncompressed
 * P-256 point (65 bytes), a private key is the 32-byte scalar, both base64url without padding.
 */
public final class WebPushCrypto {

    /** Record size written into the header; one record always holds a whole notification. */
    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {}

    // --- keys ----------------------------------------------------------------------------------

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) throw new InvalidKeyException("Not an uncompressed P-256 point");
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    public static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
    }

    public static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static byte[] encode(ECPrivateKey key) {
        byte[] out = new byte[32];
        copy32(key.getS(), out, 0);
        return out;
    }

    // --- RFC 8291 encryption -------------------------------------------------------------------

    /** Encrypts {@code plaintext} for a subscription with a fresh server key pair and salt. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, generateKeyPair(), salt);
    }

    /** Same, with the server key pair and salt given (tests use the RFC's example values). */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair as, byte[] salt)
            throws GeneralSecurityException {
        if (authSecret.length != 16) throw new InvalidKeyException("auth secret must be 16 bytes");
        byte[] asPublic = encode((ECPublicKey) as.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(as.getPrivate());
        ka.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = ka.generateSecret();

        // IKM = HKDF(salt = auth_secret, ikm = ecdh_secret, info = "WebPush: info\0" || ua_public || as_public, 32)
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        // A single, final record: plaintext followed by the 0x02 delimiter, no padding.
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[]{2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    // --- RFC 8292 VAPID ------------------------------------------------------------------------

    /** The {@code Authorization} header value for a push service at {@code audience} (its origin). */
    public static String vapidAuthorization(String audience, String subject, long expiresAtEpochSeconds,
                                            ECPrivateKey privateKey, byte[] publicKey) throws GeneralSecurityException {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64.encodeToString(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":" + expiresAtEpochSeconds
                + ",\"sub\":\"" + jsonEscape(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + claims;
        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format"); // raw r||s, as JWS wants
        es256.initSign(privateKey);
        es256.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + unsigned + "." + b64.encodeToString(es256.sign()) + ", k=" + b64.encodeToString(publicKey);
    }

    // --- helpers -------------------------------------------------------------------------------

    public static byte[] b64(String base64url) {
        return Base64.getUrlDecoder().decode(base64url.strip().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static void copy32(BigInteger n, byte[] out, int offset) {
        byte[] raw = n.toByteArray(); // big-endian, may have a leading 0 or be shorter than 32
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, out, offset + 32 - len, len);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
