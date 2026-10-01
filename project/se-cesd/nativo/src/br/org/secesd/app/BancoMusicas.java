package br.org.secesd.app;

/**
 * BANCO DE LINKS das canções militares do player — curadoria das
 * melhores e mais bonitas, das três Forças Armadas federais do Brasil
 * (Marinha, Exército e FAB) mais os hinos nacionais cerimoniais.
 * URLs verificadas e estáveis: site oficial da Marinha, site oficial
 * da AFA/FAB (www2.fab.mil.br/afa — quadro completo de canções),
 * Internet Archive (Fuzileiros Navais), Wikimedia Commons (domínio
 * público) e cópias preservadas do Colégio Militar de Brasília.
 */
public final class BancoMusicas {

    /** Título — origem (uma linha por canção, na ordem de execução). */
    private static final String[] TITULOS = {
            "Hino Nacional Brasileiro (coral) — Banda dos Fuzileiros Navais",
            "Hino Nacional Brasileiro (banda) — U.S. Navy Band",
            "Hino à Bandeira (banda) — Banda da Marinha",
            "Hino à Bandeira Nacional (coral) — FAB, Academia da Força Aérea",
            "Hino da Independência — Banda da Marinha",
            "Hino à Proclamação da República (coral) — Exército Brasileiro",
            "Hino da Proclamação da República (banda) — Fuzileiros Navais",
            "Hino da Aviação Naval — Banda da Marinha",
            "Cisne Branco — Banda da Marinha",
            "Canção do Expedicionário (FEB) — Banda da Marinha",
            "Fibra de Herói — Banda da Marinha",
            "Soldados da Liberdade — Banda da Marinha",
            "Canção da Esquadra — Banda da Marinha",
            "Canção da Infantaria — Banda da Marinha",
            "Hino da Escola Naval — Banda da Marinha",
            "Adeus Escola Querida — Banda da Marinha",
            "Hino da DNOG — Banda da Marinha",
            "Na Vanguarda — Banda da Marinha",
            "Regimento Naval — Banda da Marinha",
            "Viva a Marinha — Banda da Marinha",
            "Canção do Exército — Colégio Militar de Brasília (EB)",
            "Avante Camaradas — Colégio Militar de Brasília (EB)",
            "Hino dos Aviadores — FAB, gravação oficial (AFA)",
            "Canção da Intendência da Aeronáutica — FAB (oficial)",
            "Bandeirantes do Ar (Hino da AFA) — FAB (oficial)",
            "Canção do Cadete da Aeronáutica — FAB (oficial)",
            "Canção do Especialista da Aeronáutica — FAB (oficial)",
            "Canção do Paraquedista (Eterno Herói) — FAB (oficial)",
            "Canção das Comunicações da Aeronáutica — FAB (oficial)",
            "Canção da Engenharia da Aeronáutica — FAB (oficial)"
    };

    private static final String[] URLS = {
            "https://archive.org/download/lp_hinario-nacional_banda-sinfonica-do-corpo-de-fuzileiros/disc1/01.01.%20Hino%20Nacional.mp3",
            "https://upload.wikimedia.org/wikipedia/commons/9/9b/Hino_Nacional_Brasileiro_instrumental.ogg",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20da%20Bandeira.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/325.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20da%20Independencia.mp3",
            "https://upload.wikimedia.org/wikipedia/commons/a/a6/Hino_%C3%A0_Proclama%C3%A7%C3%A3o_da_Rep%C3%BAblica_-_Ex%C3%A9rcito_Brasileiro_-_Coral.ogg",
            "https://archive.org/download/lp_hinario-nacional_banda-sinfonica-do-corpo-de-fuzileiros/disc1/01.03.%20Hino%20Da%20Proclama%C3%A7%C3%A3o%20Da%20Rep%C3%BAblica.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20da%20Avia%C3%A7%C3%A3o.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Cisne%20Branco.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Can%C3%A7%C3%A3o%20Expedicion%C3%A1rio.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Fibra%20de%20Heroi.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Soldados%20da%20Liberdade.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Can%C3%A7%C3%A3o%20da%20Esquadra.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Can%C3%A7%C3%A3o%20da%20Infantaria.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20Escola%20Naval.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Adeus%20Escola%20Querida.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20da%20DNOG.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Na%20Vanguarda.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/RegimentoNaval.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Viva%20A%20Marinha.mp3",
            "https://web.archive.org/web/20211230041330mp_/http://www.cmb.eb.mil.br/images/audios/Musica/Cancao%20do%20Exercito.mp3",
            "https://web.archive.org/web/20211230041341mp_/http://www.cmb.eb.mil.br/images/audios/Musica/Avante%20Camaradas.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/82.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/78.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/77.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/318.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/80.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/85.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/84.mp3",
            "https://www2.fab.mil.br/afa/media/k2/audio/83.mp3"
    };

    private BancoMusicas() {}

    public static int total() {
        return TITULOS.length;
    }

    public static String[] titulos() {
        return TITULOS.clone();
    }

    public static String[] urls() {
        return URLS.clone();
    }
}
