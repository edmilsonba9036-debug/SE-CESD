package br.org.secesd;

/**
 * Configuração central da autorização do Google Drive.
 *
 * VERIFICAÇÃO REALIZADA (29/09/2026, testes públicos no endpoint OAuth do Google):
 * Este client_id é um cliente OAuth do TIPO ANDROID ("SE CESD - Backup Google Drive").
 * Para clientes tipo Android, o Google BLOQUEIA:
 *   - redirect por esquema customizado (ex.: br.org.secesd:/oauth2redirect)
 *     → erro "Custom URI scheme is not enabled for your Android client";
 *   - redirect por IP de loopback (http://127.0.0.1:porta)
 *     → bloqueado desde 21/10/2022 para clientes Android/iOS.
 * Portanto NÃO existe fluxo por navegador (Custom Tabs) para este cliente.
 *
 * Caminho oficial suportado (Google: "Mobile Clients → recommended SDKs"):
 * usar o Google Play Services (com.google.android.gms), que valida
 * automaticamente o pacote + SHA-1 registrados no Google Cloud:
 *   1. Autorização: GoogleSignIn.requestPermissions(...) / AuthorizationClient
 *      com o escopo drive.file — o consentimento é desenhado pelo Play Services;
 *   2. Token: GoogleAuthUtil.getToken(conta, escopo) — o Play Services emite
 *      e renova o access token internamente (não guardamos refresh token).
 * Requer dependência play-services-auth no build.gradle e Google Play Services
 * no aparelho.
 *
 * Um fluxo por navegador só seria possível criando OUTRO cliente OAuth do tipo
 * "App de desktop" (loopback segue suportado para desktop) — alternativa
 * documentada no LEIA-ME-APK.txt.
 */
public final class DriveOAuth {

    private DriveOAuth() {}

    /** Client ID OAuth do cliente tipo Android (client_secret.com.json). */
    public static final String CLIENT_ID =
            "1021820891818-1mf236bt0o1m2lqe6184cojtk3kpv0kq.apps.googleusercontent.com";

    /** Projeto Google Cloud dono do cliente. */
    public static final String PROJECT_ID = "stone-host-510104-n1";

    /**
     * Escopo mínimo: o aplicativo só enxerga os arquivos que ele mesmo criou
     * no Drive do usuário (nada mais).
     */
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.file";

    /** Endpoints (referência; no fluxo Play Services a troca de token é interna). */
    public static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    // ----- Backup no Drive (REST v3) -----

    /** Nome do arquivo único de backup mantido no Drive do usuário. */
    public static final String BACKUP_FILE_NAME = "SE-CESD-backup.json";

    /** Pasta criada pelo próprio app no Drive (o escopo drive.file só vê o que o app criou). */
    public static final String BACKUP_FOLDER_NAME = "SE • CESD";

    /** Formato do contêiner de backup. */
    public static final String BACKUP_FORMAT = "se-cesd-backup";
    public static final int BACKUP_VERSION = 1;

    /** Base da Drive REST API. */
    public static final String DRIVE_API = "https://www.googleapis.com/drive/v3/";
    public static final String DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/";
}
