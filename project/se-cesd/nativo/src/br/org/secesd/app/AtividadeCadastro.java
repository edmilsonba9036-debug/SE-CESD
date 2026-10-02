package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Meu cadastro: fotografia + dados do Soldado Especializado.
 */
public class AtividadeCadastro extends AtividadeBase {

    private static final int PEDIR_FOTO = 4301;

    private EditText nomeCompleto, nomeGuerra, especialidade, om;
    private ImageView foto;
    private String fotoBase64;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }

        ScrollView rolagem = tela("Meu cadastro", "Adicione sua fotografia e preencha seus dados de Soldado Especializado.");
        LinearLayout coluna = coluna(rolagem);

        // Moldura VERTICAL (retrato 3:4) e CENTRALIZADA — padrão 3x4 militar.
        int larguraTelaC = getResources().getDisplayMetrics().widthPixels;
        int moldW = Math.min((int) (larguraTelaC * 0.62f), px(300));
        int moldH = (int) (moldW * 4f / 3f);

        foto = new ImageView(this);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP); // preenche a moldura: sem faixas
        foto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { escolherFoto(); }
        });

        android.widget.FrameLayout quadro = new android.widget.FrameLayout(this);
        quadro.setBackground(arredondado(0xFFDCE9F8, px(10), 0xFF9DB8D9, px(1)));
        quadro.setPadding(px(4), px(4), px(4), px(4));
        TextView marcador = new TextView(this);
        marcador.setText("📷\nToque para\ninserir a foto");
        marcador.setTextColor(0xFF6B84A3);
        marcador.setTextSize(13);
        marcador.setGravity(android.view.Gravity.CENTER);
        marcador.setClickable(false);
        quadro.addView(marcador, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        quadro.addView(foto, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams moldura = new LinearLayout.LayoutParams(moldW, moldH);
        moldura.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        moldura.topMargin = px(6);
        coluna.addView(quadro, moldura);

        TextView dicaFoto = texto("Toque na moldura para escolher a fotografia (JPG). "
                + "É salva neste aparelho, criptografada com a sua senha.");
        dicaFoto.setTextSize(12);
        dicaFoto.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        coluna.addView(dicaFoto);

        nomeCompleto = campo("Nome completo");
        nomeGuerra = campo("Nome de guerra");
        especialidade = campo("Especialidade (ex.: Segurança e Defesa)");
        om = campo("Organização Militar (ex.: Ala 13 — Guarulhos / SP)");
        nomeCompleto.addTextChangedListener(vigia());
        nomeGuerra.addTextChangedListener(vigia());
        especialidade.addTextChangedListener(vigia());
        om.addTextChangedListener(vigia());
        coluna.addView(nomeCompleto, largura());
        coluna.addView(nomeGuerra, largura());
        coluna.addView(especialidade, largura());
        coluna.addView(om, largura());

        View salvar = botao("Salvar alterações", true);
        salvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvar(false);
            }
        });
        coluna.addView(salvar, largura());

        View removerFoto = botao("Remover fotografia", false);
        removerFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fotoBase64 = null;
                foto.setImageDrawable(null);
            }
        });
        coluna.addView(removerFoto, largura());

        carregar();
        setContentView(rolagem);
    }

    private void carregar() {
        populando(true);
        try {
            org.json.JSONObject c = Cofre.lerDados(this).optJSONObject("cadastro");
            if (c == null) return;
            nomeCompleto.setText(c.optString("nomeCompleto"));
            nomeGuerra.setText(c.optString("nomeGuerra"));
            especialidade.setText(c.optString("especialidade"));
            om.setText(c.optString("om"));
            fotoBase64 = c.optString("foto", null);
            if (fotoBase64 != null && !fotoBase64.isEmpty()) {
                byte[] bytes = android.util.Base64.decode(fotoBase64, android.util.Base64.NO_WRAP);
                foto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }
        } catch (Exception e) {
            aviso("Não foi possível ler o cadastro: " + e.getMessage());
        }
        populando(false);
    }

    private void salvar(boolean silencioso) {
        String nome = nomeCompleto.getText().toString().trim();
        if (nome.length() < 3) {
            if (!silencioso) aviso("Informe seu nome completo.");
            return;
        }
        if (especialidade.getText().toString().trim().isEmpty()) {
            if (!silencioso) aviso("Informe sua especialidade.");
            return;
        }
        try {
            org.json.JSONObject dados = Cofre.lerDados(this);
            org.json.JSONObject c = new org.json.JSONObject();
            c.put("nomeCompleto", nome);
            c.put("nomeGuerra", nomeGuerra.getText().toString().trim());
            c.put("especialidade", especialidade.getText().toString().trim());
            c.put("om", om.getText().toString().trim());
            c.put("foto", fotoBase64 == null ? "" : fotoBase64);
            dados.put("cadastro", c);
            Cofre.salvarDados(this, dados);
            if (!silencioso) aviso("Cadastro salvo ✓ criptografado neste aparelho.");
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
        }
    }

    @Override
    protected void salvarConteudo() {
        salvar(true);
    }

    private void escolherFoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Escolher fotografia"), PEDIR_FOTO);
        } catch (Exception e) {
            aviso("Este aparelho não permite escolher imagens.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PEDIR_FOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap original = comExif(getContentResolver().openInputStream(data.getData()), decodificar(data.getData(), 1280));
            if (original == null) {
                aviso("Não foi possível abrir a foto. O arquivo pode estar danificado.");
                return;
            }
            Bitmap quadrado = cortarQuadrado(original);
            if (quadrado.getWidth() > 640) {
                quadrado = Bitmap.createScaledBitmap(quadrado, 640, 640, true);
            }
            java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
            quadrado.compress(Bitmap.CompressFormat.JPEG, 85, saida);
            fotoBase64 = android.util.Base64.encodeToString(saida.toByteArray(), android.util.Base64.NO_WRAP);
            foto.setImageBitmap(quadrado);
            aviso("Foto ajustada automaticamente.");
        } catch (Exception e) {
            aviso("Não foi possível preparar a foto.");
        }
    }

    private Bitmap decodificar(Uri uri, int maximo) throws Exception {
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opcoes);
        int amostra = 1;
        while (opcoes.outWidth / amostra > maximo * 2 || opcoes.outHeight / amostra > maximo * 2) {
            amostra *= 2;
        }
        opcoes = new BitmapFactory.Options();
        opcoes.inSampleSize = amostra;
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opcoes);
    }

    private Bitmap cortarQuadrado(Bitmap b) {
        int lado = Math.min(b.getWidth(), b.getHeight());
        int x = (b.getWidth() - lado) / 2;
        int y = (b.getHeight() - lado) / 2;
        return Bitmap.createBitmap(b, x, y, lado, lado);
    }
}
