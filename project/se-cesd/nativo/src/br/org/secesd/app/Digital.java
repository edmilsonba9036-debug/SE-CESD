package br.org.secesd.app;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Entrada pela digital.
 *
 * A digital em si é verificada pelo Android (BiometricPrompt no Android 9+,
 * FingerprintManager nos anteriores). Aqui guardamos apenas o usuário e a
 * senha do cofre, cifrados com uma chave do Keystore do aparelho que só é
 * usada DEPOIS de uma digital válida (operação amarrada ao CryptoObject).
 * Assim a senha nunca fica em texto puro e nenhum dedo errado abre o app.
 */
public final class Digital {

    private static final String ALIAS = "secesd-digital";
    private static final String ARQUIVO = "se-cesd-digital.bin";
    private static final int TAM_IV = 12;

    private Digital() {}

    // ------------------------------------------------------------------
    // Disponibilidade
    // ------------------------------------------------------------------

    /** O aparelho tem leitor de digital? */
    public static boolean leitorPresente(Context ctx) {
        try {
            android.hardware.fingerprint.FingerprintManager fm =
                    ctx.getSystemService(android.hardware.fingerprint.FingerprintManager.class);
            return fm != null && fm.isHardwareDetected();
        } catch (Exception e) {
            return false;
        }
    }

    /** Existe pelo menos uma digital cadastrada no aparelho? */
    public static boolean digitalCadastrada(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.hardware.biometrics.BiometricManager bm =
                        ctx.getSystemService(android.hardware.biometrics.BiometricManager.class);
                return bm != null
                        && bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
            }
            android.hardware.fingerprint.FingerprintManager fm =
                    ctx.getSystemService(android.hardware.fingerprint.FingerprintManager.class);
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Estado (habilitada = existe pacote salvo)
    // ------------------------------------------------------------------

    public static boolean habilitada(Context ctx) {
        return arquivo(ctx).exists();
    }

    public static void desabilitar(Context ctx) {
        arquivo(ctx).delete();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignorado) {
        }
    }

    private static File arquivo(Context ctx) {
        return new File(ctx.getFilesDir(), ARQUIVO);
    }

    // ------------------------------------------------------------------
    // Chave do Keystore (amarrada à digital)
    // ------------------------------------------------------------------

    private static KeyGenerator gerador() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .build());
        return kg;
    }

    /** Recria a chave amarrada às digitais ATUAIS (só ao habilitar). */
    public static Cipher cifradorHabilitar() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        SecretKey k = gerador().generateKey();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c;
    }

    /** Prepara a decifragem com o IV gravado no pacote. Falha se as digitais mudaram. */
    public static Cipher cifradorAbrir(Context ctx) throws Exception {
        byte[] pacote = ler(ctx);
        byte[] iv = Arrays.copyOfRange(pacote, 0, TAM_IV);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey k = (SecretKey) ks.getKey(ALIAS, null);
        if (k == null) throw new IllegalStateException("chave ausente");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
        return c;
    }

    // ------------------------------------------------------------------
    // Salvar / decifrar (sempre pós-autenticação, via cipher do CryptoObject)
    // ------------------------------------------------------------------

    /** Pós-prompt de habilitação: grava usuário+senha cifrados com a cipher autenticada. */
    public static void salvar(Context ctx, Cipher cipherAutenticada, String usuario, String senha) throws Exception {
        JSONObject dados = new JSONObject();
        dados.put("usuario", usuario);
        dados.put("senha", senha);
        byte[] iv = cipherAutenticada.getIV();
        byte[] cifrado = cipherAutenticada.doFinal(dados.toString().getBytes("UTF-8"));
        byte[] pacote = new byte[iv.length + cifrado.length];
        System.arraycopy(iv, 0, pacote, 0, iv.length);
        System.arraycopy(cifrado, 0, pacote, iv.length, cifrado.length);
        FileOutputStream f = new FileOutputStream(arquivo(ctx));
        f.write(pacote);
        f.close();
    }

    /** Pós-prompt de entrada: devolve {usuario, senha}. */
    public static String[] decifrar(Context ctx, Cipher cipherAutenticada) throws Exception {
        byte[] pacote = ler(ctx);
        byte[] cifrado = Arrays.copyOfRange(pacote, TAM_IV, pacote.length);
        String json = new String(cipherAutenticada.doFinal(cifrado), "UTF-8");
        JSONObject dados = new JSONObject(json);
        return new String[]{dados.getString("usuario"), dados.getString("senha")};
    }

    private static byte[] ler(Context ctx) throws Exception {
        FileInputStream f = new FileInputStream(arquivo(ctx));
        byte[] pacote = new byte[(int) arquivo(ctx).length()];
        int lido = 0;
        while (lido < pacote.length) {
            int n = f.read(pacote, lido, pacote.length - lido);
            if (n < 0) break;
            lido += n;
        }
        f.close();
        if (lido < TAM_IV + 1) throw new IllegalStateException("pacote truncado");
        return pacote;
    }

    /** Utilitário para telas: usuario em Base64 (evita import espalhado). */
    public static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }
}
