package br.org.secesd.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backup no Google Drive via framework (AccountManager + Drive REST v3),
 * sem bibliotecas externas e sem fluxo de navegador.
 *
 * O consentimento do escopo drive.file é desenhado pelo serviço de contas
 * Google (Play Services) do aparelho, que valida pacote + SHA-1.
 */
public class DriveBackup {

    public static final int RC_CONTA = 4204;
    public static final int RC_CONSENTIMENTO = 4205;

    static final String TIPO_CONTA = "com.google";
    static final String ESCOPO = "https://www.googleapis.com/auth/drive.file";
    static final String API = "https://www.googleapis.com/drive/v3/";
    static final String API_UPLOAD = "https://www.googleapis.com/upload/drive/v3/";
    static final String NOME_PASTA = "SE • CESD";
    static final String NOME_ARQUIVO = "SE-CESD-backup.json";

    /** Aviso para a tela e entrega do backup baixado. */
    public interface Ouvinte {
        void status(String mensagem, boolean ok);
        void restaurado(String cofreJson);
    }

    private final Activity atividade;
    private final Ouvinte ouvinte;
    private final ExecutorService fila = Executors.newSingleThreadExecutor();
    private String token;

    public DriveBackup(Activity atividade, Ouvinte ouvinte) {
        this.atividade = atividade;
        this.ouvinte = ouvinte;
    }

    private enum Acao { NENHUMA, CONECTAR, ENVIAR, RESTAURAR }
    private Acao pendente = Acao.NENHUMA;
    private String payloadPendente;

    // ------------------------------------------------------------------
    // Ações
    // ------------------------------------------------------------------

    public void conectar() {
        iniciar(Acao.CONECTAR, null, false);
    }

    public void enviar(String cofreJson) {
        iniciar(Acao.ENVIAR, cofreJson, false);
    }

    /** Envio automático (sem mensagens de progresso; só o resultado final). */
    public void enviarAutomatico(String cofreJson) {
        iniciar(Acao.ENVIAR, cofreJson, true);
    }

    /** Automático ligado? (padrão: ligado) */
    public static boolean autoAtivo(Activity a) {
        return a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getBoolean("autoBackup", true);
    }

    public static void definirAuto(Activity a, boolean ligado) {
        a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .edit().putBoolean("autoBackup", ligado).apply();
    }

    /** Há conexão pronta (conta escolhida ou cliente do navegador configurado)? */
    public boolean prontoParaEnviar() {
        return browserConfigurado() || conta() != null;
    }

    /** Data/hora do último backup bem-sucedido (0 = nunca). */
    public static long ultimoBackup(Activity a) {
        return a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getLong("ultimoBackup", 0);
    }

    public void restaurar() {
        iniciar(Acao.RESTAURAR, null, false);
    }

    /** Chamar a partir de Activity.onActivityResult. Devolve true se era do Drive. */
    public boolean onActivityResult(int requestCode, Intent data) {
        if (requestCode == RC_CONTA) {
            String nome = data != null ? data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;
            if (nome == null) {
                avisar("Nenhuma conta escolhida.", false);
            } else {
                salvarConta(nome);
                continuar();
            }
            return true;
        }
        if (requestCode == RC_CONSENTIMENTO) {
            continuar();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Fluxo
    // ------------------------------------------------------------------

    private boolean silencioso;

    private synchronized void iniciar(Acao acao, String payload, boolean quieto) {
        silencioso = quieto;
        pendente = acao;
        payloadPendente = payload;
        token = null;
        fila.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (conta() == null) {
                        escolherConta();
                        return;
                    }
                    executar();
                } catch (PrecisaTela e) {
                    avisar("Conclua a autorização no Google para continuar.", true);
                } catch (Exception e) {
                    avisar(erroAmigavel(e), false);
                }
            }
        });
    }

    private void continuar() {
        fila.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executar();
                } catch (PrecisaTela e) {
                    avisar("Conclua a autorização no Google para continuar.", true);
                } catch (Exception e) {
                    avisar(erroAmigavel(e), false);
                }
            }
        });
    }

    private void executar() throws Exception {
        Acao acao = pendente;
        pendente = Acao.NENHUMA;
        if (acao == Acao.CONECTAR) {
            obterToken();
            avisar("Conectado ao Google Drive ✓", true);
        } else if (acao == Acao.ENVIAR) {
            String payload = payloadPendente;
            payloadPendente = null;
            enviar(payload);
        } else if (acao == Acao.RESTAURAR) {
            baixar();
        }
    }

    private void avisar(final String msg, final boolean ok) {
        if (ouvinte == null) return;
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ouvinte.status(msg, ok);
            }
        });
    }

    // ------------------------------------------------------------------
    // Conta e token
    // ------------------------------------------------------------------

    private Account conta() {
        String nome = atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("contaNome", null);
        return nome == null ? null : new Account(nome, TIPO_CONTA);
    }

    private void salvarConta(String nome) {
        atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .edit().putString("contaNome", nome).apply();
    }

    private void escolherConta() throws PrecisaTela {
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent seletor = AccountManager.newChooseAccountIntent(
                            null, null, new String[]{TIPO_CONTA}, true, null, null, null, null);
                    atividade.startActivityForResult(seletor, RC_CONTA);
                } catch (Exception e) {
                    avisar("Este aparelho não tem conta Google (Play Services) para o backup.", false);
                }
            }
        });
        throw new PrecisaTela();
    }

    private static class PrecisaTela extends IOException {
        PrecisaTela() { super("precisa-tela"); }
    }

    /**
     * Traduz a falha do autenticador do Google em orientação prática.
     * As causas dominantes são: (1) o app não está registrado no Google Cloud
     * com o pacote+SHA-1 corretos; (2) a conta não é testadora na tela de
     * consentimento (escopo drive.file é restrito); (3) sem internet.
     */
    private String orientarFalhaAutenticador(android.accounts.AuthenticatorException e) {
        String detalhe = String.valueOf(e.getMessage());
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            detalhe += " | " + e.getCause().getMessage();
        }
        String t = detalhe.toLowerCase();
        boolean naoReconhece =
                t.contains("invalid") || t.contains("unregistered") || t.contains("unsuccessful")
                        || t.contains("client") || t.contains("notfound") || t.equals("null");
        if (naoReconhece) {
            return "O Google não reconheceu este app para o Drive. No Google Cloud Console → APIs e "
                    + "Serviços → Credenciais, confirme que existe um cliente OAuth do tipo ANDROID com "
                    + "Nome do pacote = br.org.secesd.debug e SHA-1 = 25:32:BE:B9:BD:65:7D:8C:92:22:5A:60:57:"
                    + "A7:5D:37:00:57:21:7D. Em Tela de consentimento → Usuários de teste, adicione a sua "
                    + "conta Google. Aguarde 5 minutos, reinicie o Wi-Fi/dados e toque em Conectar de novo. "
                    + "(Detalhe técnico: " + detalhe + ")";
        }
        if (t.contains("network")) {
            return "Sem conexão com o Google. Verifique a internet e tente novamente.";
        }
        return "Falha no serviço de contas Google. Confira o cliente Android no Cloud Console "
                + "(pacote br.org.secesd.debug + SHA-1) e tente novamente. (Detalhe: " + detalhe + ")";
    }

    // ------------------------------------------------------------------
    // Plano B: login pelo NAVEGADOR (cliente OAuth tipo Desktop + PKCE +
    // redirect loopback 127.0.0.1 — permitido pelo Google para desktop).
    // Independente de pacote/SHA-1 e do Play Services do aparelho.
    // ------------------------------------------------------------------

    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/auth";

    private long tokenExpiraEm = 0;

    private boolean browserConfigurado() {
        SharedPreferences p = atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE);
        return !p.getString("browserClientId", "").isEmpty();
    }

    /** ID/segredo do cliente Desktop configurados pelo usuário no app. */
    public String idBrowser() {
        return atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("browserClientId", "");
    }

    public void configurarBrowser(String clientId, String clientSecret) {
        atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .edit()
                .putString("browserClientId", clientId == null ? "" : clientId.trim())
                .putString("browserClientSecret", clientSecret == null ? "" : clientSecret.trim())
                .apply();
        token = null;
        tokenExpiraEm = 0;
    }

    public void removerConfigBrowser() {
        SharedPreferences p = atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE);
        p.edit().remove("browserClientId").remove("browserClientSecret")
                .remove("browserRefreshToken").apply();
        token = null;
        tokenExpiraEm = 0;
    }


    /** Segredo salvo, apenas para preencher o campo de edição. */
    public String segredoParaEdicao() {
        return atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("browserClientSecret", "");
    }

    private String segredoBrowser() {
        return atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("browserClientSecret", "");
    }

    private static String base64url(byte[] bytes) {
        return android.util.Base64.encodeToString(bytes,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }

    /** Fluxo completo: PKCE → navegador → loopback → troca do código → tokens. */
    private void loginPeloNavegador() throws IOException {
        final String clientId = idBrowser();
        if (clientId.isEmpty()) {
            throw new IOException("Configure o ID do cliente (navegador) na tela de backup antes de conectar.");
        }
        avisar("Abrindo o Google no navegador…", true);

        byte[] aleatorio = new byte[48];
        new java.security.SecureRandom().nextBytes(aleatorio);
        final String verificador = base64url(aleatorio);
        byte[] resumo;
        try {
            resumo = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(verificador.getBytes("US-ASCII"));
        } catch (Exception e) {
            throw new IOException("Falha ao gerar o desafio PKCE.");
        }
        final String desafio = base64url(resumo);
        final String estado = base64url(aleatorio, 0, 12);

        ServerSocket servidor;
        try {
            servidor = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            throw new IOException("Não foi possível abrir a porta local para o retorno do Google.");
        }
        final String redirect = "http://127.0.0.1:" + servidor.getLocalPort() + "/callback";

        final String url = AUTH_ENDPOINT
                + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(redirect, "UTF-8")
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(ESCOPO, "UTF-8")
                + "&code_challenge=" + URLEncoder.encode(desafio, "UTF-8")
                + "&code_challenge_method=S256"
                + "&state=" + URLEncoder.encode(estado, "UTF-8")
                + "&access_type=offline&prompt=consent";
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent navegador = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    navegador.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    atividade.startActivity(navegador);
                } catch (Exception e) {
                    avisar("Nenhum navegador disponível neste aparelho.", false);
                }
            }
        });

        String codigo = null;
        try {
            servidor.setSoTimeout(300000); // 5 min para o usuário concluir
            Socket cliente = servidor.accept();
            java.io.BufferedReader entrada = new java.io.BufferedReader(
                    new java.io.InputStreamReader(cliente.getInputStream(), "US-ASCII"));
            String linha = entrada.readLine(); // "GET /callback?... HTTP/1.1"
            java.io.OutputStream bruto = cliente.getOutputStream();
            if (linha != null && linha.contains("/callback")) {
                String alvo = linha.split(" ")[1];
                String consulta = alvo.contains("?") ? alvo.substring(alvo.indexOf('?') + 1) : "";
                String erro = parametro(consulta, "error");
                String voltaEstado = parametro(consulta, "state");
                if (erro != null) {
                    respostaHtml(bruto, "Autorização não concluída (" + erro + "). Volte ao app.");
                    entrada.close(); bruto.close(); cliente.close();
                    throw new IOException("O Google retornou: " + erro);
                }
                if (voltaEstado == null || !estado.equals(voltaEstado)) {
                    respostaHtml(bruto, "Resposta inválida. Volte ao app e tente de novo.");
                    entrada.close(); bruto.close(); cliente.close();
                    throw new IOException("Resposta do Google com estado inválido.");
                }
                codigo = parametro(consulta, "code");
                respostaHtml(bruto, "<b>Autorizado!</b><br>Pode fechar esta aba e voltar ao aplicativo SE • CESD.");
            }
            entrada.close();
            bruto.close();
            cliente.close();
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("O navegador não voltou ao app (5 min esgotados). Tente de novo.");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao receber o retorno do Google no aparelho.");
        } finally {
            try {
                servidor.close();
            } catch (IOException ignored) {
            }
        }

        if (codigo == null || codigo.isEmpty()) {
            throw new IOException("O Google não devolveu o código de autorização.");
        }

        // Troca do código pelos tokens (PKCE + segredo do cliente instalado).
        String corpo = "code=" + URLEncoder.encode(codigo, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&client_secret=" + URLEncoder.encode(segredoBrowser(), "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(redirect, "UTF-8")
                + "&grant_type=authorization_code"
                + "&code_verifier=" + URLEncoder.encode(verificador, "UTF-8");
        JSONObject resposta = postarFormulario(TOKEN_ENDPOINT, corpo);
        salvarTokens(resposta);
        avisar("Conectado pelo navegador ✓", true);
    }

    private static String base64url(byte[] bytes, int de, int ate) {
        byte[] pedaco = new byte[ate - de];
        System.arraycopy(bytes, de, pedaco, pedaco.length == bytes.length ? 0 : 0, pedaco.length);
        return base64url(pedaco);
    }

    private String parametro(String consulta, String nome) {
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            if (igual < 0) continue;
            if (par.substring(0, igual).equals(nome)) {
                try {
                    return URLDecoder.decode(par.substring(igual + 1), "UTF-8");
                } catch (Exception e) {
                    return par.substring(igual + 1);
                }
            }
        }
        return null;
    }

    private void respostaHtml(java.io.OutputStream bruto, String html) throws IOException {
        byte[] pagina = ("<html><head><meta charset='utf-8'><title>SE • CESD</title></head>"
                + "<body style='font-family:sans-serif;padding:28px;text-align:center'>" + html
                + "</body></html>").getBytes(StandardCharsets.UTF_8);
        String cabecalho = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + pagina.length + "\r\nConnection: close\r\n\r\n";
        bruto.write(cabecalho.getBytes(StandardCharsets.UTF_8));
        bruto.write(pagina);
        bruto.flush();
    }

    private JSONObject postarFormulario(String endpoint, String formulario) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(formulario.getBytes(StandardCharsets.UTF_8).length);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        java.io.OutputStream saida = conn.getOutputStream();
        saida.write(formulario.getBytes(StandardCharsets.UTF_8));
        saida.flush();
        saida.close();
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) {
            throw new IOException("O Google recusou a troca de tokens (HTTP " + codigo + ": " + resumo(corpo) + ").");
        }
        try {
            return new JSONObject(corpo);
        } catch (JSONException e) {
            throw new IOException("Resposta inválida do Google na troca de tokens.");
        }
    }

    private void salvarTokens(JSONObject resposta) throws IOException {
        String acesso = resposta.optString("access_token", "");
        if (acesso.isEmpty()) throw new IOException("O Google não devolveu o token de acesso.");
        token = acesso;
        long segundos = resposta.optLong("expires_in", 3600);
        tokenExpiraEm = System.currentTimeMillis() + (segundos - 120) * 1000L;
        String renovacao = resposta.optString("refresh_token", "");
        if (!renovacao.isEmpty()) {
            atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                    .edit().putString("browserRefreshToken", renovacao).apply();
        }
    }

    private String obterTokenNavegador() throws IOException {
        if (token != null && System.currentTimeMillis() < tokenExpiraEm) return token;
        String renovacao = atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("browserRefreshToken", "");
        if (renovacao.isEmpty()) {
            loginPeloNavegador();
            return token;
        }
        String corpo = "client_id=" + URLEncoder.encode(idBrowser(), "UTF-8")
                + "&client_secret=" + URLEncoder.encode(segredoBrowser(), "UTF-8")
                + "&grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(renovacao, "UTF-8");
        salvarTokens(postarFormulario(TOKEN_ENDPOINT, corpo));
        return token;
    }

    private String obterToken() throws IOException {
        if (token != null && (!browserConfigurado() || System.currentTimeMillis() < tokenExpiraEm)) {
            return token;
        }
        if (browserConfigurado()) {
            return obterTokenNavegador();
        }
        Account conta = conta();
        if (conta == null) {
            escolherConta();
            throw new PrecisaTela();
        }
        AccountManager am = AccountManager.get(atividade);
        Bundle resultado;
        try {
            AccountManagerFuture<Bundle> futuro = am.getAuthToken(
                    conta, "oauth2:" + ESCOPO, new Bundle(), null, null, null);
            resultado = futuro.getResult();
        } catch (android.accounts.OperationCanceledException e) {
            throw new IOException("Você cancelou a autorização do Google.");
        } catch (android.accounts.AuthenticatorException e) {
            throw new IOException(orientarFalhaAutenticador(e));
        } catch (IOException e) {
            String d = String.valueOf(e.getMessage());
            if (d.contains("NetworkError") || d.toLowerCase().contains("network")
                    || d.toLowerCase().contains("timeout")) {
                throw new IOException("Sem conexão com o Google. Verifique a internet e tente novamente.");
            }
            throw new IOException("Falha ao falar com o Google: " + d);
        }
        Intent tela = (Intent) resultado.getParcelable(AccountManager.KEY_INTENT);
        if (tela != null) {
            final Intent i = tela;
            atividade.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    atividade.startActivityForResult(i, RC_CONSENTIMENTO);
                }
            });
            throw new PrecisaTela();
        }
        String t = resultado.getString(AccountManager.KEY_AUTHTOKEN);
        if (t == null || t.isEmpty()) throw new IOException("O Google não devolveu o token de acesso.");
        token = t;
        return token;
    }

    // ------------------------------------------------------------------
    // Enviar / baixar
    // ------------------------------------------------------------------

    /** Ação pendente… */
    private void executarEnvio(String cofreJson) throws Exception {
        if (!silencioso) avisar("Preparando o backup…", true);
        JSONObject conteudo = new JSONObject();
        conteudo.put("formato", "se-cesd-backup");
        conteudo.put("versao", 1);
        conteudo.put("criadoEm", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        conteudo.put("aparelho", android.os.Build.MODEL == null ? "Android" : android.os.Build.MODEL);
        conteudo.put("dados", cofreJson);
        byte[] bytes = conteudo.toString().getBytes(StandardCharsets.UTF_8);

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            try {
                String t = obterToken();
                String pastaId = garantirPasta(t);
                String arquivoId = localizarArquivo(t, pastaId);
                final boolean novo = arquivoId == null;
                if (!silencioso) {
                    avisar(novo ? "Criando o backup no Google Drive…" : "Atualizando o backup no Google Drive…", true);
                }
                HttpURLConnection conn;
                if (novo) {
                    JSONObject meta = new JSONObject();
                    meta.put("name", NOME_ARQUIVO);
                    org.json.JSONArray pais = new org.json.JSONArray();
                    pais.put(pastaId);
                    meta.put("parents", pais);
                    String limite = "secesd" + System.currentTimeMillis();
                    byte[] corpo = multipart(limite, meta.toString(), bytes);
                    conn = abrir(API_UPLOAD + "files?uploadType=multipart&fields=id", t, "POST");
                    conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + limite);
                    escrever(conn, corpo);
                } else {
                    conn = abrir(API_UPLOAD + "files/" + arquivoId + "?uploadType=media&fields=id", t, "PATCH");
                    escrever(conn, bytes);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                int codigo = conn.getResponseCode();
                String corpo = ler(conn, codigo);
                conn.disconnect();
                if (codigo >= 200 && codigo < 300) {
                    atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                            .edit()
                            .putBoolean("pendenteEnviar", false)
                            .putLong("ultimoBackup", System.currentTimeMillis())
                            .apply();
                    avisar(silencioso ? "Backup automático enviado ao Drive ✓"
                                      : "Backup salvo no Google Drive ✓", true);
                    return;
                }
                if (codigo == 401 && tentativa == 0) { token = null; continue; }
                throw new IOException("HTTP " + codigo + ": " + resumo(corpo));
            } catch (PrecisaTela e) {
                throw e;
            } catch (IOException e) {
                if (tentativa == 0 && String.valueOf(e.getMessage()).contains("401")) { token = null; continue; }
                throw e;
            }
        }
    }

    private void baixar() throws Exception {
        avisar("Procurando o backup no Drive…", true);
        String arquivoId = null;
        for (int tentativa = 0; tentativa < 2 && arquivoId == null; tentativa++) {
            String t = obterToken();
            arquivoId = localizarArquivo(t, localizarPasta(t));
        }
        if (arquivoId == null) {
            avisar("Nenhum backup encontrado neste Drive. Envie um backup primeiro.", false);
            return;
        }
        avisar("Baixando o backup…", true);
        String corpo = null;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            String t = obterToken();
            HttpURLConnection conn = abrir(API + "files/" + arquivoId + "?alt=media", t, "GET");
            int codigo = conn.getResponseCode();
            corpo = ler(conn, codigo);
            conn.disconnect();
            if (codigo >= 200 && codigo < 300) break;
            if (codigo == 401 && tentativa == 0) { token = null; continue; }
            throw new IOException("HTTP " + codigo + ": " + resumo(corpo));
        }
        JSONObject conteudo = new JSONObject(corpo);
        if (!"se-cesd-backup".equals(conteudo.optString("formato"))) {
            avisar("O arquivo do Drive não é um backup válido do SE • CESD.", false);
            return;
        }
        String dados = conteudo.optString("dados", "");
        if (dados.isEmpty()) {
            avisar("O backup está vazio.", false);
            return;
        }
        final String cofre = dados;
        avisar("Backup de " + conteudo.optString("criadoEm", "?") + " pronto para aplicar.", true);
        if (ouvinte == null) return;
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ouvinte.restaurado(cofre);
            }
        });
    }


    // ------------------------------------------------------------------
    // Pasta escolhida pelo usuário (SAF/Storage Access Framework)
    // ------------------------------------------------------------------

    private SharedPreferences prefs() {
        return atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE);
    }

    /** Nome da pasta no Drive usada pelo fluxo da conta (padrão: SE • CESD). */
    public static String pastaContaNome(Activity a) {
        String n = a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("pastaContaNome", "");
        return n.isEmpty() ? NOME_PASTA : n;
    }

    /** Define o nome da pasta no Drive para o fluxo da conta. */
    public static void definirPastaContaNome(Activity a, String nome) {
        a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .edit().putString("pastaContaNome", nome == null ? "" : nome.trim()).apply();
    }

    private String nomePastaAtual() {
        String n = prefs().getString("pastaContaNome", "");
        return n.isEmpty() ? NOME_PASTA : n;
    }

    /** Há pasta escolhida no seletor (Drive ou outro local)? */
    public boolean temSaf() {
        return !prefs().getString("safPastaUri", "").isEmpty();
    }

    private Uri safPasta() {
        return Uri.parse(prefs().getString("safPastaUri", ""));
    }

    /** Guarda (ou limpa, com null) a pasta escolhida. */
    public void definirSafPasta(Uri uri) {
        SharedPreferences.Editor e = prefs().edit();
        if (uri == null) {
            e.remove("safPastaUri");
        } else {
            e.putString("safPastaUri", uri.toString());
        }
        e.apply();
    }

    /** Nome amigável da pasta escolhida (se o provedor informar). */
    public String nomeSafPasta() {
        try {
            Uri tree = safPasta();
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            Cursor c = atividade.getContentResolver().query(docUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return "Pasta escolhida";
    }

    /** Envia pela pasta escolhida (sem OAuth — usa o app Drive do aparelho). */
    private void enviarSaf(String cofreJson) throws IOException {
        Uri pasta = safPasta();
        JSONObject conteudo = new JSONObject();
        try {
            conteudo.put("formato", "se-cesd-backup");
            conteudo.put("versao", 1);
            conteudo.put("criadoEm", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            conteudo.put("aparelho", android.os.Build.MODEL == null ? "Android" : android.os.Build.MODEL);
            conteudo.put("dados", cofreJson);
        } catch (JSONException e) {
            throw new IOException("Falha ao montar o backup.");
        }
        byte[] bytes = conteudo.toString().getBytes(StandardCharsets.UTF_8);
        ContentResolver r = atividade.getContentResolver();

        // procura o arquivo já existente na pasta (para atualizar o mesmo)
        Uri existente = null;
        try {
            Uri filhos = DocumentsContract.buildChildDocumentsUriUsingTree(pasta,
                    DocumentsContract.getTreeDocumentId(pasta));
            Cursor c = r.query(filhos, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        String nome = c.getString(1);
                        if (NOME_ARQUIVO.equalsIgnoreCase(nome)) {
                            existente = DocumentsContract.buildDocumentUriUsingTree(pasta, c.getString(0));
                            break;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }

        Uri alvo = existente;
        if (alvo == null) {
            alvo = DocumentsContract.createDocument(r, pasta, "application/json", NOME_ARQUIVO);
        }
        if (alvo == null) throw new IOException("Não foi possível criar o arquivo na pasta escolhida.");
        OutputStream saida = r.openOutputStream(alvo, "w");
        if (saida == null) throw new IOException("A pasta escolhida não permite escrita.");
        saida.write(bytes);
        saida.flush();
        saida.close();
        prefs().edit()
                .putBoolean("pendenteEnviar", false)
                .putLong("ultimoBackup", System.currentTimeMillis())
                .apply();
    }

    /** Envio inteligente: pasta escolhida (SAF) quando houver; senão conta Google. */
    public void enviarSmart(String cofreJson, boolean quieto) {
        if (temSaf()) {
            fila.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!quieto) avisar("Salvando na pasta escolhida…", true);
                        enviarSaf(cofreJson);
                        avisar(quieto ? "Backup automático salvo na pasta ✓"
                                      : "Backup salvo na pasta escolhida ✓", true);
                    } catch (IOException e) {
                        avisar(erroAmigavel(e), false);
                    } catch (Exception e) {
                        avisar("Falha ao salvar na pasta escolhida.", false);
                    }
                }
            });
        } else if (quieto) {
            if (prontoParaEnviar()) iniciar(Acao.ENVIAR, cofreJson, true);
        } else {
            enviar(cofreJson);
        }
    }

    /** Lê um arquivo de backup escolhido no seletor (restauração). */
    public String lerBackupDeUri(Uri uri) throws IOException {
        try {
            InputStream entrada = atividade.getContentResolver().openInputStream(uri);
            if (entrada == null) throw new IOException("Não foi possível abrir o arquivo.");
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] p = new byte[8192];
            int n;
            while ((n = entrada.read(p)) != -1) saida.write(p, 0, n);
            entrada.close();
            String corpo = saida.toString("UTF-8");
            JSONObject conteudo = new JSONObject(corpo);
            if (!"se-cesd-backup".equals(conteudo.optString("formato"))) {
                throw new IOException("Este arquivo não é um backup do SE • CESD.");
            }
            String dados = conteudo.optString("dados", "");
            if (dados.isEmpty()) throw new IOException("O backup está vazio.");
            return dados;
        } catch (IOException e) {
            throw e;
        } catch (JSONException e) {
            throw new IOException("Arquivo de backup inválido.");
        } catch (Exception e) {
            throw new IOException("Falha ao ler o arquivo de backup.");
        }
    }

    // ------------------------------------------------------------------
    // Pasta / arquivo
    // ------------------------------------------------------------------

    private String garantirPasta(String t) throws Exception {
        String id = localizarPasta(t);
        if (id != null) return id;
        JSONObject meta = new JSONObject();
        meta.put("name", nomePastaAtual());
        meta.put("mimeType", "application/vnd.google-apps.folder");
        HttpURLConnection conn = abrir(API + "files?fields=id", t, "POST");
        escrever(conn, meta.toString().getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo + " ao criar a pasta.");
        return new JSONObject(corpo).getString("id");
    }

    private String localizarPasta(String t) throws Exception {
        String q = "name='" + nomePastaAtual() + "' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        return primeiro(buscar(t, q));
    }

    private String localizarArquivo(String t, String pastaId) throws Exception {
        String q = pastaId != null
                ? "name='" + NOME_ARQUIVO + "' and '" + pastaId + "' in parents and trashed=false"
                : "name='" + NOME_ARQUIVO + "' and trashed=false";
        return primeiro(buscar(t, q));
    }

    private org.json.JSONArray buscar(String t, String q) throws Exception {
        HttpURLConnection conn = abrir(API + "files?q=" + URLEncoder.encode(q, "UTF-8")
                + "&spaces=drive&fields=files(id,name)&pageSize=5", t, "GET");
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo + " ao buscar.");
        return new JSONObject(corpo).optJSONArray("files");
    }

    private String primeiro(org.json.JSONArray arquivos) {
        if (arquivos != null && arquivos.length() > 0) {
            return arquivos.optJSONObject(0).optString("id", null);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private HttpURLConnection abrir(String url, String t, String metodo) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(metodo);
        conn.setRequestProperty("Authorization", "Bearer " + t);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(!"GET".equals(metodo));
        return conn;
    }

    private void escrever(HttpURLConnection conn, byte[] bytes) throws IOException {
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream saida = conn.getOutputStream();
        saida.write(bytes);
        saida.flush();
        saida.close();
    }

    private byte[] multipart(String limite, String metaJson, byte[] media) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(("--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        saida.write(metaJson.getBytes(StandardCharsets.UTF_8));
        saida.write(("\r\n--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        saida.write(media);
        saida.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return saida.toByteArray();
    }

    private String ler(HttpURLConnection conn, int codigo) throws IOException {
        InputStream fluxo = codigo >= 200 && codigo < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (fluxo == null) return "";
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] p = new byte[8192];
        int n;
        while ((n = fluxo.read(p)) != -1) saida.write(p, 0, n);
        fluxo.close();
        return saida.toString("UTF-8");
    }

    private String resumo(String corpo) {
        if (corpo == null) return "";
        return corpo.length() > 250 ? corpo.substring(0, 250) + "…" : corpo;
    }

    private String erroAmigavel(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("HTTP 403")) return "O Google recusou (403). Confira a Drive API ativa e o escopo drive.file no Console.";
        if (m.contains("HTTP")) return "O Google recusou a operação (" + resumo(m) + ").";
        if (m.contains("precisa-tela")) return "Conclua a autorização no Google para continuar.";
        return m.isEmpty() ? "Sem conexão com o Google Drive." : m;
    }
}
