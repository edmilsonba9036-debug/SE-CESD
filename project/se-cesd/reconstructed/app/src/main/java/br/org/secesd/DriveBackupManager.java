package br.org.secesd;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

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
 * Fluxo (cliente OAuth tipo Android — sem redirect no navegador):
 *   1. Autorização: GoogleSignIn.requestPermissions(...) com o escopo drive.file.
 *      O Play Services valida sozinho o pacote + SHA-1 registrados no Google Cloud.
 *   2. Token: GoogleAuthUtil.getToken(conta, "oauth2:" + escopo), renovado quando
 *      o Drive responde 401.
 *   3. Arquivo único na pasta "SE • CESD" (criada pelo app):
 *        - primeira vez: POST multipart (files.create)
 *        - depois:       PATCH media no mesmo fileId (o Drive mantém só a versão mais recente)
 *   4. O conteúdo já vai criptografado do app web (registros AES-GCM do cofre
 *      IndexedDB, indecifráveis sem a senha do usuário) + TLS em trânsito.
 *
 * Ponte com o app web: window.SecesoDrive (recebe comandos) e
 * window.SecesoDriveBridge (recebe status e o backup restaurado).
 */
public class DriveBackupManager {

    /** Códigos usados pela MainActivity em startActivityForResult. */
    public static final int RC_AUTH_PERMISSION = 4202;
    public static final int RC_RECOVERABLE = 4203;

    /** Levantada quando o Google pediu um diálogo extra de permissão. */
    private static class PrecisaAutorizacao extends IOException {
        PrecisaAutorizacao() {
            super("precisa-autorizacao");
        }
    }

    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private enum Acao { NENHUMA, CONECTAR, BACKUP, RESTAURAR }

    private Acao acaoPendente = Acao.NENHUMA;
    private String payloadPendente;

    private GoogleSignInAccount conta;
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

    /** MainActivity → resultado do consentimento do GoogleSignIn. */
    public void receberResultadoAutorizacao(Intent data) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    conta = GoogleSignIn.getSignedInAccountFromIntent(data).getResult();
                    if (conta == null || conta.getAccount() == null) {
                        falhar("A autorização do Google não foi concluída.");
                        return;
                    }
                    executarPendente();
                } catch (PrecisaAutorizacao e) {
                    aguardarAutorizacao();
                } catch (Exception e) {
                    falhar(erroAmigavel(e));
                }
            }
        });
    }

    /** MainActivity → resultado do diálogo do UserRecoverableAuthException. */
    public void receberResultadoRecuperavel(int resultCode, Intent data) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executarPendente();
                } catch (PrecisaAutorizacao e) {
                    aguardarAutorizacao();
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
                    if (!servicosOk()) {
                        falhar("Este aparelho não tem o Google Play Services, necessário para o backup no Drive.");
                        return;
                    }
                    if (!garantirAutorizacao()) {
                        return; // aguardando o consentimento; continua em receberResultadoAutorizacao
                    }
                    executarPendente();
                } catch (PrecisaAutorizacao e) {
                    aguardarAutorizacao();
                } catch (Exception e) {
                    falhar(erroAmigavel(e));
                }
            }
        });
    }

    private void executarPendente() throws Exception {
        Acao acao = acaoPendente;
        if (acao == Acao.BACKUP) {
            enviarBackup(payloadPendente);
            payloadPendente = null;
            acaoPendente = Acao.NENHUMA;
        } else if (acao == Acao.RESTAURAR) {
            baixarBackup();
            acaoPendente = Acao.NENHUMA;
        } else if (acao == Acao.CONECTAR) {
            obterToken(true);
            acaoPendente = Acao.NENHUMA;
            status("conectar", true, "Conectado ao Google Drive ✓");
        }
    }

    private void falhar(String mensagem) {
        acaoPendente = Acao.NENHUMA;
        payloadPendente = null;
        status("erro", false, mensagem);
    }

    private void aguardarAutorizacao() {
        status("autorizacao", true, "Conclua a autorização no Google para continuar.");
    }

    // ------------------------------------------------------------------
    // Autorização
    // ------------------------------------------------------------------

    private boolean servicosOk() {
        return GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS;
    }

    /** Garante conta autorizada; devolve false se lançou o diálogo do Play Services. */
    private boolean garantirAutorizacao() throws Exception {
        if (conta != null && conta.getAccount() != null) {
            return true;
        }
        conta = GoogleSignIn.getLastSignedInAccount(activity);
        if (conta != null && conta.getAccount() != null) {
            return true;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GoogleSignIn.requestPermissions(
                        activity,
                        RC_AUTH_PERMISSION,
                        GoogleSignIn.getLastSignedInAccount(activity),
                        new Scope(DriveOAuth.SCOPE));
            }
        });
        return false;
    }

    private String obterToken(boolean renovar) throws IOException, GoogleAuthException {
        if (!renovar && tokenEmCache != null) {
            return tokenEmCache;
        }
        try {
            Account account = conta.getAccount();
            String token = GoogleAuthUtil.getToken(activity, account, "oauth2:" + DriveOAuth.SCOPE);
            tokenEmCache = token;
            return token;
        } catch (UserRecoverableAuthException e) {
            final Intent intencao = e.getIntent();
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startActivityForResult(intencao, RC_RECOVERABLE);
                }
            });
            throw new PrecisaAutorizacao();
        }
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

    private byte[] montarMultipart(String metaJson, byte[] media) throws IOException {
        String limite = "secesd" + System.currentTimeMillis();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(("--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        saida.write(metaJson.getBytes(StandardCharsets.UTF_8));
        saida.write(("\r\n--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        saida.write(media);
        saida.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] tudo = saida.toByteArray();
        // O boundary precisa bater com o do cabeçalho — guarda no primeiro byte do objeto
        // não é possível; então devolvemos via variável de instância simples:
        limiteAtual = limite;
        return tudo;
    }

    /** Boundary usado pelo montarMultipart (definido junto). */
    private String limiteAtual;

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
        if (e instanceof UserRecoverableAuthException) {
            return "Faltou permitir o acesso no Google.";
        }
        String m = e.getMessage();
        if (m != null && m.contains("HTTP 403")) {
            return "O Google recusou a operação (403). Confira se a Google Drive API está ativa e se o escopo drive.file está na tela de consentimento do projeto.";
        }
        if (m != null && m.startsWith("HTTP ")) {
            return "O Google recusou a operação (" + enxugar(m) + ").";
        }
        return "Sem conexão com o Google Drive. Verifique a internet e tente novamente.";
    }
}

        return "Sem conexão com o Google Drive. Verifique a internet e tente novamente.";
    }
}
