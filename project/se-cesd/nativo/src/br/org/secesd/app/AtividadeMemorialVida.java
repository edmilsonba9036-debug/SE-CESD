package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MEMORIAL DA MINHA VIDA — os fatos e acontecimentos do SE, organizados
 * nas quatro fases da jornada:
 *   1. Antes do concurso CESD
 *   2. Durante o concurso
 *   3. Após formado — Soldado Especialista
 *   4. Até a minha baixa
 *
 * Cada fase guarda uma lista de fatos (título, data, local, descrição),
 * em ordem de data. Tudo fica no cofre criptografado e vai no backup.
 */
public class AtividadeMemorialVida extends AtividadeBase {

    static final String[] FASES = {
            "Antes do concurso CESD",
            "Durante o concurso",
            "Após formado — Soldado Especialista",
            "Até a minha baixa"
    };

    static final String[] FASES_SUB = {
            "Família, estudos, trabalho e a decisão de servir.",
            "Inscrição, provas, aprovação e o curso de especialização.",
            "Serviço nas Organizações Militares, funções, missões e conquistas.",
            "Licenciamento do serviço ativo e a despedida da farda."
    };

    private static final String[] CHAVES = {"fase0", "fase1", "fase2", "fase3"};

    private JSONObject memorial = new JSONObject();
    private int faseAtual = -1; // -1 = lista de fases; 0..3 = fatos da fase

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }
        if (state != null) faseAtual = state.getInt("fase", -1);
        carregar();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("fase", faseAtual);
    }

    private void carregar() {
        try {
            JSONObject m = Cofre.lerDados(this).optJSONObject("memorial");
            memorial = m == null ? new JSONObject() : m;
        } catch (Exception e) {
            memorial = new JSONObject();
        }
        montar();
    }

    private void persistir() {
        try {
            JSONObject dados = Cofre.lerDados(this);
            dados.put("memorial", memorial);
            Cofre.salvarDados(this, dados);
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
        }
    }

    private JSONArray fatosDaFase(int fase) {
        JSONArray a = memorial.optJSONArray(CHAVES[fase]);
        return a == null ? new JSONArray() : a;
    }

    // ------------------------------------------------------------------
    // Visão 1: as quatro fases
    // ------------------------------------------------------------------

    private void montar() {
        if (faseAtual < 0) montarFases();
        else montarFase(faseAtual);
    }

    private void montarFases() {
        ScrollView rolagem = tela("Memorial da minha vida",
                "Os fatos e acontecimentos da sua jornada, nas quatro fases da causa CESD. "
                        + "Toque numa fase para registrar.");
        LinearLayout coluna = coluna(rolagem);

        for (int i = 0; i < FASES.length; i++) {
            final int fase = i;
            JSONArray fatos = fatosDaFase(fase);
            LinearLayout cartao = cartao();

            TextView numero = new TextView(this);
            numero.setText("FASE " + (i + 1));
            numero.setTextColor(OURO);
            numero.setTextSize(11);
            numero.setTypeface(Typeface.DEFAULT_BOLD);
            cartao.addView(numero);

            TextView t = new TextView(this);
            t.setText(FASES[i]);
            t.setTextColor(AZUL_MARINHA);
            t.setTextSize(16);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            cartao.addView(t);

            TextView s = new TextView(this);
            s.setText(FASES_SUB[i]);
            s.setTextColor(CINZA_TEXTO);
            s.setTextSize(13);
            cartao.addView(s);

            TextView contagem = new TextView(this);
            contagem.setText(fatos.length() == 0
                    ? "Nenhum fato registrado — toque para começar"
                    : fatos.length() + (fatos.length() == 1 ? " fato registrado" : " fatos registrados")
                      + " — toque para abrir");
            contagem.setTextColor(fatos.length() == 0 ? 0xFF8A9BB5 : AZUL_MEDIO);
            contagem.setTextSize(12);
            contagem.setTypeface(Typeface.DEFAULT_BOLD);
            contagem.setPadding(0, px(6), 0, 0);
            cartao.addView(contagem);

            cartao.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    faseAtual = fase;
                    montar();
                }
            });
            coluna.addView(cartao, largura());
        }

        TextView nota = texto("Dica: em cada fase, use “Adicionar fato” para registrar o acontecimento "
                + "com data, local e a sua história. Os fatos aparecem em ordem de data.");
        nota.setTextSize(12);
        coluna.addView(nota);
        setContentView(rolagem);
    }

    // ------------------------------------------------------------------
    // Visão 2: os fatos de uma fase
    // ------------------------------------------------------------------

    private void montarFase(final int fase) {
        JSONArray fatos = ordenar(fatosDaFase(fase));
        salvarFase(fase, fatos);

        ScrollView rolagem = tela("Fase " + (fase + 1) + " — " + FASES[fase], FASES_SUB[fase]);
        LinearLayout coluna = coluna(rolagem);

        View voltar = botao("← Voltar às fases", false);
        voltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                faseAtual = -1;
                montar();
            }
        });
        coluna.addView(voltar, largura());

        if (fatos.length() == 0) {
            coluna.addView(texto("Nenhum fato nesta fase ainda. Registre o primeiro acontecimento!"));
        }
        for (int i = 0; i < fatos.length(); i++) {
            final int indice = i;
            JSONObject f = fatos.optJSONObject(i);
            if (f == null) continue;
            LinearLayout cartao = cartao();
            TextView t = new TextView(this);
            t.setText(f.optString("titulo", "(sem título)"));
            t.setTextColor(AZUL_MARINHA);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            cartao.addView(t);
            String linha = f.optString("data", "");
            if (!f.optString("local", "").isEmpty()) {
                linha += (linha.isEmpty() ? "" : " • ") + f.optString("local");
            }
            if (!linha.isEmpty()) cartao.addView(texto(linha));
            String desc = f.optString("descricao", "");
            if (!desc.isEmpty()) cartao.addView(texto(desc));

            LinearLayout botoes = new LinearLayout(this);
            botoes.setOrientation(LinearLayout.HORIZONTAL);
            View editar = botao("Editar", false);
            editar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { dialogoFato(fase, indice); }
            });
            View remover = botao("Remover", false);
            remover.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { removerFato(fase, indice); }
            });
            LinearLayout.LayoutParams metade = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            botoes.addView(editar, metade);
            botoes.addView(remover, metade);
            cartao.addView(botoes);
            coluna.addView(cartao, largura());
        }

        View adicionar = botao("Adicionar fato", true);
        adicionar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoFato(fase, -1); }
        });
        coluna.addView(adicionar, largura());
        setContentView(rolagem);
    }

    /** Ordena por data (string AAAA-MM-DD) e devolve o array ordenado. */
    private JSONArray ordenar(JSONArray entrada) {
        try {
            java.util.List<JSONObject> lista = new java.util.ArrayList<>();
            for (int i = 0; i < entrada.length(); i++) lista.add(entrada.getJSONObject(i));
            java.util.Collections.sort(lista, new java.util.Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optString("data").compareTo(b.optString("data"));
                }
            });
            JSONArray saida = new JSONArray();
            for (JSONObject o : lista) saida.put(o);
            return saida;
        } catch (Exception e) {
            return entrada;
        }
    }

    private void salvarFase(int fase, JSONArray fatos) {
        try {
            memorial.put(CHAVES[fase], fatos);
        } catch (Exception ignored) {
        }
    }

    private void dialogoFato(final int fase, final int indice) {
        JSONArray fatos = fatosDaFase(fase);
        final JSONObject original = indice >= 0 && indice < fatos.length() ? fatos.optJSONObject(indice) : null;

        LinearLayout formulario = new LinearLayout(this);
        formulario.setOrientation(LinearLayout.VERTICAL);
        int p = px(18);
        formulario.setPadding(p, p, p, 0);

        final EditText titulo = campo("Fato/acontecimento (ex.: Aprovação no concurso)");
        final EditText data = campo("Data (AAAA-MM-DD)");
        data.setInputType(InputType.TYPE_CLASS_DATETIME);
        final EditText local = campo("Local (cidade, OM…) — opcional");
        final EditText descricao = campoMultilinha("Conte esse fato com suas palavras.", 4);
        formulario.addView(titulo);
        formulario.addView(data, largura());
        formulario.addView(local, largura());
        formulario.addView(descricao, largura());

        if (original != null) {
            titulo.setText(original.optString("titulo"));
            data.setText(original.optString("data"));
            local.setText(original.optString("local"));
            descricao.setText(original.optString("descricao"));
        }

        new AlertDialog.Builder(this)
                .setTitle((indice >= 0 ? "Editar fato" : "Novo fato")
                        + " — Fase " + (fase + 1))
                .setView(formulario)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        salvarFato(fase, indice, titulo, data, local, descricao);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarFato(int fase, int indice, EditText titulo, EditText data,
                            EditText local, EditText descricao) {
        String t = titulo.getText().toString().trim();
        if (t.length() < 3) {
            aviso("Dê um título ao fato (mínimo de 3 letras).");
            return;
        }
        try {
            JSONObject f = new JSONObject();
            f.put("titulo", t);
            f.put("data", data.getText().toString().trim());
            f.put("local", local.getText().toString().trim());
            f.put("descricao", descricao.getText().toString().trim());
            JSONArray fatos = fatosDaFase(fase);
            if (indice >= 0) fatos.put(indice, f);
            else fatos.put(f);
            salvarFase(fase, ordenar(fatos));
            persistir();
            aviso(indice >= 0 ? "Fato atualizado." : "Fato registrado no memorial ✓");
        } catch (Exception e) {
            aviso("Não foi possível salvar o fato.");
        }
        montar();
    }

    private void removerFato(final int fase, final int indice) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir o fato?")
                .setMessage("Este fato será apagado do memorial.")
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        JSONArray atual = fatosDaFase(fase);
                        JSONArray nova = new JSONArray();
                        for (int i = 0; i < atual.length(); i++) {
                            if (i != indice) nova.put(atual.opt(i));
                        }
                        salvarFase(fase, nova);
                        persistir();
                        montar();
                        aviso("Fato excluído.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (faseAtual >= 0) {
            faseAtual = -1;
            montar();
        } else {
            super.onBackPressed();
        }
    }
}
