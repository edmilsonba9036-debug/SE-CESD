package br.org.secesd;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backup do SE • CESD no Google Drive.
 *
 * Autorização SEM biblioteca externa (só o framework Android):
 *   1. Conta: AccountManager.newChooseAccountIntent — o seletor de contas
 *      Google do sistema (quem responde é o autenticador com.google do
 *      Google Play Services, que valida o pacote + SHA-1 no Google Cloud).
 *   2. Token: AccountManager.getAuthToken(conta, "oauth2:" + escopo) — o
 *      consentimento (escopo drive.file) é desenhado pelo próprio Play
 *      Services; se faltar consentimento, o Bundle traz KEY_INTENT e o app
 *      lança a tela e repete a chamada.
 *   3. Arquivo único na pasta "SE • CESD" (criada pelo app):
 *        - primeira vez: POST multipart (files.create)
 *        - depois:       PATCH media no mesmo fileId (só a versão mais recente)
 *   4. Conteúdo já criptografado pelo app web (registros AES-GCM do cofre
 *      IndexedDB, indecifráveis sem a senha do usuário) + TLS em trânsito.
 *
 * Ponte com o app web: window.SecesoDrive (comandos) e
 * window.SecesoDriveBridge (status e backup restaurado).
 */
public class DriveBackupManager {

    /** Códigos usados pela MainActivity em startActivityForResult. */
    public static final int RC_CONTA = 4204;
    public static final int RC_CONSENTIMENTO = 4205;

    /** Tipo de conta Google usada pelo seletor e pelo token. */
    static final String TIPO_CONTA = "com.google";

    /** Levantada quando falta interação do usuário (escolha de conta/consentimento). */
    private static class PrecisaInteracao extends IOException {
        PrecisaInteracao() {
            super("precisa-interacao");
        }
    }

    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private enum Acao { NENHUMA, CONECTAR, BACKUP, RESTAURAR }

    private Acao acaoPendente = Acao.NENHUMA;
    private String payloadPendente;
    private String tokenEmCache;

    public DriveBackupManager(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    // ------------------------------------------------------------------
    // Ponte JavaScript (window.SecesoDrive)
    // ------------------------------------------------------------------

    public class Ponte {

        /** O app web pergunta se a ponte nativa existe (só existe dentro do APK). */
        @JavascriptInterface
        public boolean disponivel() {
            return true;
        }

        /** Recebe o cofre (base64) do app web e envia ao Drive. */
        @JavascriptInterface
        public void backup(String payloadBase64) {
            iniciar(Acao.BACKUP, payloadBase64);
        }

        /** Baixa o backup do Drive e devolve ao app web para confirmação. */
        @JavascriptInterface
        public void restaurar() {
            iniciar(Acao.RESTAURAR, null);
        }

        /** Só confere/renova o acesso (botão "Conectar ao Drive"). */
        @JavascriptInterface
        public void conectar() {
            iniciar(Acao.CONECTAR, null);
        }
    }

    public Ponte novaPonte() {
        return new Ponte();
    }

    // ------------------------------------------------------------------
    // Resultados vindos da MainActivity
    // ------------------------------------------------------------------

    /** Resultado do seletor de contas do sistema. */
    public void receberConta(final Intent data) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String nome = data != null ? data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;
                if (TextUtils.isEmpty(nome)) {
                    falhar("Nenhuma conta escolhida.");
                    return;
                }
                prefs().edit().putString("contaNome", nome).apply();
                try {
                    executarPendente();
                } catch (PrecisaInteracao e) {
                    // seguindo no consentimento; nada a fazer aqui
                } catch (Exception e) {
                    falhar(erroAmigavel(e));
                }
            }
        });
    }

    /** Resultado da tela de consentimento do Google. */
    public void receberConsentimento(Intent data) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executarPendente();
                } catch (PrecisaInteracao e) {
                    aguardarInteracao();
                } catch (Exception e) {
                    falhar(erroAmigavel(e));
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Máquina de estados
    // ------------------------------------------------------------------

    private synchronized void iniciar(Acao acao, String payload) {
        acaoPendente = acao;
        payloadPendente = payload;
        tokenEmCache = null;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (contaAtual() == null) {
                        pedirConta();
                        return;
                    }
                    executarPendente();
                } catch (PrecisaInteracao e) {
                    aguardarInteracao();
                } catch (Exception e) {
                    falhar(erroAmigavel(e));
                }
            }
        });
    }

    private void executarPendente() throws Exception {
        Acao acao = acaoPendente;
        if (acao == Acao.BACKUP) {
            String payload = payloadPendente;
            payloadPendente = null;
            acaoPendente = Acao.NENHUMA;
            enviarBackup(payload);
        } else if (acao == Acao.RESTAURAR) {
            acaoPendente = Acao.NENHUMA;
            baixarBackup();
        } else if (acao == Acao.CONECTAR) {
            acaoPendente = Acao.NENHUMA;
            obterToken(true);
            status("conectar", true, "Conectado ao Google Drive ✓");
        }
    }

    private void falhar(String mensagem) {
        acaoPendente = Acao.NENHUMA;
        payloadPendente = null;
        status("erro", false, mensagem);
    }

    private void aguardarInteracao() {
        status("autorizacao", true, "Conclua a autorização no Google para continuar.");
    }

    // ------------------------------------------------------------------
    // Conta e token (framework AccountManager)
    // ------------------------------------------------------------------

    private Account contaAtual() {
        String nome = prefs().getString("contaNome", null);
        return nome == null ? null : new Account(nome, TIPO_CONTA);
    }

    /** Abre o seletor de contas Google do sistema (devolve false: aguardando). */
    private boolean pedirConta() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent seletor = AccountManager.newChooseAccountIntent(
                            null, null, new String[]{TIPO_CONTA}, true,
                            null, null, null, null);
                    activity.startActivityForResult(seletor, RC_CONTA);
                } catch (Exception e) {
                    status("erro", false,
                            "Este aparelho não tem conta Google nem o Play Services, necessários para o backup.");
                }
            }
        });
        return false;
    }

    /** Obtém o access token; lança PrecisaInteracao quando abre tela do Google. */
    private String obterToken(boolean renovar) throws IOException {
        if (!renovar && tokenEmCache != null) {
            return tokenEmCache;
        }
        Account conta = contaAtual();
        if (conta == null) {
            pedirConta();
            throw new PrecisaInteracao();
        }
        final AccountManager am = AccountManager.get(activity);
        final String tipo = "oauth2:" + DriveOAuth.SCOPE;
        Bundle resultado;
        try {
            // activity = null: o KEY_INTENT (se houver) volta no Bundle,
            // e o app lança a tela com startActivityForResult (determinístico).
            AccountManagerFuture<Bundle> futuro = am.getAuthToken(
                    conta, tipo, new Bundle(), null, null, null);
            resultado = futuro.getResult();
        } catch (OperationCanceledException e) {
            throw new IOException("O usuário cancelou a autorização do Google.");
        } catch (AuthenticatorException e) {
            throw new IOException("O serviço de contas Google não respondeu. Verifique se o Google Play Services está atualizado.");
        } catch (IOException e) {
            throw e;
        }
        Intent tela = (Intent) resultado.getParcelable(AccountManager.KEY_INTENT);
        if (tela != null) {
            final Intent intent = tela;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startActivityForResult(intent, RC_CONSENTIMENTO);
                }
            });
            throw new PrecisaInteracao();
        }
        String token = resultado.getString(AccountManager.KEY_AUTHTOKEN);
        if (TextUtils.isEmpty(token)) {
            throw new IOException("O Google não devolveu o token de acesso.");
        }
        tokenEmCache = token;
        return token;
    }

    // ------------------------------------------------------------------
    // Backup (upload) e restauração (download)
    // ------------------------------------------------------------------

    private void enviarBackup(String payloadBase64) throws Exception {
        if (TextUtils.isEmpty(payloadBase64)) {
            status("backup", false, "O cofre está vazio — nada para enviar.");
            return;
        }
        status("backup", true, "Preparando o backup…");

        JSONObject conteudo = new JSONObject();
        conteudo.put("formato", DriveOAuth.BACKUP_FORMAT);
        conteudo.put("versao", DriveOAuth.BACKUP_VERSION);
        conteudo.put("criadoEm", agoraISO());
        conteudo.put("aparelho", Build.MODEL == null ? "Android" : Build.MODEL);
        conteudo.put("dados", payloadBase64);
        byte[] bytes = conteudo.toString().getBytes(StandardCharsets.UTF_8);

        String arquivoId = null;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            String token = obterToken(tentativa > 0);
            String pastaId = garantirPasta(token);
            if (arquivoId == null) {
                arquivoId = localizarArquivo(token, pastaId);
            }
            status("backup", true, arquivoId == null
                    ? "Criando o backup no Google Drive…"
                    : "Atualizando o backup no Google Drive…");
            HttpURLConnection conn = abrir(arquivoId == null
                    ? DriveOAuth.DRIVE_UPLOAD_API + "files?uploadType=multipart&fields=id,name,createdTime"
                    : DriveOAuth.DRIVE_UPLOAD_API + "files/" + arquivoId
                      + "?uploadType=media&fields=id,modifiedTime",
                    token, arquivoId == null ? "POST" : "PATCH");
            int codigo;
            String corpo;
            try {
                if (arquivoId == null) {
                    JSONObject meta = new JSONObject();
                    meta.put("name", DriveOAuth.BACKUP_FILE_NAME);
                    JSONArray pais = new JSONArray();
                    pais.put(pastaId);
                    meta.put("parents", pais);
                    JSONObject props = new JSONObject();
                    props.put("criadoEm", conteudo.getString("criadoEm"));
                    props.put("aparelho", conteudo.getString("aparelho"));
                    meta.put("appProperties", props);
                    String limite = "secesd" + System.currentTimeMillis();
                    byte[] multipart = montarMultipart(limite, meta.toString(), bytes);
                    escreverCorpo(conn, multipart, "multipart/related; boundary=" + limite);
                } else {
                    escreverCorpo(conn, bytes, "application/json; charset=UTF-8");
                }
                codigo = conn.getResponseCode();
                corpo = lerCorpo(conn, codigo);
            } finally {
                conn.disconnect();
            }
            if (codigo >= 200 && codigo < 300) {
                SharedPreferences.Editor pref = prefs().edit();
                pref.putString("arquivoId", new JSONObject(corpo).optString("id", arquivoId));
                pref.putLong("ultimoBackup", System.currentTimeMillis());
                pref.apply();
                status("backup", true, "Backup salvo no Google Drive ✓");
                return;
            }
            if (codigo == 401 && tentativa == 0) {
                continue; // token vencido: renova e tenta de novo
            }
            throw new IOException("HTTP " + codigo + ": " + enxugar(corpo));
        }
        throw new IOException("HTTP 401: o Google não aceitou a sessão.");
    }

    private void baixarBackup() throws Exception {
        status("restaurar", true, "Procurando o backup no Drive…");

        String arquivoId = null;
        for (int tentativa = 0; tentativa < 2 && arquivoId == null; tentativa++) {
            String token = obterToken(tentativa > 0);
            String pastaId = localizarPasta(token);
            arquivoId = localizarArquivo(token, pastaId);
        }
        if (arquivoId == null) {
            status("restaurar", false, "Nenhum backup encontrado neste Drive. Envie um backup primeiro.");
            return;
        }

        status("restaurar", true, "Baixando o backup…");
        String corpo = null;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            String token = obterToken(tentativa > 0);
            HttpURLConnection conn = abrir(DriveOAuth.DRIVE_API + "files/" + arquivoId
                    + "?alt=media", token, "GET");
            int codigo;
            try {
                codigo = conn.getResponseCode();
                corpo = lerCorpo(conn, codigo);
            } finally {
                conn.disconnect();
            }
            if (codigo >= 200 && codigo < 300) {
                break;
            }
            if (codigo == 401 && tentativa == 0) {
                continue;
            }
            throw new IOException("HTTP " + codigo + ": " + enxugar(corpo));
        }

        JSONObject conteudo = new JSONObject(corpo);
        if (!DriveOAuth.BACKUP_FORMAT.equals(conteudo.optString("formato"))) {
            status("restaurar", false, "O arquivo do Drive não é um backup válido do SE • CESD.");
            return;
        }
        String dados = conteudo.optString("dados", "");
        if (TextUtils.isEmpty(dados)) {
            status("restaurar", false, "O backup está vazio.");
            return;
        }
        status("restaurar", true, "Backup de " + conteudo.optString("criadoEm", "?")
                + " baixado. Confirme no aviso para aplicar.");
        devolverAoApp(dados);
    }

    // ------------------------------------------------------------------
    // Pasta e arquivo no Drive
    // ------------------------------------------------------------------

    private String garantirPasta(String token) throws Exception {
        String salva = prefs().getString("pastaId", null);
        if (salva != null && existe(token, salva)) {
            return salva;
        }
        String id = localizarPasta(token);
        if (id == null) {
            JSONObject meta = new JSONObject();
            meta.put("name", DriveOAuth.BACKUP_FOLDER_NAME);
            meta.put("mimeType", "application/vnd.google-apps.folder");
            byte[] corpo = meta.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = abrir(DriveOAuth.DRIVE_API + "files?fields=id", token, "POST");
            int codigo;
            String resposta;
            try {
                escreverCorpo(conn, corpo, "application/json; charset=UTF-8");
                codigo = conn.getResponseCode();
                resposta = lerCorpo(conn, codigo);
            } finally {
                conn.disconnect();
            }
            if (codigo < 200 || codigo >= 300) {
                throw new IOException("HTTP " + codigo + " ao criar a pasta: " + enxugar(resposta));
            }
            id = new JSONObject(resposta).optString("id", null);
        }
        prefs().edit().putString("pastaId", id).apply();
        return id;
    }

    private String localizarPasta(String token) throws Exception {
        String q = "name='" + DriveOAuth.BACKUP_FOLDER_NAME + "'"
                + " and mimeType='application/vnd.google-apps.folder'"
                + " and trashed=false";
        return primeiroId(buscar(token, q));
    }

    private String localizarArquivo(String token, String pastaId) throws Exception {
        String q;
        if (pastaId != null) {
            q = "name='" + DriveOAuth.BACKUP_FILE_NAME + "'"
                    + " and '" + pastaId + "' in parents and trashed=false";
        } else {
            q = "name='" + DriveOAuth.BACKUP_FILE_NAME + "' and trashed=false";
        }
        return primeiroId(buscar(token, q));
    }

    private JSONArray buscar(String token, String q) throws Exception {
        String url = DriveOAuth.DRIVE_API + "files?q=" + URLEncoder.encode(q, "UTF-8")
                + "&spaces=drive&fields=files(id,name)&pageSize=5";
        HttpURLConnection conn = abrir(url, token, "GET");
        int codigo;
        String corpo;
        try {
            codigo = conn.getResponseCode();
            corpo = lerCorpo(conn, codigo);
        } finally {
            conn.disconnect();
        }
        if (codigo < 200 || codigo >= 300) {
            throw new IOException("HTTP " + codigo + " ao buscar: " + enxugar(corpo));
        }
        return new JSONObject(corpo).optJSONArray("files");
    }

    private boolean existe(String token, String id) throws Exception {
        HttpURLConnection conn = abrir(DriveOAuth.DRIVE_API + "files/" + id + "?fields=id", token, "GET");
        int codigo;
        try {
            codigo = conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
        return codigo >= 200 && codigo < 300;
    }

    private String primeiroId(JSONArray arquivos) {
        if (arquivos != null && arquivos.length() > 0) {
            return arquivos.optJSONObject(0).optString("id", null);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private HttpURLConnection abrir(String url, String token, String metodo) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(metodo);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(!"GET".equals(metodo));
        return conn;
    }

    private void escreverCorpo(HttpURLConnection conn, byte[] bytes, String contentType)
            throws IOException {
        conn.setFixedLengthStreamingMode(bytes.length);
        conn.setRequestProperty("Content-Type", contentType);
        OutputStream saida = conn.getOutputStream();
        saida.write(bytes);
        saida.flush();
        saida.close();
    }

    private byte[] montarMultipart(String limite, String metaJson, byte[] media) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(("--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        saida.write(metaJson.getBytes(StandardCharsets.UTF_8));
        saida.write(("\r\n--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        saida.write(media);
        saida.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return saida.toByteArray();
    }

    private String lerCorpo(HttpURLConnection conn, int codigo) throws IOException {
        InputStream fluxo = codigo >= 200 && codigo < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (fluxo == null) return "";
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] pedaco = new byte[8192];
        int lido;
        while ((lido = fluxo.read(pedaco)) != -1) {
            saida.write(pedaco, 0, lido);
        }
        fluxo.close();
        return saida.toString("UTF-8");
    }

    // ------------------------------------------------------------------
    // Respostas para o app web (via WebView)
    // ------------------------------------------------------------------

    private void status(final String fase, final boolean ok, final String mensagem) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject json = new JSONObject();
                    json.put("fase", fase);
                    json.put("ok", ok);
                    json.put("mensagem", mensagem);
                    webView.evaluateJavascript(
                            "window.SecesoDriveBridge && window.SecesoDriveBridge.onStatus(" + json + ");",
                            null);
                } catch (JSONException ignored) {
                }
            }
        });
    }

    private void devolverAoApp(final String base64) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // base64 não contém aspas nem barras invertidas: seguro embutir no literal.
                webView.evaluateJavascript(
                        "window.SecesoDriveBridge && window.SecesoDriveBridge.onRestore('" + base64 + "');",
                        null);
            }
        });
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private SharedPreferences prefs() {
        return activity.getSharedPreferences("drive-backup", Context.MODE_PRIVATE);
    }

    private String agoraISO() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }

    private String enxugar(String corpo) {
        if (corpo == null) return "";
        return corpo.length() > 300 ? corpo.substring(0, 300) + "…" : corpo;
    }

    private String erroAmigavel(Exception e) {
        String m = e.getMessage();
        if (m != null && m.contains("HTTP 403")) {
            return "O Google recusou a operação (403). Confira se a Google Drive API está ativa e se o escopo drive.file está na tela de consentimento do projeto.";
        }
        if (m != null && m.startsWith("HTTP ")) {
            return "O Google recusou a operação (" + enxugar(m) + ").";
        }
        return m != null && !m.isEmpty() ? m
                : "Sem conexão com o Google Drive. Verifique a internet e tente novamente.";
    }
}
