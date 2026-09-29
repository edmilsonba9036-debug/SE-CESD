package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MEMORIAL DA MINHA VIDA (v1.3) — as histórias militares do SE em 5 trilhas
 * com cor própria:
 *   1. Antes do concurso CESD (azul)
 *   2. Durante o concurso (ouro)
 *   3. Após formado — Soldado Especialista (verde FAB)
 *   4. Até a minha baixa (cinza-azulado)
 *   5. Passagem pelo Exército (verde-oliva)
 * E a LINHA DO TEMPO, que mistura todos os fatos em ordem cronológica
 * com pontos coloridos por trilha.
 */
public class AtividadeMemorialVida extends AtividadeBase {

    static final String[] TRILHAS = {
            "Antes do concurso CESD",
            "Durante o concurso",
            "Após formado — Soldado Especialista",
            "Até a minha baixa",
            "Passagem pelo Exército"
    };

    static final String[] TRILHAS_SUB = {
            "Família, estudos, trabalho e a decisão de servir.",
            "Inscrição, provas, aprovação e o curso no CESD.",
            "OMs, funções, missões e conquistas na FAB.",
            "Licenciamento do serviço ativo e a despedida da farda.",
            "Sua jornada nas unidades do Exército Brasileiro."
    };

    static final int[] TRILHAS_COR = {
            COR_FASE1, COR_FASE2, COR_FASE3, COR_FASE4, COR_EXERCITO
    };

    private static final String[] CHAVES = {"fase0", "fase1", "fase2", "fase3", "exercito"};

    private JSONObject memorial = new JSONObject();
    private int modo = 0;      // 0 = trilhas, 1 = fatos de uma trilha, 2 = linha do tempo
    private int trilhaAtual = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }
        if (state != null) {
            modo = state.getInt("modo", 0);
            trilhaAtual = state.getInt("trilha", 0);
        }
        carregar();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("modo", modo);
        out.putInt("trilha", trilhaAtual);
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

    private JSONArray fatosDaTrilha(int trilha) {
        JSONArray a = memorial.optJSONArray(CHAVES[trilha]);
        return a == null ? new JSONArray() : a;
    }

    private int totalFatos() {
        int total = 0;
        for (int i = 0; i < TRILHAS.length; i++) total += fatosDaTrilha(i).length();
        return total;
    }

    // ------------------------------------------------------------------

    private void montar() {
        if (modo == 0) montarTrilhas();
        else if (modo == 1) montarTrilha(trilhaAtual);
        else montarLinhaDoTempo();
    }

    private void montarTrilhas() {
        ScrollView rolagem = tela("Memorial da minha vida",
                "Registre suas histórias militares nas 5 trilhas e veja tudo junto na linha do tempo.");
        LinearLayout coluna = coluna(rolagem);

        int total = totalFatos();
        LinearLayout resumo = cartao();
        TextView grande = new TextView(this);
        grande.setText(total == 0 ? "Comece a sua história" : total
                + (total == 1 ? " fato registrado" : " fatos registrados"));
        grande.setTextColor(AZUL_MARINHA);
        grande.setTextSize(19);
        grande.setTypeface(Typeface.DEFAULT_BOLD);
        resumo.addView(grande);
        TextView detalhe = new TextView(this);
        detalhe.setText("Cada fato guarda título, data, local e a sua narrativa — criptografados.");
        detalhe.setTextColor(CINZA_TEXTO);
        detalhe.setTextSize(12.5f);
        detalhe.setPadding(0, px(2), 0, 0);
        resumo.addView(detalhe);
        coluna.addView(resumo, largura());

        View linhaDoTempo = botao("★  Ver minha linha do tempo", true);
        linhaDoTempo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modo = 2;
                montar();
            }
        });
        coluna.addView(linhaDoTempo, largura());

        for (int i = 0; i < TRILHAS.length; i++) {
            final int trilha = i;
            JSONArray fatos = fatosDaTrilha(trilha);
            LinearLayout externo = cartaoFaixa(TRILHAS_COR[trilha]);
            LinearLayout c = conteudoFaixa(externo);
            c.setOrientation(LinearLayout.VERTICAL);

            LinearLayout topo = new LinearLayout(this);
            topo.setOrientation(LinearLayout.HORIZONTAL);
            topo.setGravity(Gravity.CENTER_VERTICAL);
            View emblema = emblema(trilha == 4 ? "EX" : String.valueOf(trilha + 1), TRILHAS_COR[trilha]);
            LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(px(34), px(34));
            lpE.rightMargin = px(10);
            topo.addView(emblema, lpE);
            TextView t = new TextView(this);
            t.setText(TRILHAS[trilha]);
            t.setTextColor(AZUL_MARINHA);
            t.setTextSize(15.5f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            topo.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            c.addView(topo);

            TextView s = new TextView(this);
            s.setText(TRILHAS_SUB[trilha]);
            s.setTextColor(CINZA_TEXTO);
            s.setTextSize(12.5f);
            s.setPadding(0, px(3), 0, 0);
            c.addView(s);

            TextView contagem = new TextView(this);
            contagem.setText(fatos.length() == 0
                    ? "Toque para registrar o primeiro fato"
                    : fatos.length() + (fatos.length() == 1 ? " fato — toque para abrir"
                                                            : " fatos — toque para abrir"));
            contagem.setTextColor(fatos.length() == 0 ? 0xFF8A9BB5 : TRILHAS_COR[trilha]);
            contagem.setTextSize(12);
            contagem.setTypeface(Typeface.DEFAULT_BOLD);
            contagem.setPadding(0, px(6), 0, 0);
            c.addView(contagem);

            externo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    modo = 1;
                    trilhaAtual = trilha;
                    montar();
                }
            });
            coluna.addView(externo, largura());
        }

        TextView dica = texto("As cores identificam cada capítulo: azul (antes), dourado (concurso), "
                + "verde (FAB), cinza (baixa) e verde-oliva (Exército).");
        dica.setTextSize(11.5f);
        coluna.addView(dica);
        setContentView(rolagem);
    }

    private void montarTrilha(final int trilha) {
        JSONArray fatos = ordenar(fatosDaTrilha(trilha));
        salvarTrilha(trilha, fatos);

        ScrollView rolagem = tela(TRILHAS[trilha], TRILHAS_SUB[trilha]);
        LinearLayout coluna = coluna(rolagem);

        View voltar = botao("← Todas as trilhas", false);
        voltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modo = 0;
                montar();
            }
        });
        coluna.addView(voltar, largura());

        if (fatos.length() == 0) {
            coluna.addView(texto("Nada registrado aqui ainda. Cada história merece ser contada!"));
        }
        for (int i = 0; i < fatos.length(); i++) {
            final int indice = i;
            JSONObject f = fatos.optJSONObject(i);
            if (f == null) continue;
            LinearLayout externo = cartaoFaixa(TRILHAS_COR[trilha]);
            LinearLayout c = conteudoFaixa(externo);
            c.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(this);
            t.setText(f.optString("titulo", "(sem título)"));
            t.setTextColor(AZUL_MARINHA);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(t);
            String linha = f.optString("data", "");
            if (!f.optString("local", "").isEmpty()) {
                linha += (linha.isEmpty() ? "" : " • ") + f.optString("local");
            }
            if (!linha.isEmpty()) {
                TextView l = texto(linha);
                l.setTextColor(TRILHAS_COR[trilha]);
                l.setTypeface(Typeface.DEFAULT_BOLD);
                l.setTextSize(12.5f);
                c.addView(l);
            }
            String desc = f.optString("descricao", "");
            if (!desc.isEmpty()) c.addView(texto(desc));

            LinearLayout botoes = new LinearLayout(this);
            botoes.setOrientation(LinearLayout.HORIZONTAL);
            View editar = botao("Editar", false);
            editar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { dialogoFato(trilha, indice); }
            });
            View remover = botao("Remover", false);
            remover.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { removerFato(trilha, indice); }
            });
            LinearLayout.LayoutParams metade = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams metade2 = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            metade2.leftMargin = px(6);
            botoes.addView(editar, metade);
            botoes.addView(remover, metade2);
            c.addView(botoes, largura());
            coluna.addView(externo, largura());
        }

        View adicionar = botao("+ Adicionar fato nesta trilha", true);
        adicionar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoFato(trilha, -1); }
        });
        coluna.addView(adicionar, largura());
        setContentView(rolagem);
    }

    private void montarLinhaDoTempo() {
        ScrollView rolagem = tela("Minha linha do tempo",
                "Todos os seus fatos, do mais antigo ao mais recente, coloridos por trilha.");
        LinearLayout coluna = coluna(rolagem);

        View voltar = botao("← Voltar às trilhas", false);
        voltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modo = 0;
                montar();
            }
        });
        coluna.addView(voltar, largura());

        // junta tudo
        java.util.List<JSONObject> todos = new java.util.ArrayList<>();
        java.util.List<Integer> trilhaDe = new java.util.ArrayList<>();
        for (int trilha = 0; trilha < TRILHAS.length; trilha++) {
            JSONArray fatos = ordenar(fatosDaTrilha(trilha));
            for (int i = 0; i < fatos.length(); i++) {
                todos.add(fatos.optJSONObject(i));
                trilhaDe.add(trilha);
            }
        }
        // ordena por data (estável, mantendo pares com trilhaDe)
        Integer[] ordem = new Integer[todos.size()];
        for (int i = 0; i < ordem.length; i++) ordem[i] = i;
        java.util.Arrays.sort(ordem, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int c = todos.get(a).optString("data", "").compareTo(todos.get(b).optString("data", ""));
                return c != 0 ? c : Integer.compare(trilhaDe.get(a), trilhaDe.get(b));
            }
        });

        if (todos.isEmpty()) {
            coluna.addView(texto("Sua linha do tempo está esperando o primeiro fato. "
                    + "Escolha uma trilha e comece!"));
        }
        LinearLayout trilhaVisual = new LinearLayout(this);
        trilhaVisual.setOrientation(LinearLayout.VERTICAL);
        for (int k = 0; k < ordem.length; k++) {
            int idx = ordem[k];
            JSONObject f = todos.get(idx);
            int trilha = trilhaDe.get(idx);
            LinearLayout cartao = cartao();
            TextView legenda = new TextView(this);
            legenda.setText(TRILHAS[trilha].toUpperCase());
            legenda.setTextColor(TRILHAS_COR[trilha]);
            legenda.setTextSize(10.5f);
            legenda.setTypeface(Typeface.DEFAULT_BOLD);
            legenda.setLetterSpacing(0.08f);
            cartao.addView(legenda);
            TextView t = new TextView(this);
            t.setText(f.optString("titulo", "(sem título)"));
            t.setTextColor(AZUL_MARINHA);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(0, px(2), 0, 0);
            cartao.addView(t);
            String linha = f.optString("data", "");
            if (!f.optString("local", "").isEmpty()) {
                linha += (linha.isEmpty() ? "" : " • ") + f.optString("local");
            }
            if (!linha.isEmpty()) {
                TextView l = texto(linha);
                l.setTextSize(12.5f);
                c(l, 0xFF5F718C);
                cartao.addView(l);
            }
            String desc = f.optString("descricao", "");
            if (!desc.isEmpty()) {
                TextView d = texto(desc);
                d.setTextSize(13.5f);
                cartao.addView(d);
            }
            trilhaVisual.addView(itemLinhaDoTempo(TRILHAS_COR[trilha], cartao));
        }
        coluna.addView(trilhaVisual, largura());

        TextView fim = texto("★ Fim da linha do tempo — sua história continua sendo escrita.");
        fim.setGravity(Gravity.CENTER);
        fim.setTextSize(12);
        coluna.addView(fim);
        setContentView(rolagem);
    }

    private void c(TextView t, int cor) {
        t.setTextColor(cor);
    }

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

    private void salvarTrilha(int trilha, JSONArray fatos) {
        try {
            memorial.put(CHAVES[trilha], fatos);
        } catch (Exception ignored) {
        }
    }

    private void dialogoFato(final int trilha, final int indice) {
        JSONArray fatos = fatosDaTrilha(trilha);
        final JSONObject original = indice >= 0 && indice < fatos.length() ? fatos.optJSONObject(indice) : null;

        LinearLayout formulario = new LinearLayout(this);
        formulario.setOrientation(LinearLayout.VERTICAL);
        int p = px(18);
        formulario.setPadding(p, p, p, 0);

        final EditText titulo = campo("Fato/acontecimento (ex.: Aprovação no concurso do CESD)");
        final EditText data = campo("Data (AAAA-MM-DD)");
        data.setInputType(InputType.TYPE_CLASS_DATETIME);
        final EditText local = campo("Unidade/Cidade (ex.: 3ª Cia — Exército; CESD — FAB)");
        final EditText descricao = campoMultilinha("Conte essa história com suas palavras.", 4);
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
                        + " — " + TRILHAS[trilha])
                .setView(formulario)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        salvarFato(trilha, indice, titulo, data, local, descricao);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarFato(int trilha, int indice, EditText titulo, EditText data,
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
            JSONArray fatos = fatosDaTrilha(trilha);
            if (indice >= 0) fatos.put(indice, f);
            else fatos.put(f);
            salvarTrilha(trilha, ordenar(fatos));
            persistir();
            aviso(indice >= 0 ? "Fato atualizado." : "História registrada no memorial ✓");
        } catch (Exception e) {
            aviso("Não foi possível salvar o fato.");
        }
        montar();
    }

    private void removerFato(final int trilha, final int indice) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir o fato?")
                .setMessage("Este fato será apagado do memorial.")
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        JSONArray atual = fatosDaTrilha(trilha);
                        JSONArray nova = new JSONArray();
                        for (int i = 0; i < atual.length(); i++) {
                            if (i != indice) nova.put(atual.opt(i));
                        }
                        salvarTrilha(trilha, nova);
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
        if (modo == 2 || modo == 1) {
            modo = 0;
            montar();
        } else {
            super.onBackPressed();
        }
    }
}
