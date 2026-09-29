package br.org.secesd.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Base das atividades do SE • CESD nativo + fábrica de componentes
 * estilizados (sem XML de layout — tudo programático).
 */
public abstract class AtividadeBase extends Activity {

    static final int AZUL_MARINHA = 0xFF0A2463;
    static final int AZUL_MEDIO = 0xFF1E5AA8;
    static final int AZUL_CLARO = 0xFFEAF3FC;
    static final int CINZA_TEXTO = 0xFF33415C;
    static final int OURO = 0xFFC9A227;

    /** Painel rolável com fundo claro e título no topo. */
    protected ScrollView tela(String titulo, String subtitulo) {
        ScrollView rolagem = new ScrollView(this);
        rolagem.setBackgroundColor(AZUL_CLARO);
        rolagem.setFillViewport(true);
        LinearLayout coluna = new LinearLayout(this);
        coluna.setOrientation(LinearLayout.VERTICAL);
        int p = px(20);
        coluna.setPadding(p, p, p, px(32));
        rolagem.addView(coluna, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextColor(AZUL_MARINHA);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        coluna.addView(t);
        if (subtitulo != null) {
            TextView s = new TextView(this);
            s.setText(subtitulo);
            s.setTextColor(CINZA_TEXTO);
            s.setTextSize(14);
            s.setPadding(0, px(4), 0, 0);
            coluna.addView(s);
        }
        coluna.addView(espaco(12));
        return rolagem;
    }

    /** A coluna de conteúdo criada por tela(). */
    protected LinearLayout coluna(ScrollView rolagem) {
        return (LinearLayout) rolagem.getChildAt(0);
    }

    protected TextView titulo(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextColor(AZUL_MARINHA);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(14), 0, px(6));
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
        e.setBackground(arredondado(Color.WHITE, px(10), 0xFF9DB8D9, px(1)));
        e.setPadding(px(12), px(11), px(12), px(11));
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
            b.setBackground(arredondado(AZUL_MARINHA, px(12), 0, 0));
        } else {
            b.setTextColor(AZUL_MARINHA);
            b.setBackground(arredondado(0xFFDCE9F8, px(12), 0, 0));
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
        lp.topMargin = px(8);
        return lp;
    }

    protected LinearLayout cartao() {
        LinearLayout cartao = new LinearLayout(this);
        cartao.setOrientation(LinearLayout.VERTICAL);
        int p = px(14);
        cartao.setPadding(p, p, p, p);
        cartao.setBackground(arredondado(Color.WHITE, px(14), 0xFFCBD9EA, px(1)));
        return cartao;
    }

    protected GradientDrawable arredondado(int cor, int raio, int corBorda, int borda) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(raio);
        if (borda > 0) g.setStroke(borda, corBorda);
        return g;
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
     * conexão com o Drive já estiver configurada, envia sozinho (o token é
     * renovado sem perguntar nada). Falha em silêncio — só avisa se der erro.
     */
    private void tentarBackupAutomatico() {
        try {
            if (!Cofre.sessaoAberta()) return;
            SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
            if (!p.getBoolean("autoBackup", true)) return;
            if (!p.getBoolean("pendenteEnviar", false)) return;
            long agora = System.currentTimeMillis();
            if (agora - p.getLong("tentativaAuto", 0) < 60000) return; // 1x por minuto, no máximo
            DriveBackup sonda = new DriveBackup(this, null);
            if (!sonda.prontoParaEnviar()) return; // ainda não conectou: nada a fazer
            p.edit().putLong("tentativaAuto", agora).apply();
            sonda.enviarAutomatico(Cofre.exportar(this));
        } catch (Exception e) {
            // o automático nunca deve atrapalhar o uso do app
        }
    }
}
