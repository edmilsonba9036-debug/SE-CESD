package br.org.secesd.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Base das atividades do SE • CESD + sistema visual (v1.3):
 * cabeçalho em gradiente azul-marinho com detalhe dourado e "folha"
 * branca arredondada para o conteúdo; utilitários de cartões, emblemas
 * e linha do tempo. Tudo programático (sem XML de layout).
 */
public abstract class AtividadeBase extends Activity {

    static final int AZUL_MARINHA = 0xFF0A2463;
    static final int AZUL_MEDIO = 0xFF1E5AA8;
    static final int AZUL_CLARO = 0xFFEAF3FC;
    static final int CINZA_TEXTO = 0xFF33415C;
    static final int OURO = 0xFFC9A227;

    /** Cores das trilhas do memorial (fases + Exército). */
    static final int COR_FASE1 = 0xFF2D7DD2; // antes do concurso
    static final int COR_FASE2 = 0xFFC9A227; // durante o concurso
    static final int COR_FASE3 = 0xFF2E7D5B; // após formado
    static final int COR_FASE4 = 0xFF5A6B85; // até a baixa
    static final int COR_EXERCITO = 0xFF4B5320; // passagem pelo Exército

    /** Painel rolável com o novo visual: gradiente + folha branca. */
    protected ScrollView tela(String titulo, String subtitulo) {
        ScrollView rolagem = new ScrollView(this);
        rolagem.setFillViewport(true);
        GradientDrawable fundo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF071638, AZUL_MARINHA, 0xFF16407F});
        rolagem.setBackground(fundo);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int p = px(22);
        raiz.setPadding(p, px(28), p, 0);

        TextView marca = new TextView(this);
        marca.setText("SE • CESD");
        marca.setTextColor(OURO);
        marca.setTextSize(12);
        marca.setTypeface(Typeface.DEFAULT_BOLD);
        marca.setLetterSpacing(0.18f);
        raiz.addView(marca);

        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(6), 0, 0);
        raiz.addView(t);

        View barra = new View(this);
        barra.setBackground(arredondado(OURO, px(2), 0, 0));
        LinearLayout.LayoutParams lpBarra = new LinearLayout.LayoutParams(px(56), px(4));
        lpBarra.topMargin = px(10);
        raiz.addView(barra, lpBarra);

        if (subtitulo != null && !subtitulo.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(subtitulo);
            s.setTextColor(0xFFC6D6EE);
            s.setTextSize(13.5f);
            s.setLineSpacing(px(2), 1f);
            LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpS.topMargin = px(10);
            raiz.addView(s, lpS);
        }

        LinearLayout folha = new LinearLayout(this);
        folha.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fd = new GradientDrawable();
        fd.setColor(Color.WHITE);
        fd.setCornerRadii(new float[]{
                px(26), px(26), px(26), px(26), 0, 0, 0, 0});
        folha.setBackground(fd);
        int pf = px(18);
        folha.setPadding(pf, px(22), pf, px(40));
        folha.setTag("folha");
        LinearLayout.LayoutParams lpFolha = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFolha.topMargin = px(20);
        raiz.addView(folha, lpFolha);

        rolagem.addView(raiz, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return rolagem;
    }

    /** A folha de conteúdo criada por tela(). */
    protected LinearLayout coluna(ScrollView rolagem) {
        ViewGroup raiz = (ViewGroup) rolagem.getChildAt(0);
        for (int i = 0; i < raiz.getChildCount(); i++) {
            View filho = raiz.getChildAt(i);
            if ("folha".equals(filho.getTag())) {
                return (LinearLayout) filho;
            }
        }
        return (LinearLayout) raiz.getChildAt(raiz.getChildCount() - 1);
    }

    protected TextView titulo(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextColor(AZUL_MARINHA);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(16), 0, px(6));
        return t;
    }

    protected TextView texto(String conteudo) {
        TextView t = new TextView(this);
        t.setText(conteudo);
        t.setTextColor(CINZA_TEXTO);
        t.setTextSize(14);
        t.setLineSpacing(px(2), 1f);
        t.setPadding(0, px(2), 0, px(2));
        return t;
    }

    protected EditText campo(String dica) {
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(0xFF8A9BB5);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(arredondado(0xFFF3F7FC, px(12), 0xFFD5E2F1, px(1)));
        e.setPadding(px(14), px(12), px(14), px(12));
        return e;
    }

    protected EditText campoMultilinha(String dica, int linhas) {
        EditText e = campo(dica);
        e.setSingleLine(false);
        e.setMinLines(linhas);
        e.setGravity(Gravity.TOP);
        return e;
    }

    protected Button botao(String texto, boolean primario) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        if (primario) {
            b.setTextColor(Color.WHITE);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{AZUL_MARINHA, AZUL_MEDIO});
            g.setCornerRadius(px(14));
            b.setBackground(g);
        } else {
            b.setTextColor(AZUL_MARINHA);
            b.setBackground(arredondado(0xFFE3EDF9, px(14), 0, 0));
        }
        b.setPadding(0, px(12), 0, px(12));
        return b;
    }

    protected View espaco(int dp) {
        return new Space(this);
    }

    protected LinearLayout.LayoutParams largura() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(10);
        return lp;
    }

    protected LinearLayout cartao() {
        LinearLayout cartao = new LinearLayout(this);
        cartao.setOrientation(LinearLayout.VERTICAL);
        int p = px(14);
        cartao.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(px(16));
        g.setStroke(px(1), 0xFFE1E9F4);
        cartao.setBackground(g);
        // elevação suave
        cartao.setElevation(px(2));
        return cartao;
    }

    /** Cartão com faixa colorida à esquerda (para trilhas/categorias). */
    protected LinearLayout cartaoFaixa(int cor) {
        LinearLayout externo = new LinearLayout(this);
        externo.setOrientation(LinearLayout.HORIZONTAL);
        View faixa = new View(this);
        faixa.setBackground(arredondado(cor, px(3), 0, 0));
        LinearLayout.LayoutParams lpFaixa = new LinearLayout.LayoutParams(px(5),
                ViewGroup.LayoutParams.MATCH_PARENT);
        externo.addView(faixa, lpFaixa);
        LinearLayout conteudo = cartao();
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpC.leftMargin = px(8);
        externo.addView(conteudo, lpC);
        externo.setTag(conteudo);
        return externo;
    }

    /** O cartão interno de um cartaoFaixa(). */
    protected LinearLayout conteudoFaixa(LinearLayout faixa) {
        return (LinearLayout) faixa.getTag();
    }

    /** Emblema circular com monograma (ex.: "MV" = Memorial da Vida). */
    protected View emblema(String monograma, int cor) {
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable circulo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cor, escurecer(cor)});
        circulo.setShape(GradientDrawable.OVAL);
        badge.setBackground(circulo);
        TextView m = new TextView(this);
        m.setText(monograma);
        m.setTextColor(Color.WHITE);
        m.setTextSize(15);
        m.setTypeface(Typeface.DEFAULT_BOLD);
        m.setGravity(Gravity.CENTER);
        badge.addView(m, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        return badge;
    }

    /** Item de linha do tempo: ponto colorido + fio vertical + conteúdo. */
    protected LinearLayout itemLinhaDoTempo(int cor, View conteudo) {
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        View dot = new View(this);
        dot.setBackground(arredondado(cor, px(9), 0, 0));
        col.addView(dot, new ViewGroup.LayoutParams(px(18), px(18)));
        View fio = new View(this);
        fio.setBackgroundColor(0xFFDCE6F2);
        col.addView(fio, new LinearLayout.LayoutParams(px(2),
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        linha.addView(col, new LinearLayout.LayoutParams(px(26),
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpC.leftMargin = px(10);
        lpC.bottomMargin = px(14);
        linha.addView(conteudo, lpC);
        return linha;
    }

    protected GradientDrawable arredondado(int cor, int raio, int corBorda, int borda) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(raio);
        if (borda > 0) g.setStroke(borda, corBorda);
        return g;
    }

    private static int escurecer(int cor) {
        int r = (cor >> 16) & 0xFF, g = (cor >> 8) & 0xFF, b = cor & 0xFF;
        return Color.argb(255, Math.max(0, r - 30), Math.max(0, g - 30), Math.max(0, b - 30));
    }

    protected void aviso(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    protected int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    protected void ir(Class<?> destino) {
        startActivity(new Intent(this, destino));
    }

    @Override
    protected void onStop() {
        super.onStop();
        tentarBackupAutomatico();
    }

    /**
     * Backup automático: ao sair da tela, se houver mudanças pendentes e a
     * conexão com o Drive já estiver configurada, envia sozinho. Falha em
     * silêncio — nunca atrapalha o uso do app.
     */
    private void tentarBackupAutomatico() {
        try {
            if (!Cofre.sessaoAberta()) return;
            android.content.SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
            if (!p.getBoolean("autoBackup", true)) return;
            if (!p.getBoolean("pendenteEnviar", false)) return;
            long agora = System.currentTimeMillis();
            if (agora - p.getLong("tentativaAuto", 0) < 60000) return;
            DriveBackup sonda = new DriveBackup(this, null);
            if (!sonda.temSaf() && !sonda.prontoParaEnviar()) return;
            p.edit().putLong("tentativaAuto", agora).apply();
            sonda.enviarSmart(Cofre.exportar(this), true);
        } catch (Exception e) {
            // silencioso por design
        }
    }
}
