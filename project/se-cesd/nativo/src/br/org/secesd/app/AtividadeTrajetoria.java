package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
 * Minha trajetória: etapas ordenadas por data (título, data, local, descrição).
 */
public class AtividadeTrajetoria extends AtividadeBase {

    private JSONArray etapas = new JSONArray();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }
        carregar();
    }

    private EditText fTitulo, fData, fLocal, fDescricao;
    private TextView rotuloForm;
    private View btnCancelarEdicao;
    private ScrollView rolagemRef;
    private int editIndice = -1;

    private void carregar() {
        try {
            etapas = Cofre.lerDados(this).optJSONArray("trajetoria");
            if (etapas == null) etapas = new JSONArray();
        } catch (Exception e) {
            etapas = new JSONArray();
        }
        montar();
    }

    private void montar() {
        ScrollView rolagem = tela("Minha trajetória",
                "Registre toda a sua caminhada, desde antes do concurso até a saída da Força Aérea. "
                        + "As etapas aparecem em ordem de data.");
        LinearLayout coluna = coluna(rolagem);
        rolagemRef = rolagem;

        // FÓRMULÁRIO SEMPRE À VISTA (sem janelinhas): nova/editar etapa
        LinearLayout form = cartao();
        rotuloForm = new TextView(this);
        rotuloForm.setText(editIndice >= 0
                ? "EDITANDO ETAPA — altere os campos e toque em Salvar"
                : "REGISTRAR ETAPA — os campos estão aqui à vista");
        rotuloForm.setTextColor(AZUL_MARINHA);
        rotuloForm.setTextSize(13);
        rotuloForm.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        form.addView(rotuloForm);
        fTitulo = campo("Título (ex.: Aprovação no concurso do CESD)");
        fData = campo("Data (AAAA-MM-DD)");
        fData.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        fLocal = campo("Local ou OM (opcional)");
        fDescricao = campoMultilinha("Conte essa etapa com suas palavras.", 3);
        form.addView(fTitulo, largura());
        form.addView(fData, largura());
        form.addView(fLocal, largura());
        form.addView(fDescricao, largura());
        View salvarEtapaBtn = botao(editIndice >= 0 ? "Salvar alterações" : "Salvar etapa", true);
        salvarEtapaBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { salvarDaTela(); }
        });
        form.addView(salvarEtapaBtn, largura());
        btnCancelarEdicao = botao("Cancelar edição", false);
        btnCancelarEdicao.setVisibility(editIndice >= 0 ? View.VISIBLE : View.GONE);
        btnCancelarEdicao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { preencherFormulario(-1); }
        });
        form.addView(btnCancelarEdicao, largura());
        coluna.addView(form, largura());

        // ordena por data (string yyyy-mm-dd)
        try {
            java.util.List<JSONObject> lista = new java.util.ArrayList<>();
            for (int i = 0; i < etapas.length(); i++) lista.add(etapas.getJSONObject(i));
            java.util.Collections.sort(lista, new java.util.Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optString("data").compareTo(b.optString("data"));
                }
            });
            etapas = new JSONArray();
            for (JSONObject e : lista) etapas.put(e);
        } catch (Exception ignored) {
        }

        if (etapas.length() == 0) {
            coluna.addView(texto("Nenhuma etapa ainda. Clique em “Adicionar etapa” para começar."));
        }
        for (int i = 0; i < etapas.length(); i++) {
            final int indice = i;
            try {
                JSONObject e = etapas.getJSONObject(i);
                LinearLayout cartao = cartao();
                TextView t = new TextView(this);
                t.setText(e.optString("titulo", "(sem título)"));
                t.setTextColor(AZUL_MARINHA);
                t.setTextSize(15);
                t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                cartao.addView(t);
                String linha = e.optString("data", "") + (e.optString("local").isEmpty() ? "" : " • " + e.optString("local"));
                cartao.addView(texto(linha));
                String desc = e.optString("descricao", "");
                if (!desc.isEmpty()) cartao.addView(texto(desc));
                LinearLayout botoes = new LinearLayout(this);
                botoes.setOrientation(LinearLayout.HORIZONTAL);
                View editar = botao("Editar", false);
                editar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { preencherFormulario(indice); }
                });
                View remover = botao("Remover", false);
                remover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        remover(indice);
                    }
                });
                LinearLayout.LayoutParams metade = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                botoes.addView(editar, metade);
                botoes.addView(remover, metade);
                cartao.addView(botoes);
                coluna.addView(cartao, largura());
            } catch (Exception ignored) {
            }
        }

        View adicionar = botao("Adicionar etapa", true);
        adicionar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { preencherFormulario(-1); }
        });
        coluna.addView(adicionar, largura());
        setContentView(rolagem);
    }

    /** Carrega a etapa no formulário da tela (ou limpa, para uma nova). */
    private void preencherFormulario(int indice) {
        editIndice = indice;
        montar();
        if (indice >= 0) {
            JSONObject e = etapas.optJSONObject(indice);
            if (e != null) {
                fTitulo.setText(e.optString("titulo"));
                fData.setText(e.optString("data"));
                fLocal.setText(e.optString("local"));
                fDescricao.setText(e.optString("descricao"));
            }
        }
        if (rolagemRef != null) rolagemRef.smoothScrollTo(0, 0);
    }

    /** Salva lendo os campos SEMPRE VISÍVEIS no topo da tela. */
    private void salvarDaTela() {
        if (fTitulo.getText().toString().trim().length() < 3) {
            aviso("Dê um título à etapa (mínimo de 3 letras).");
            return;
        }
        salvar(editIndice, fTitulo, fData, fLocal, fDescricao);
        editIndice = -1;
        montar();
        if (rolagemRef != null) rolagemRef.smoothScrollTo(0, 0);
    }

    private void dialogo(final int indice) {
        final JSONObject original = indice >= 0 && indice < etapas.length() ? etapas.optJSONObject(indice) : null;
        LinearLayout formulario = new LinearLayout(this);
        formulario.setOrientation(LinearLayout.VERTICAL);
        int p = px(18);
        formulario.setPadding(p, p, p, 0);
        final EditText titulo = campo("Título (ex.: Aprovação no concurso do CESD)");
        final EditText data = campo("Data (AAAA-MM-DD)");
        data.setInputType(InputType.TYPE_CLASS_DATETIME);
        final EditText local = campo("Local ou OM (opcional)");
        final EditText descricao = campoMultilinha("Conte essa etapa com suas palavras.", 3);
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
                .setTitle(indice >= 0 ? "Editar etapa" : "Nova etapa")
                .setView(formulario)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        salvar(indice, titulo, data, local, descricao);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvar(int indice, EditText titulo, EditText data, EditText local, EditText descricao) {
        String t = titulo.getText().toString().trim();
        if (t.length() < 3) {
            aviso("Dê um título à etapa (mínimo de 3 letras).");
            return;
        }
        try {
            JSONObject e = new JSONObject();
            e.put("titulo", t);
            e.put("data", data.getText().toString().trim());
            e.put("local", local.getText().toString().trim());
            e.put("descricao", descricao.getText().toString().trim());
            if (indice >= 0) etapas.put(indice, e);
            else etapas.put(e);
            persistir();
            aviso(indice >= 0 ? "Etapa atualizada." : "Etapa adicionada à trajetória.");
        } catch (Exception ex) {
            aviso("Não foi possível salvar a etapa.");
        }
    }

    private void remover(int indice) {
        JSONArray nova = new JSONArray();
        for (int i = 0; i < etapas.length(); i++) {
            if (i != indice) nova.put(etapas.opt(i));
        }
        etapas = nova;
        persistir();
        aviso("Etapa excluída.");
    }

    private void persistir() {
        try {
            org.json.JSONObject dados = Cofre.lerDados(this);
            dados.put("trajetoria", etapas);
            Cofre.salvarDados(this, dados);
            agendarBackup();
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
            return;
        }
        montar();
    }
}
