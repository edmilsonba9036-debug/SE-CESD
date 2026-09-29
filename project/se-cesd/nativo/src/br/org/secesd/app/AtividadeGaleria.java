package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Galeria: até 4 fotos pessoais da história na Força Aérea.
 */
public class AtividadeGaleria extends AtividadeBase {

    private static final int PEDIR_FOTO = 4302;
    private static final int MAXIMO = 4;

    private JSONArray galeria = new JSONArray();
    private LinearLayout lista;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }
        carregar();
    }

    private void carregar() {
        try {
            galeria = Cofre.lerDados(this).optJSONArray("galeria");
            if (galeria == null) galeria = new JSONArray();
        } catch (Exception e) {
            galeria = new JSONArray();
        }
        montar();
    }

    private void montar() {
        ScrollView rolagem = tela("Galeria", "Adicione até 4 fotos pessoais da sua história na Força Aérea. "
                + "Salvas na hora, criptografadas, sem localização (GPS).");
        LinearLayout coluna = coluna(rolagem);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        coluna.addView(lista);

        if (galeria.length() == 0) {
            lista.addView(texto("Nenhuma foto ainda."));
        }
        for (int i = 0; i < galeria.length(); i++) {
            final int indice = i;
            JSONObject f = galeria.optJSONObject(i);
            if (f == null) continue;
            LinearLayout cartao = cartao();
            TextView legenda = new TextView(this);
            legenda.setText(f.optString("legenda", "Foto " + (i + 1)));
            legenda.setTextColor(AZUL_MARINHA);
            legenda.setTextSize(14);
            legenda.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            cartao.addView(legenda);
            ImageView imagem = new ImageView(this);
            imagem.setAdjustViewBounds(true);
            imagem.setMaxHeight(px(240));
            try {
                byte[] bytes = android.util.Base64.decode(f.optString("foto"), android.util.Base64.NO_WRAP);
                imagem.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            } catch (Exception e) {
                imagem.setMinimumHeight(px(80));
            }
            imagem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { ampliar(indice); }
            });
            cartao.addView(imagem, largura());
            View remover = botao("Remover foto", false);
            remover.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { remover(indice); }
            });
            cartao.addView(remover, largura());
            lista.addView(cartao, largura());
        }

        if (galeria.length() < MAXIMO) {
            View adicionar = botao("Adicionar foto (" + galeria.length() + "/" + MAXIMO + ")", true);
            adicionar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { escolher(); }
            });
            coluna.addView(adicionar, largura());
        }
        setContentView(rolagem);
    }

    private void ampliar(int indice) {
        JSONObject f = galeria.optJSONObject(indice);
        if (f == null) return;
        ImageView grande = new ImageView(this);
        grande.setAdjustViewBounds(true);
        try {
            byte[] bytes = android.util.Base64.decode(f.optString("foto"), android.util.Base64.NO_WRAP);
            grande.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } catch (Exception ignored) {
            return;
        }
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(grande);
        new AlertDialog.Builder(this).setView(rolagem)
                .setPositiveButton("Fechar", null).show();
    }

    private void remover(int indice) {
        JSONArray nova = new JSONArray();
        for (int i = 0; i < galeria.length(); i++) {
            if (i != indice) nova.put(galeria.opt(i));
        }
        galeria = nova;
        persistir();
        aviso("Foto removida.");
    }

    private void escolher() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Escolher foto"), PEDIR_FOTO);
        } catch (Exception e) {
            aviso("Este aparelho não permite escolher imagens.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PEDIR_FOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            BitmapFactory.Options limites = new BitmapFactory.Options();
            limites.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()), null, limites);
            int amostra = 1;
            while (limites.outWidth / amostra > 1600 || limites.outHeight / amostra > 1600) amostra *= 2;
            BitmapFactory.Options opcoes = new BitmapFactory.Options();
            opcoes.inSampleSize = amostra;
            Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()), null, opcoes);
            if (b == null) {
                aviso("Não foi possível abrir a foto.");
                return;
            }
            if (b.getWidth() > 1280) b = Bitmap.createScaledBitmap(b, 1280, b.getHeight() * 1280 / b.getWidth(), true);
            java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 85, saida);
            JSONObject foto = new JSONObject();
            foto.put("legenda", "Foto " + (galeria.length() + 1));
            foto.put("foto", android.util.Base64.encodeToString(saida.toByteArray(), android.util.Base64.NO_WRAP));
            galeria.put(foto);
            persistir();
            aviso("Foto adicionada — salva criptografada neste aparelho.");
        } catch (Exception e) {
            aviso("Não foi possível preparar a foto.");
        }
    }

    private void persistir() {
        try {
            org.json.JSONObject dados = Cofre.lerDados(this);
            dados.put("galeria", galeria);
            Cofre.salvarDados(this, dados);
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
            return;
        }
        montar();
    }
}
