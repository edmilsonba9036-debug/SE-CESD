package br.org.secesd.app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cofre criptografado do SE • CESD (nativo).
 *
 * Mesma segurança do app web: PBKDF2WithHmacSHA256 (120.000 iterações)
 * deriva a chave AES-256-GCM a partir da senha; os registros só existem
 * cifrados no disco. O "verificador" prova a senha sem expor os dados.
 */
public final class Cofre {

    public static final String MARCA_VERIFICADOR = "se-cesd-aberto";
    private static final int ITERACOES = 120000;

    /** Chave derivada da senha, viva só enquanto a sessão estiver aberta. */
    private static SecretKey chaveSessao;
    static String usuarioSessao;

    private Cofre() {}

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    public static boolean existe(Context ctx) {
        return arquivo(ctx).exists();
    }

    public static String usuarioGravado(Context ctx) {
        try {
            JSONObject c = lerBruto(ctx);
            return c == null ? "" : c.optString("usuario", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Cria o cofre com o primeiro acesso. Devolve erro ou null. */
    public static String criar(Context ctx, String usuario, String senha) {
        try {
            if (usuario == null || usuario.trim().length() < 3) return "O usuário deve ter pelo menos 3 caracteres.";
            if (usuario.contains(" ")) return "Não use espaços no usuário.";
            if (senha == null || senha.length() < 8) return "Use pelo menos 8 caracteres na senha.";
            if (senha.toLowerCase().contains(usuario.toLowerCase())) return "A senha não pode conter o usuário.";
            if (existe(ctx)) return "Já existe um acesso criado neste aparelho.";

            byte[] sal = aleatorio(16);
            SecretKey chave = chave(senha.toCharArray(), sal);
            byte[] iv = aleatorio(12);
            byte[] verif = cifrar(chave, iv, MARCA_VERIFICADOR);
            byte[] ivDados = aleatorio(12);
            JSONObject vazio = new JSONObject();
            vazio.put("cadastro", new JSONObject());
            vazio.put("trajetoria", new JSONArray());
            vazio.put("galeria", new JSONArray());
            byte[] dados = cifrar(chave, ivDados, vazio.toString());

            JSONObject cofre = new JSONObject();
            cofre.put("usuario", usuario.trim());
            cofre.put("resumoUsuario", resumo(usuario.trim() + "/" + usuario.trim()));
            cofre.put("sal", Base64.encodeToString(sal, Base64.NO_WRAP));
            cofre.put("ivVerif", Base64.encodeToString(iv, Base64.NO_WRAP));
            cofre.put("verif", Base64.encodeToString(verif, Base64.NO_WRAP));
            cofre.put("ivDados", Base64.encodeToString(ivDados, Base64.NO_WRAP));
            cofre.put("dados", Base64.encodeToString(dados, Base64.NO_WRAP));
            gravar(ctx, cofre.toString());

            chaveSessao = chave;
            usuarioSessao = usuario.trim();
            return null;
        } catch (Exception e) {
            return "Não foi possível criar o acesso: " + mensagem(e);
        }
    }

    /** Abre a sessão; devolve null quando ok ou a mensagem de erro. */
    public static String abrir(Context ctx, String usuario, String senha) {
        try {
            JSONObject c = lerBruto(ctx);
            if (c == null) return "Nenhum acesso criado neste aparelho.";
            if (!c.optString("usuario", "").equals(usuario == null ? "" : usuario.trim())) {
                return "Usuário ou senha incorretos.";
            }
            byte[] sal = Base64.decode(c.getString("sal"), Base64.NO_WRAP);
            SecretKey chave = chave(senha.toCharArray(), sal);
            byte[] ivVerif = Base64.decode(c.getString("ivVerif"), Base64.NO_WRAP);
            byte[] verif = Base64.decode(c.getString("verif"), Base64.NO_WRAP);
            String marca = decifrar(chave, ivVerif, verif);
            if (!MARCA_VERIFICADOR.equals(marca)) return "Usuário ou senha incorretos.";
            chaveSessao = chave;
            usuarioSessao = c.optString("usuario");
            return null;
        } catch (Exception e) {
            return "Usuário ou senha incorretos.";
        }
    }

    public static boolean sessaoAberta() {
        return chaveSessao != null;
    }

    public static void bloquear() {
        chaveSessao = null;
        usuarioSessao = null;
    }

    /** Lê os registros (decifrados). Chamar com sessão aberta. */
    public static JSONObject lerDados(Context ctx) throws Exception {
        JSONObject c = lerBruto(ctx);
        byte[] dados = Base64.decode(c.getString("dados"), Base64.NO_WRAP);
        byte[] ivDados = Base64.decode(c.getString("ivDados"), Base64.NO_WRAP);
        return new JSONObject(decifrar(chaveSessao, ivDados, dados));
    }

    /** Salva os registros cifrando de novo com IV novo. */
    public static void salvarDados(Context ctx, JSONObject dados) throws Exception {
        JSONObject c = lerBruto(ctx);
        byte[] ivDados = aleatorio(12);
        byte[] blob = cifrar(chaveSessao, ivDados, dados.toString());
        c.put("ivDados", Base64.encodeToString(ivDados, Base64.NO_WRAP));
        c.put("dados", Base64.encodeToString(blob, Base64.NO_WRAP));
        gravar(ctx, c.toString());
    }

    /** Substitui o cofre inteiro (restauração de backup). A sessão é encerrada. */
    public static void substituir(Context ctx, String cofreJson) throws Exception {
        JSONObject novo = new JSONObject(cofreJson);
        // valida minimamente
        novo.getString("sal");
        novo.getString("verif");
        novo.getString("dados");
        gravar(ctx, novo.toString());
        bloquear();
    }

    public static void apagarTudo(Context ctx) {
        arquivo(ctx).delete();
        bloquear();
    }

    /** O cofre completo em JSON (para o backup no Drive). */
    public static String exportar(Context ctx) throws Exception {
        return lerBruto(ctx).toString();
    }

    // ------------------------------------------------------------------
    // Primitivas
    // ------------------------------------------------------------------

    private static File arquivo(Context ctx) {
        return new File(ctx.getFilesDir(), "cofre.json");
    }

    private static JSONObject lerBruto(Context ctx) throws Exception {
        File f = arquivo(ctx);
        if (!f.exists()) return null;
        FileInputStream entrada = new FileInputStream(f);
        byte[] tudo = new byte[(int) f.length()];
        int lido = 0;
        while (lido < tudo.length) {
            int n = entrada.read(tudo, lido, tudo.length - lido);
            if (n < 0) break;
            lido += n;
        }
        entrada.close();
        return new JSONObject(new String(tudo, 0, lido, StandardCharsets.UTF_8));
    }

    private static void gravar(Context ctx, String json) throws Exception {
        File f = arquivo(ctx);
        File tmp = new File(ctx.getFilesDir(), "cofre.json.tmp");
        FileOutputStream saida = new FileOutputStream(tmp);
        saida.write(json.getBytes(StandardCharsets.UTF_8));
        saida.flush();
        saida.getFD().sync();
        saida.close();
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new Exception("Falha ao gravar o cofre.");
        }
    }

    private static SecretKey chave(char[] senha, byte[] sal) throws Exception {
        KeySpec spec = new PBEKeySpec(senha, sal, ITERACOES, 256);
        SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] derivada = fabrica.generateSecret(spec).getEncoded();
        return new SecretKeySpec(derivada, "AES");
    }

    private static byte[] cifrar(SecretKey chave, byte[] iv, String texto) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
        return cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String decifrar(SecretKey chave, byte[] iv, byte[] blob) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(blob), StandardCharsets.UTF_8);
    }

    private static byte[] aleatorio(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String resumo(String texto) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(h, Base64.NO_WRAP);
    }

    private static String mensagem(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
