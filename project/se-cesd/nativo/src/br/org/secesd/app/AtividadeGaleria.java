package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Galeria: QUATRO locais fixos para as fotografias da história do SE.
 * Cada local tem legenda editável, e a foto fica salva criptografada.
 */
public class AtividadeGaleria extends AtividadeBase {

    private static final int PEDIR_FOTO = 4302;
    private static final int MAXIMO = 4;

    private JSONArray galeria = new JSONArray();
    private int slotPedindo = -1; // qual local (0..3) está escolhendo foto

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

    private JSONObject fotoDoSlot(int slot) {
        return slot < galeria.length() ? galeria.optJSONObject(slot) : null;
    }

    private void montar() {
        ScrollView rolagem = tela("Galeria — 4 locais para fotos",
                "Adicione até 4 fotografias da sua história na Força Aérea. "
                        + "Salvas na hora, criptografadas, sem localização (GPS). Toque na legenda para renomear.");
        LinearLayout coluna = coluna(rolagem);

        for (int slot = 0; slot < MAXIMO; slot++) {
            final int qual = slot;
            JSONObject foto = fotoDoSlot(slot);
            LinearLayout cartao = cartao();

            TextView rotulo = new TextView(this);
            rotulo.setText("LOCAL " + (slot + 1) + " DE 4");
            rotulo.setTextColor(OURO);
            rotulo.setTextSize(11);
            rotulo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            rotulo.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cartao.addView(rotulo);

            TextView legenda = new TextView(this);
            legenda.setText(foto == null ? "(vazio)" : foto.optString("legenda", "Foto " + (slot + 1)));
            legenda.setTextColor(AZUL_MARINHA);
            legenda.setTextSize(15);
            legenda.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            legenda.setPadding(0, px(2), 0, px(4));
            if (foto != null) {
                legenda.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { editarLegenda(qual); }
                });
                legenda.setText(legenda.getText().toString() + "  ✎");
            }
            legenda.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cartao.addView(legenda);

            // Moldura na POSIÇÃO VERTICAL (retrato 3:4), CENTRALIZADA na horizontal.
            int larguraTela = getResources().getDisplayMetrics().widthPixels;
            int moldW = Math.min((int) (larguraTela * 0.62f), px(300));
            int moldH = (int) (moldW * 4f / 3f);

            ImageView imagem = new ImageView(this);
            imagem.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (foto != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(foto.optString("foto"), android.util.Base64.NO_WRAP);
                    imagem.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                } catch (Exception e) {
                    aviso("Uma das fotos não pôde ser mostrada.");
                }
            }
            imagem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (fotoDoSlot(qual) == null) escolher(qual);
                    else ampliar(qual);
                }
            });

            android.widget.FrameLayout molduraF = new android.widget.FrameLayout(this);
            molduraF.setBackground(arredondado(0xFFDCE9F8, px(10), 0xFF9DB8D9, px(1)));
            molduraF.setPadding(px(4), px(4), px(4), px(4));
            molduraF.addView(imagem, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            TextView marcador = new TextView(this);
            marcador.setText("📷\nToque para\ninserir a foto");
            marcador.setTextColor(0xFF6B84A3);
            marcador.setTextSize(13);
            marcador.setGravity(android.view.Gravity.CENTER);
            marcador.setClickable(false);
            if (foto != null) marcador.setVisibility(android.view.View.GONE);
            molduraF.addView(marcador, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout.LayoutParams moldura = new LinearLayout.LayoutParams(moldW, moldH);
            moldura.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            moldura.topMargin = px(6);
            cartao.addView(molduraF, moldura);

            TextView dica = new TextView(this);
            dica.setText(foto == null ? "Toque na moldura para inserir a fotografia aqui."
                                      : "Toque na foto para ampliar.");
            dica.setTextColor(CINZA_TEXTO);
            dica.setTextSize(12);
            dica.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            dica.setPadding(0, px(4), 0, 0);
            cartao.addView(dica);

            if (foto != null) {
                LinearLayout botoes = new LinearLayout(this);
                botoes.setOrientation(LinearLayout.HORIZONTAL);
                View girar = botao("\u27f2 Girar", false);
                girar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { girarFoto(qual); }
                });
                View trocar = botao("Trocar", false);
                trocar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { escolher(qual); }
                });
                View remover = botao("Remover", false);
                remover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { remover(qual); }
                });
                LinearLayout.LayoutParams tercoA = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                LinearLayout.LayoutParams tercoB = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                LinearLayout.LayoutParams tercoC = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tercoB.leftMargin = px(4);
                tercoC.leftMargin = px(4);
                botoes.addView(girar, tercoA);
                botoes.addView(trocar, tercoB);
                botoes.addView(remover, tercoC);
                cartao.addView(botoes);
            }
            coluna.addView(cartao, largura());
        }

        TextView contagem = texto(galeria.length() + " de 4 locais ocupados."
                + (galeria.length() < MAXIMO ? " Toque em um local vazio para inserir a foto." : ""));
        contagem.setTextSize(12);
        coluna.addView(contagem);
        setContentView(rolagem);
    }

    private void editarLegenda(final int slot) {
        final JSONObject foto = fotoDoSlot(slot);
        if (foto == null) return;
        final EditText campo = new EditText(this);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        campo.setHint("Legenda da foto");
        campo.setText(foto.optString("legenda", "Foto " + (slot + 1)));
        new AlertDialog.Builder(this)
                .setTitle("Legenda do local " + (slot + 1))
                .setView(campo)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        String nova = campo.getText().toString().trim();
                        if (nova.isEmpty()) nova = "Foto " + (slot + 1);
                        try {
                            foto.put("legenda", nova);
                            persistir();
                        } catch (Exception ignored) {
                        }
                        montar();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Gira a foto 90° anti-horário e GRAVA girada (vale no app e no backup). */
    private void girarFoto(int slot) {
        JSONObject f = fotoDoSlot(slot);
        if (f == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(f.optString("foto"), android.util.Base64.NO_WRAP);
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b == null) { aviso("Não consegui girar essa foto."); return; }
            b = girar(b, 270);
            f.put("foto", android.util.Base64.encodeToString(jpeg(b, 1280), android.util.Base64.NO_WRAP));
            persistir();
            montar();
        } catch (Exception e) {
            aviso("Não consegui girar essa foto.");
        }
    }

    private void ampliar(int slot) {
        JSONObject f = fotoDoSlot(slot);
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
        new AlertDialog.Builder(this)
                .setTitle(f.optString("legenda", "Foto"))
                .setView(rolagem)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void remover(int slot) {
        JSONArray nova = new JSONArray();
        for (int i = 0; i < galeria.length(); i++) {
            if (i != slot) nova.put(galeria.opt(i));
        }
        galeria = nova;
        persistir();
        aviso("Local " + (slot + 1) + " liberado.");
        montar();
    }

    private void escolher(int slot) {
        if (slot >= MAXIMO) {
            aviso("São 4 locais no total.");
            return;
        }
        slotPedindo = slot;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Local " + (slot + 1) + " — escolher foto"), PEDIR_FOTO);
        } catch (Exception e) {
            aviso("Este aparelho não permite escolher imagens.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PEDIR_FOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        int slot = Math.max(0, Math.min(MAXIMO - 1, slotPedindo));
        try {
            BitmapFactory.Options limites = new BitmapFactory.Options();
            limites.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()), null, limites);
            int amostra = 1;
            while (limites.outWidth / amostra > 1600 || limites.outHeight / amostra > 1600) amostra *= 2;
            BitmapFactory.Options opcoes = new BitmapFactory.Options();
            opcoes.inSampleSize = amostra;
            Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()), null, opcoes);
            b = comExif(getContentResolver().openInputStream(data.getData()), b);
            if (b == null) {
                aviso("Não foi possível abrir a foto.");
                return;
            }
            if (b.getWidth() > 1280) b = Bitmap.createScaledBitmap(b, 1280, b.getHeight() * 1280 / b.getWidth(), true);
            java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 85, saida);

            JSONObject foto = new JSONObject();
            foto.put("legenda", "Foto " + (slot + 1));
            foto.put("foto", android.util.Base64.encodeToString(saida.toByteArray(), android.util.Base64.NO_WRAP));

            while (galeria.length() <= slot) galeria.put(new JSONObject()); // garante o índice do local
            galeria.put(slot, foto);
            // remove espaços vazios à direita para manter a ordem compacta
            while (galeria.length() > 0 && galeria.optJSONObject(galeria.length() - 1) != null
                    && galeria.optJSONObject(galeria.length() - 1).optString("foto").isEmpty()) {
                JSONArray menor = new JSONArray();
                for (int i = 0; i < galeria.length() - 1; i++) menor.put(galeria.opt(i));
                galeria = menor;
            }
            persistir();
            aviso("Foto inserida no local " + (slot + 1) + " ✓ salva criptografada.");
        } catch (Exception e) {
            aviso("Não foi possível preparar a foto.");
        }
        montar();
    }

    private void persistir() {
        try {
            org.json.JSONObject dados = Cofre.lerDados(this);
            dados.put("galeria", galeria);
            Cofre.salvarDados(this, dados);
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
        }
    }
}
