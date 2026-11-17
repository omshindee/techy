package com.devsocial.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static com.devsocial.push.WebPushCrypto.b64;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the push encryption against the worked example in RFC 8291 (section 5 / appendix A). */
class WebPushCryptoTest {

    @Test
    void matchesTheRfc8291Example() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        ECPublicKey asPublic = WebPushCrypto.publicKey(b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        ECPrivateKey asPrivate = WebPushCrypto.privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret, new KeyPair(asPublic, asPrivate), salt);

        String expected = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";
        assertEquals(expected, b64(body));
    }

    @Test
    void keysRoundTripAndVapidTokensVerify() throws Exception {
        KeyPair kp = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.encode((ECPublicKey) kp.getPublic());
        byte[] priv = WebPushCrypto.encode((ECPrivateKey) kp.getPrivate());
        assertEquals(65, pub.length);
        assertEquals(32, priv.length);
        ECPublicKey pub2 = WebPushCrypto.publicKey(pub);
        ECPrivateKey priv2 = WebPushCrypto.privateKey(priv);

        String auth = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com", "https://techy.example", 1_900_000_000L, priv2, pub);
        assertTrue(auth.startsWith("vapid t="));
        String jwt = auth.substring("vapid t=".length(), auth.indexOf(", k="));
        assertEquals(b64(pub), auth.substring(auth.indexOf(", k=") + 4));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertEquals("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,\"sub\":\"https://techy.example\"}", claims);

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(pub2);
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verify.verify(Base64.getUrlDecoder().decode(parts[2])));
        assertEquals(64, Base64.getUrlDecoder().decode(parts[2]).length);
    }
}
