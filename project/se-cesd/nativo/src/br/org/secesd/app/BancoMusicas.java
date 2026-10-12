package br.org.secesd.app;

/**
 * REPERTÓRIO do player: as melhores canções militares do Brasil,
 * TODAS no YouTube Music (a única fonte de reprodução do app).
 * Cada link foi VERIFICADO vivo via oembed do YouTube em 02/10/2026
 * — incluindo gravações de canais oficiais (Exército Brasileiro,
 * bandas militares) e vídeos legendados das canções clássicas.
 */
public final class BancoMusicas {

    /** As melhores canções militares — toque abre direto no YT Music. */
    private static final String[] TITULOS = {
            "Hino Nacional Brasileiro — Coral do Exército",
            "Canção do Exército — gravação oficial do Exército",
            "Hino a Caxias — Exército",
            "Ardor do Infante — Infantaria do Exército",
            "Nobre Infantaria — Infantaria do Exército",
            "Canção do Expedicionário (FEB) — legendada",
            "Cisne Branco — Canção da Marinha",
            "Hino dos Aviadores — FAB",
            "Bandeirantes do Ar — Hino da AFA (legendado)",
            "Canção da Infantaria da Aeronáutica — FAB",
            "Canção da AMAN — Academia das Agulhas Negras",
            "Marcha da Despedida — FAB/EEAR",
            "Aviação de Transporte de Tropa — FAB",
            "Dobrado Brasília — Banda da FAB (CINDACTA II)"
    };

    private static final String[] LINKS = {
            "https://music.youtube.com/watch?v=1hnQjNojGp0",
            "https://music.youtube.com/watch?v=spJkc0MIfpw",
            "https://music.youtube.com/watch?v=1kAul-eQNlY",
            "https://music.youtube.com/watch?v=JEyEdi7Kf3Y",
            "https://music.youtube.com/watch?v=QP5uXGLBWMM",
            "https://music.youtube.com/watch?v=uDvBz7LGpbw",
            "https://music.youtube.com/watch?v=A2K_hTlJ-rQ",
            "https://music.youtube.com/watch?v=TdoWtM2mULs",
            "https://music.youtube.com/watch?v=7Vcvcx8ixJs",
            "https://music.youtube.com/watch?v=8HcGO_kiqSA",
            "https://music.youtube.com/watch?v=R9fzOK9c5UU",
            "https://music.youtube.com/watch?v=VbudcLdsaA4",
            "https://music.youtube.com/watch?v=bjPSUl8pr5M",
            "https://music.youtube.com/watch?v=3PPcRPEISiA"
    };

    /** A playlist de ouro: 56 dobrados, marchas e hinos (YT Music). */
    private static final String PLAYLIST =
            "https://music.youtube.com/playlist?list=PLHHgcpF3ODBpODQHPCPgGlg5yHVPnvVvm";

    private BancoMusicas() {}

    public static String[] titulos() {
        return TITULOS.clone();
    }

    public static String[] links() {
        return LINKS.clone();
    }

    public static String playlist() {
        return PLAYLIST;
    }
}
