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

    /** Repara o automático: limpa pausas, erros e o limitador; marca pendência. */
    public static void repararAuto(Activity a) {
        a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE).edit()
                .putLong("autoPausaAte", 0L)
                .putLong("tentativaAuto", 0L)
                .putBoolean("pendenteEnviar", true)
                .remove("ultimoAviso")
                .remove("ultimoAvisoEm")
                .apply();
    }

    /** Data/hora do último backup bem-sucedido (0 = nunca). */
    public static long ultimoBackup(Activity a) {
        return a.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getLong("ultimoBackup", 0);
    }

    /** Cria a pasta do backup no Drive imediatamente (sem enviar nada). */
    public void criarPastaAgora() {
        fila.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String t = obterToken();
                    garantirPasta(t);
                    avisar("Pasta \u201c" + nomePastaAtual() + "\u201d pronta no seu Drive ✓", true);
                } catch (PrecisaTela e) {
                    avisar("Conclua a autoriza\u00e7\u00e3o no Google para continuar.", true);
                } catch (IOException e) {
                    avisar(erroAmigavel(e), false);
                } catch (Exception e) {
                    avisar("N\u00e3o foi poss\u00edvel criar a pasta (" + detalhe(e) + ").", false);
                }
            }
        });
    }

    /**
     * Chamada ao abrir a tela de backup: conecta e garante a pasta no Drive
     * sem que a pessoa precise tocar em nada. Depois da autoriza\u00e7\u00e3o \u00fanica
     * do Google, cada abertura do app deixa a pasta pronta sozinha.
     */
    public void criarPastaAutomatica() {
        if (System.currentTimeMillis() < prefs().getLong("autoPausaAte", 0L)) return;
        boolean navegadorPendente = browserConfigurado()
                && prefs().getString("browserRefreshToken", "").isEmpty()
                && conta() == null;
        if (navegadorPendente) return;
        iniciar(Acao.CONECTAR, null, true);
    }

    /** Limpa modos antigos (seletor de pasta e navegador): a conta Google é o caminho. */
    public void limparModosAntigos() {
        if (browserConfigurado()) removerConfigBrowser();
        if (temSaf()) definirSafPasta(null);
        prefs().edit().remove("safRecusas").apply();
    }

    public void restaurar() {
        iniciar(Acao.RESTAURAR, null, false);
    }

    /** Chamar a partir de Activity.onActivityResult. Devolve true se era do Drive. */
    public boolean onActivityResult(int requestCode, Intent data) {
        if (requestCode == RC_CONTA) {
            String nome = data != null ? data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;
            if (nome == null) {
                prefs().edit().putLong("autoPausaAte",
                        System.currentTimeMillis() + 6L * 60 * 60 * 1000).apply();
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
            String t = obterToken();
            try {
                boolean jaExistia = localizarPasta(t) != null;
                garantirPasta(t);
                if (!silencioso) {
                    avisar("Conectado ✓ Pasta \u201c" + nomePastaAtual() + "\u201d pronta no seu Drive", true);
                } else if (!jaExistia) {
                    avisar("Pasta \u201c" + nomePastaAtual() + "\u201d criada no seu Drive ✓", true);
                }
            } catch (Exception pastaEx) {
                avisar("Conectado ✓ (a pasta ser\u00e1 criada no primeiro envio)", true);
            }
        } else if (acao == Acao.ENVIAR) {
            String payload = payloadPendente;
            payloadPendente = null;
            executarEnvio(payload);
        } else if (acao == Acao.RESTAURAR) {
            baixar();
        }
    }

    private void avisar(final String msg, final boolean ok) {
        if (ouvinte == null) {
            // Modo automático: sem tela para avisar — registra para o painel
            // de diagnóstico da tela Backup mostrar o motivo ao usuário.
            prefs().edit().putString("ultimoAviso", msg)
                    .putLong("ultimoAvisoEm", System.currentTimeMillis()).apply();
            return;
        }
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
            return "O Google ainda não conhece este app — cadastro único (5 min). "
                    + "Toque no botão \u201cAbrir o cadastro no Google\u201d logo abaixo. Lá no site, faça só isto:\n"
                    + "1) Credenciais → + Criar credenciais → ID do cliente OAuth → tipo Android.\n"
                    + "2) Nome do pacote: br.org.secesd.debug\n    SHA-1: 25:32:BE:B9:BD:65:7D:8C:92:22:5A:60:57:A7:5D:37:00:57:21:7D\n"
                    + "3) Tela de permissão → Usuários de teste → adicione o SEU Gmail.\n"
                    + "4) Espere 5 minutos e toque em \u201cEnviar backup\u201d de novo.\n"
                    + "Alternativa sem cadastro: use o botão \u201cEscolher pasta do backup…\u201d (navegue: Drive → Meu Drive → pasta).\n"
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
        avisar("Abrindo o Google no navegador… CONCLUA A AUTORIZAÇÃO LÁ e volte ao app (se nada abrir, este caminho está com problema — use a conta Google).", true);

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
            servidor.setSoTimeout(90000); // 90 s: se o navegador não voltar, o app troca o caminho sozinho
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
        JSONObject resposta;
        try {
            resposta = postarFormulario(TOKEN_ENDPOINT, corpo);
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("HTTP 400") || m.contains("HTTP 401")) {
                removerConfigBrowser();
                throw new IOException("O Google recusou o login do navegador (ID ou segredo do cliente inválidos). Toque em “Reparar backup automático” e use a sua conta Google.");
            }
            throw e;
        }
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
            try {
                loginPeloNavegador();
            } catch (IOException e) {
                // Navegador não abreu, não voltou ou foi cancelado: o modo navegador
                // sai de cena e o app passa a usar a conta Google do aparelho.
                removerConfigBrowser();
                throw new IOException(String.valueOf(e.getMessage())
                        + " Removi o modo navegador — o app agora usa a sua CONTA Google. Toque em \u201cEnviar backup\u201d de novo.");
            }
            return token;
        }
        String corpo = "client_id=" + URLEncoder.encode(idBrowser(), "UTF-8")
                + "&client_secret=" + URLEncoder.encode(segredoBrowser(), "UTF-8")
                + "&grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(renovacao, "UTF-8");
        try {
            salvarTokens(postarFormulario(TOKEN_ENDPOINT, corpo));
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("HTTP 400") || m.contains("HTTP 401")) {
                removerConfigBrowser();
                throw new IOException("O login pelo navegador foi recusado pelo Google (ID ou segredo inválidos). Toque em “Reparar backup automático” e use a sua conta Google.");
            }
            throw e;
        }
        return token;
    }

    private String obterToken() throws IOException {
        if (token != null && System.currentTimeMillis() < tokenExpiraEm) {
            return token;
        }
        // Prioridade: CONTA GOOGLE (sem navegador, sem troca de tokens) quando
        // já está configurada. O navegador fica apenas como plano B.
        if (conta() != null) {
            try {
                return obterTokenConta();
            } catch (IOException e) {
                String m = String.valueOf(e.getMessage());
                boolean falhaDoServico = m.contains("ainda não conhece")
                        || m.contains("Falha no serviço de contas");
                if (falhaDoServico && browserConfigurado()) {
                    return obterTokenNavegador();
                }
                throw e;
            }
        }
        if (browserConfigurado()) {
            return obterTokenNavegador();
        }
        escolherConta();
        throw new PrecisaTela();
    }

    /** Token pela conta Google do aparelho (Play Services — sem navegador). */
    private String obterTokenConta() throws IOException {
        Account conta = conta();
        AccountManager am = AccountManager.get(atividade);
        Bundle resultado;
        try {
            AccountManagerFuture<Bundle> futuro = am.getAuthToken(
                    conta, "oauth2:" + ESCOPO, new Bundle(), null, null, null);
            resultado = futuro.getResult();
        } catch (android.accounts.OperationCanceledException e) {
            prefs().edit().putLong("autoPausaAte",
                    System.currentTimeMillis() + 6L * 60 * 60 * 1000).apply();
            throw new IOException("Você cancelou a autorização do Google.");
        } catch (android.accounts.AuthenticatorException e) {
            throw new IOException(orientarFalhaAutenticador(e));
        } catch (IOException e) {
            String d = String.valueOf(e.getMessage());
            if (d.contains("NetworkError") || d.toLowerCase().contains("network")
                    || d.toLowerCase().contains("timeout")
                    || d.contains("Unable to resolve host") || d.contains("No address associated")) {
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
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    escrever(conn, bytes);
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
                if ((codigo >= 500 || codigo == 403) && tentativa == 0) {
                    try { Thread.sleep(1500); } catch (InterruptedException ie) { }
                    continue; // instabilidade/recusa momentânea do Google: tenta mais uma vez
                }
                throw new IOException("HTTP " + codigo + ": " + resumo(corpo));
            } catch (PrecisaTela e) {
                throw e;
            } catch (IOException e) {
                String m = String.valueOf(e.getMessage());
                if (tentativa == 0 && m.contains("401")) { token = null; continue; }
                if (tentativa == 0 && (m.contains("Sem conexão") || m.toLowerCase().contains("timeout")
                        || m.toLowerCase().contains("failed") || m.toLowerCase().contains("connect")
                        || m.contains("Unable to resolve host") || m.contains("No address associated"))) {
                    try { Thread.sleep(1500); } catch (InterruptedException ie) { }
                    continue; // rede oscilou: tenta mais uma vez
                }
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
            e.putInt("safRecusas", 0);
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
        } catch (SecurityException e) {
            throw new IOException("O Android negou o acesso à pasta escolhida. Toque em “Trocar pasta do backup…” e escolha a pasta de novo.");
        } catch (Exception e) {
            throw new IOException("Não consegui ler a pasta escolhida (" + detalhe(e) + "). Toque em “Trocar pasta do backup…” e escolha de novo.");
        }

        Uri alvo = existente;
        if (alvo == null) {
            // O Drive às vezes recusa criar com um tipo MIME, mas aceita com outro.
            // Tenta 3 tipos e, se preciso, dá uma segunda rodada (o provedor aquece).
            String[] tipos = {"application/json", "application/octet-stream", "text/plain"};
            IOException ultimoErro = null;
            for (int rodada = 0; rodada < 2 && alvo == null; rodada++) {
                for (String tipo : tipos) {
                    try {
                        alvo = DocumentsContract.createDocument(r, pasta, tipo, NOME_ARQUIVO);
                    } catch (SecurityException e) {
                        throw new IOException("O Android negou a permissão de gravar nessa pasta. Toque em “Trocar pasta do backup…” e escolha de novo.");
                    } catch (IllegalArgumentException e) {
                        ultimoErro = new IOException("Essa pasta não aceita criar arquivos pelo seletor. Em “Trocar pasta do backup…”, escolha a pasta navegando: Drive → Meu Drive → sua pasta. Ou use a conta Google, que o app envia sozinho.");
                        alvo = null;
                    } catch (Exception e) {
                        ultimoErro = new IOException("O Drive recusou criar o arquivo (" + detalhe(e) + "). Tente “Trocar pasta do backup…”.");
                        alvo = null;
                    }
                    if (alvo != null) break;
                }
            }
            if (alvo == null) {
                throw ultimoErro != null ? ultimoErro
                        : new IOException("Não foi possível criar o arquivo na pasta escolhida. Tente “Trocar pasta do backup…”.");
            }
        }

        OutputStream saida = null;
        try {
            saida = r.openOutputStream(alvo, "w");
            if (saida == null) {
                throw new IOException("A pasta escolhida não permite escrita. Toque em “Trocar pasta do backup…” e escolha outra pasta.");
            }
            saida.write(bytes);
            saida.flush();
        } catch (SecurityException e) {
            throw new IOException("O Android negou a escrita na pasta. Toque em “Trocar pasta do backup…” e escolha de novo.");
        } catch (IOException e) {
            throw new IOException("Falha ao gravar na pasta escolhida (" + detalhe(e) + "). Verifique a internet e tente de novo.");
        } finally {
            if (saida != null) {
                try {
                    saida.close();
                } catch (IOException ignored) {
                }
            }
        }
        prefs().edit()
                .putBoolean("pendenteEnviar", false)
                .putInt("safRecusas", 0)
                .putLong("ultimoBackup", System.currentTimeMillis())
                .apply();
    }

    /** Mensagem curta de um erro, com o nome da classe quando a mensagem vem vazia. */
    private static String detalhe(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private void registrarRecusaSeletor() {
        int n = prefs().getInt("safRecusas", 0) + 1;
        prefs().edit().putInt("safRecusas", n).apply();
    }

    /** A pasta escolhida no seletor recusou a gravação (o Drive às vezes entrega
        a pasta de um jeito que o Android não consegue gravar dentro). */
    private static boolean seletorRecusou(IOException e) {
        String m = String.valueOf(e.getMessage());
        return m.contains("não aceita criar arquivos") || m.contains("não permite escrita")
                || m.contains("negou") || m.contains("recusou criar o arquivo")
                || m.contains("Não foi possível criar o arquivo") || m.contains("ler a pasta")
                || m.contains("Falha ao gravar");
    }

    /** Envio inteligente: pasta escolhida (SAF) quando houver; senão conta Google.
     *  Depois de 2 recusas seguidas do seletor, envia direto pela conta. */
    public void enviarSmart(String cofreJson, final boolean quieto) {
        if (temSaf() && prefs().getInt("safRecusas", 0) < 2) {
            fila.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!quieto) avisar("Salvando na pasta escolhida…", true);
                        enviarSaf(cofreJson);
                        prefs().edit().putInt("safRecusas", 0).apply();
                        avisar(quieto ? "Backup automático salvo na pasta ✓"
                                      : "Backup salvo na pasta escolhida ✓", true);
                    } catch (IOException e) {
                        if (seletorRecusou(e) && prontoParaEnviar()) {
                            registrarRecusaSeletor();
                            if (!quieto) avisar("O seletor recusou a pasta — salvando pela sua conta Google…", true);
                            iniciar(Acao.ENVIAR, cofreJson, quieto);
                        } else {
                            avisar(erroAmigavel(e), false);
                        }
                    } catch (Exception e) {
                        avisar("Falha ao salvar na pasta escolhida.", false);
                    }
                }
            });
        } else if (temSaf()) {
            // Terceira recusa seguida: desiste do seletor de vez — remove a pasta
            // escolhida e passa a enviar sempre pela conta Google, sem mais avisos.
            definirSafPasta(null);
            if (!quieto) {
                avisar("O seletor recusou essa pasta de novo — removi ela das opções. "
                        + "O backup agora vai sempre pela sua conta Google (pasta \u201c"
                        + nomePastaAtual() + "\u201d).", true);
            }
            iniciar(Acao.ENVIAR, cofreJson, quieto);
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
        if (id != null) {
            moverParaCESD(t, id);
            return id;
        }
        String raiz = localizarPastaCESD(t);
        JSONObject meta = new JSONObject();
        meta.put("name", nomePastaAtual());
        meta.put("mimeType", "application/vnd.google-apps.folder");
        if (raiz != null) {
            org.json.JSONArray paisRaiz = new org.json.JSONArray();
            paisRaiz.put(raiz);
            meta.put("parents", paisRaiz);
        }
        HttpURLConnection conn = abrir(API + "files?fields=id", t, "POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        escrever(conn, meta.toString().getBytes(StandardCharsets.UTF_8));
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
        if (m.contains("Unable to resolve host") || m.contains("No address associated")
                || m.contains("resolve host") || m.contains("EAI_AGAIN")) {
            return "Sem internet agora: o celular não encontrou o Google. Confira o Wi-Fi ou os dados móveis (e se o modo avião está desligado) e toque em Enviar de novo.";
        }
        if (m.contains("negou") || m.contains("Trocar pasta") || m.contains("pasta escolhida")
                || m.contains("não permite escrita") || m.contains("ler a pasta")) {
            return m; // mensagens de pasta já vêm prontas
        }
        if (m.contains("HTTP 403")) return "O Google recusou (403). Confira a Drive API ativa e o escopo drive.file no Console.";
        if (m.contains("HTTP")) return "O Google recusou a operação (" + resumo(m) + ").";
        if (m.contains("precisa-tela")) return "Conclua a autorização no Google para continuar.";
        return m.isEmpty() ? "Sem conexão com o Google Drive." : m;
    }

    // ------------------------------------------------------------------
    // CANÇÕES MILITARES — downloads do player para o Drive
    // ------------------------------------------------------------------

    /** Nome da pasta no Drive onde o player guarda as canções baixadas. */
    public static final String PASTA_CANC = "Canções Militares";

    /** Mensagem devolvida quando a canção já existe na pasta. */
    public static final String MSG_JA_ESTAVA =
            "Essa canção já estava na pasta Canções Militares ✓";

    /**
     * Envia uma canção para a pasta Canções Militares no Drive (a mesma
     * conta do backup). Chamar FORA da thread da tela. Devolve null se
     * deu certo, ou a mensagem para o usuário.
     */
    public static String enviarMusica(Activity a, String nomeArquivo, byte[] bytes, String mime) {
        try {
            DriveBackup db = new DriveBackup(a, null);
            return db.enviarMusicaInterna(nomeArquivo, bytes, mime);
        } catch (PrecisaTela tela) {
            return "O Google quer uma autorização: abra a tela Backup uma vez "
                    + "(ela conecta sozinha) e depois tente baixar de novo.";
        } catch (Exception e) {
            return "Não deu para enviar ao Drive: " + String.valueOf(e.getMessage());
        }
    }

    /** null = enviada; MSG_JA_ESTAVA = já existia; exceção = falhou. */
    private String enviarMusicaInterna(String nomeArquivo, byte[] bytes, String mime) throws Exception {
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            String t = obterToken();
            String pastaId = garantirPastaMusicas(t);
            if (localizarMusica(t, pastaId, nomeArquivo) != null) return MSG_JA_ESTAVA;
            JSONObject meta = new JSONObject();
            meta.put("name", nomeArquivo);
            meta.put("mimeType", mime);
            org.json.JSONArray pais = new org.json.JSONArray();
            pais.put(pastaId);
            meta.put("parents", pais);
            String limite = "secesdmusica" + System.currentTimeMillis();
            byte[] corpo = multipart(limite, meta.toString(), bytes);
            HttpURLConnection conn = abrir(API_UPLOAD + "files?uploadType=multipart&fields=id", t, "POST");
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + limite);
            escrever(conn, corpo);
            int codigo = conn.getResponseCode();
            String resposta = ler(conn, codigo);
            conn.disconnect();
            if (codigo >= 200 && codigo < 300) return null;
            if (codigo == 401 && tentativa == 0) { token = null; continue; }
            if ((codigo >= 500 || codigo == 403) && tentativa == 0) {
                try { Thread.sleep(1500); } catch (InterruptedException ie) { }
                continue;
            }
            throw new IOException("HTTP " + codigo + ": " + resumo(resposta));
        }
        throw new IOException("O Google não aceitou o envio após duas tentativas.");
    }

    private String garantirPastaMusicas(String t) throws Exception {
        String q = "name='" + PASTA_CANC + "' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        String id = primeiro(buscar(t, q));
        if (id != null) {
            moverParaCESD(t, id);
            return id;
        }
        String raiz = localizarPastaCESD(t);
        JSONObject meta = new JSONObject();
        meta.put("name", PASTA_CANC);
        meta.put("mimeType", "application/vnd.google-apps.folder");
        if (raiz != null) {
            org.json.JSONArray paisMusicas = new org.json.JSONArray();
            paisMusicas.put(raiz);
            meta.put("parents", paisMusicas);
        }
        HttpURLConnection conn = abrir(API + "files?fields=id", t, "POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        escrever(conn, meta.toString().getBytes(StandardCharsets.UTF_8));
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo + " ao criar a pasta no Drive.");
        return new JSONObject(corpo).getString("id");
    }

    /**
     * Localiza a pasta CESD QUE O USUÁRIO JÁ TEM no Drive (dentro de MILIT).
     * NUNCA cria pasta: se não achar, devolve null e o app segue sem mexer.
     */
    private String localizarPastaCESD(String t) throws Exception {
        // 1º: CESD dentro de MILIT (é onde ela fica no seu Drive)
        String qMilit = "name='MILIT' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        String militId = primeiro(buscar(t, qMilit));
        if (militId != null) {
            String q = "name='CESD' and '" + militId
                    + "' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false";
            String cesd = primeiro(buscar(t, q));
            if (cesd != null) return cesd;
        }
        // 2º: CESD com nome exato em qualquer lugar
        String q2 = "name='CESD' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        String id = primeiro(buscar(t, q2));
        if (id != null) return id;
        // 3º: nome parecido (Milit, Cesd, MILITAR…) — "contains" acha parte do nome
        String q3 = "name contains 'CESD' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        return primeiro(buscar(t, q3));
    }

    /**
     * Organiza uma pasta já existente para dentro de CESD (sem copiar nada,
     * só muda o endereço). Se algo falhar, segue o jogo: a pasta continua
     * funcionando onde estiver.
     */
    private void moverParaCESD(String t, String pastaId) {
        try {
            String raiz = localizarPastaCESD(t);
            if (raiz == null) return;
            HttpURLConnection g = abrir(API + "files/" + pastaId + "?fields=parents", t, "GET");
            int cg = g.getResponseCode();
            String corpoG = ler(g, cg);
            g.disconnect();
            if (cg < 200 || cg >= 300) return;
            org.json.JSONArray atuais = new JSONObject(corpoG).optJSONArray("parents");
            if (atuais == null || atuais.length() != 1) return; // já organizada ou caso raro: não mexe
            String atual = atuais.optString(0, "");
            if (atual.isEmpty() || raiz.equals(atual)) return; // já está dentro de CESD
            String url = API + "files/" + pastaId + "?addParents=" + raiz
                    + "&removeParents=" + URLEncoder.encode(atual, "UTF-8") + "&fields=id";
            HttpURLConnection conn = abrir(url, t, "PATCH");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            escrever(conn, "{}".getBytes(StandardCharsets.UTF_8));
            ler(conn, conn.getResponseCode());
            conn.disconnect();
        } catch (Exception e) {
            // Organizar é cortesia, não obrigação: o backup/canções seguem funcionando.
        }
    }

    private String localizarMusica(String t, String pastaId, String nomeArquivo) throws Exception {
        String q = "name='" + nomeArquivo + "' and '" + pastaId + "' in parents and trashed=false";
        return primeiro(buscar(t, q));
    }
}
