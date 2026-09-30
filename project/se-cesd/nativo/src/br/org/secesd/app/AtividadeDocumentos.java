package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Documentos: QUATRO locais fixos para PDFs e imagens de documentos,
 * em moldura VERTICAL (retrato 3:4) e CENTRALIZADA, igual à Galeria.
 * Tudo salvo criptografado no cofre e levado pelo backup.
 */
public class AtividadeDocumentos extends AtividadeBase {

    private static final int MAXIMO = 4;
    private static final int PEDIR_DOC = 4312;
    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024; // 10 MB

    private JSONArray documentos = new JSONArray();
    private int slotPedindo = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }
        carregar();
    }

    private void carregar() {
        try {
            documentos = Cofre.lerDados(this).optJSONArray("documentos");
            if (documentos == null) documentos = new JSONArray();
        } catch (Exception e) {
            documentos = new JSONArray();
        }
        montar();
    }

    private JSONObject docDoSlot(int slot) {
        return slot < documentos.length() ? documentos.optJSONObject(slot) : null;
    }

    private void persistir() {
        try {
            JSONObject dados = Cofre.lerDados(this);
            dados.put("documentos", documentos);
            Cofre.salvarDados(this, dados);
        } catch (Exception e) {
            aviso("Não foi possível salvar: " + e.getMessage());
        }
    }

    private void montar() {
        ScrollView rolagem = tela("Documentos — 4 locais",
                "Guarde PDFs e imagens dos seus documentos (identidade, certificados, diplomas…). "
                        + "Criptografados no cofre e levados pelo backup do Drive.");
        LinearLayout coluna = coluna(rolagem);

        for (int slot = 0; slot < MAXIMO; slot++) {
            final int qual = slot;
            JSONObject doc = docDoSlot(slot);
            LinearLayout cartao = cartao();

            TextView rotulo = new TextView(this);
            rotulo.setText("DOCUMENTO " + (slot + 1) + " DE " + MAXIMO);
            rotulo.setTextColor(OURO);
            rotulo.setTextSize(11);
            rotulo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            rotulo.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cartao.addView(rotulo);

            TextView nome = new TextView(this);
            nome.setText(doc == null ? "(vazio)" : doc.optString("legenda", doc.optString("nome", "Documento")));
            nome.setTextColor(AZUL_MARINHA);
            nome.setTextSize(15);
            nome.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            nome.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            nome.setPadding(0, px(2), 0, px(4));
            if (doc != null) {
                nome.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { renomear(qual); }
                });
                nome.setText(nome.getText().toString() + "  ✎");
            }
            cartao.addView(nome);

            // moldura VERTICAL (retrato 3:4), CENTRALIZADA
            int larguraTela = getResources().getDisplayMetrics().widthPixels;
            int moldW = Math.min((int) (larguraTela * 0.62f), px(300));
            int moldH = (int) (moldW * 4f / 3f);

            ImageView pagina = new ImageView(this);
            pagina.setScaleType(ImageView.ScaleType.FIT_CENTER);
            boolean ehPdf = false;
            if (doc != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(doc.optString("doc"), android.util.Base64.NO_WRAP);
                    Bitmap b = null;
                    if ("imagem".equals(doc.optString("tipo"))) {
                        b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } else {
                        b = paginaDoPdf(bytes, 0);
                        ehPdf = true;
                    }
                    if (b != null) pagina.setImageBitmap(b);
                } catch (Exception ignored) {
                }
            }
            pagina.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (docDoSlot(qual) == null) escolher(qual);
                    else if ("pdf".equals(docDoSlot(qual).optString("tipo"))) verPaginas(qual);
                }
            });

            android.widget.FrameLayout quadro = new android.widget.FrameLayout(this);
            quadro.setBackground(arredondado(0xFFDCE9F8, px(10), 0xFF9DB8D9, px(1)));
            quadro.setPadding(px(4), px(4), px(4), px(4));
            TextView marcador = new TextView(this);
            marcador.setText("📄\nToque para\ninserir o documento");
            marcador.setTextColor(0xFF6B84A3);
            marcador.setTextSize(13);
            marcador.setGravity(android.view.Gravity.CENTER);
            marcador.setClickable(false);
            quadro.addView(marcador, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            quadro.addView(pagina, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout.LayoutParams moldura = new LinearLayout.LayoutParams(moldW, moldH);
            moldura.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            moldura.topMargin = px(6);
            cartao.addView(quadro, moldura);

            TextView dica = new TextView(this);
            dica.setText(doc == null ? "Toque na moldura para inserir aqui (PDF ou imagem, até 10 MB)."
                                     : (ehPdf ? "Toque na moldura para folhear, página por página."
                                              : "⟲ gira a imagem 90° anti-horário (fica gravado)."));
            dica.setTextColor(CINZA_TEXTO);
            dica.setTextSize(12);
            dica.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            dica.setPadding(0, px(4), 0, 0);
            cartao.addView(dica);

            if (doc != null) {
                LinearLayout botoes = new LinearLayout(this);
                botoes.setOrientation(LinearLayout.HORIZONTAL);
                View acao1;
                if (ehPdf) {
                    acao1 = botao("Ver páginas", false);
                    acao1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) { verPaginas(qual); }
                    });
                } else {
                    acao1 = botao("\u27f2 Girar", false);
                    acao1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) { girarImagem(qual); }
                    });
                }
                View remover = botao("Remover", false);
                remover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { remover(qual); }
                });
                LinearLayout.LayoutParams metadeA = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                LinearLayout.LayoutParams metadeB = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                metadeB.leftMargin = px(4);
                botoes.addView(acao1, metadeA);
                botoes.addView(remover, metadeB);
                cartao.addView(botoes);
            }
            coluna.addView(cartao, largura());
        }

        TextView contagem = texto(documentos.length() + " de " + MAXIMO + " locais ocupados."
                + (documentos.length() < MAXIMO ? " Toque em um local vazio para inserir o documento." : ""));
        contagem.setTextSize(12);
        coluna.addView(contagem);
        setContentView(rolagem);
    }

    private void escolher(int slot) {
        if (slot >= MAXIMO) {
            aviso("São " + MAXIMO + " locais no total.");
            return;
        }
        slotPedindo = slot;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Documento " + (slot + 1) + " — escolher (PDF ou imagem)"), PEDIR_DOC);
        } catch (Exception e) {
            aviso("Este aparelho não permite escolher arquivos.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PEDIR_DOC || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        int slot = Math.max(0, Math.min(MAXIMO - 1, slotPedindo));
        try {
            byte[] brutos = lerBytes(data.getData());
            if (brutos.length > TAMANHO_MAXIMO) {
                aviso("Documento muito grande (máximo 10 MB).");
                return;
            }
            if (brutos.length == 0) {
                aviso("Não foi possível abrir o arquivo.");
                return;
            }
            String nome = nomeDoArquivo(data.getData());

            // tenta como IMAGEM
            BitmapFactory.Options limites = new BitmapFactory.Options();
            limites.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(brutos, 0, brutos.length, limites);
            JSONObject doc = null;
            if (limites.outWidth > 0 && limites.outHeight > 0) {
                int amostra = 1;
                while (limites.outWidth / amostra > 1600 || limites.outHeight / amostra > 1600) amostra *= 2;
                BitmapFactory.Options opcoes = new BitmapFactory.Options();
                opcoes.inSampleSize = amostra;
                Bitmap b = BitmapFactory.decodeByteArray(brutos, 0, brutos.length, opcoes);
                b = comExif(new java.io.ByteArrayInputStream(brutos), b);
                if (b != null) {
                    doc = new JSONObject();
                    doc.put("tipo", "imagem");
                    doc.put("doc", android.util.Base64.encodeToString(jpeg(b, 1280), android.util.Base64.NO_WRAP));
                    doc.put("legenda", nome.isEmpty() ? "Documento " + (slot + 1) : nome);
                }
            }
            // tenta como PDF
            if (doc == null) {
                Bitmap prova = paginaDoPdf(brutos, 0);
                if (prova == null) {
                    aviso("Formato não suportado: use PDF ou imagem (JPG/PNG).");
                    return;
                }
                doc = new JSONObject();
                doc.put("tipo", "pdf");
                doc.put("doc", android.util.Base64.encodeToString(brutos, android.util.Base64.NO_WRAP));
                doc.put("legenda", nome.isEmpty() ? "Documento " + (slot + 1) : nome);
            }

            while (documentos.length() <= slot) documentos.put(new JSONObject());
            documentos.put(slot, doc);
            while (documentos.length() > 0 && documentos.optJSONObject(documentos.length() - 1) != null
                    && documentos.optJSONObject(documentos.length() - 1).length() == 0
                    && documentos.length() > slot + 1) {
                documentos.remove(documentos.length() - 1);
            }
            persistir();
            aviso("Documento guardado ✓ criptografado.");
            montar();
        } catch (Exception e) {
            aviso("Não foi possível inserir o documento.");
        }
    }

    private byte[] lerBytes(Uri uri) throws Exception {
        java.io.InputStream entrada = getContentResolver().openInputStream(uri);
        if (entrada == null) throw new Exception("não abriu");
        java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
        byte[] p = new byte[8192];
        int n;
        while ((n = entrada.read(p)) != -1) saida.write(p, 0, n);
        entrada.close();
        return saida.toByteArray();
    }

    private String nomeDoArquivo(Uri uri) {
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0 && c.moveToFirst() && c.getString(i) != null) return c.getString(i);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** Renderiza uma página do PDF para pré-visualização (0 = primeira). */
    private Bitmap paginaDoPdf(byte[] bytes, int pagina) {
        java.io.File tmp = new java.io.File(getCacheDir(), "doc-preview.pdf");
        try {
            java.io.FileOutputStream saida = new java.io.FileOutputStream(tmp);
            saida.write(bytes);
            saida.close();
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY);
            android.graphics.pdf.PdfRenderer render = new android.graphics.pdf.PdfRenderer(fd);
            if (pagina >= render.getPageCount()) {
                render.close();
                return null;
            }
            android.graphics.pdf.PdfRenderer.Page p = render.openPage(pagina);
            int h = 1000;
            int w = Math.max(1, h * p.getWidth() / p.getHeight());
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.eraseColor(0xFFFFFFFF);
            p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            p.close();
            render.close();
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            tmp.delete();
        }
    }

    private android.graphics.pdf.PdfRenderer renderizadorPaginas;

    /** Folheia o PDF página por página — cada folha EXPANDIDA na largura da tela. */
    private void verPaginas(final int slot) {
        final JSONObject doc = docDoSlot(slot);
        if (doc == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(doc.optString("doc"), android.util.Base64.NO_WRAP);
            java.io.File tmp = new java.io.File(getCacheDir(), "doc-view.pdf");
            java.io.FileOutputStream saida = new java.io.FileOutputStream(tmp);
            saida.write(bytes);
            saida.close();
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY);
            if (renderizadorPaginas != null) {
                try { renderizadorPaginas.close(); } catch (Exception ignored) { }
            }
            renderizadorPaginas = new android.graphics.pdf.PdfRenderer(fd);
            tmp.delete();

            final int total = renderizadorPaginas.getPageCount();
            final int[] atual = {0};

            LinearLayout caixa = new LinearLayout(this);
            caixa.setOrientation(LinearLayout.VERTICAL);
            int p = px(12);
            caixa.setPadding(p, p, p, p);

            final ImageView folha = new ImageView(this);
            folha.setAdjustViewBounds(true);
            folha.setBackground(arredondado(0xFFFFFFFF, px(6), 0xFFC7D4E6, px(1)));
            caixa.addView(folha, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final TextView indicador = new TextView(this);
            indicador.setGravity(android.view.Gravity.CENTER);
            indicador.setTextColor(CINZA_TEXTO);
            indicador.setTextSize(12.5f);
            indicador.setPadding(0, px(8), 0, 0);
            caixa.addView(indicador, largura());

            LinearLayout navegar = new LinearLayout(this);
            navegar.setOrientation(LinearLayout.HORIZONTAL);
            final View anterior = botao("\u25c0 Anterior", false);
            final View proxima = botao("Pr\u00f3xima \u25b6", false);
            LinearLayout.LayoutParams mA = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams mB = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            mB.leftMargin = px(6);
            navegar.addView(anterior, mA);
            navegar.addView(proxima, mB);
            caixa.addView(navegar, largura());

            final Runnable desenhar = new Runnable() {
                @Override
                public void run() {
                    try {
                        android.graphics.pdf.PdfRenderer.Page pg = renderizadorPaginas.openPage(atual[0]);
                        int w = getResources().getDisplayMetrics().widthPixels - px(56);
                        int h = Math.max(1, w * pg.getHeight() / pg.getWidth());
                        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        b.eraseColor(0xFFFFFFFF);
                        pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        pg.close();
                        folha.setImageBitmap(b);
                        indicador.setText("P\u00e1gina " + (atual[0] + 1) + " de " + total);
                        anterior.setEnabled(atual[0] > 0);
                        proxima.setEnabled(atual[0] < total - 1);
                        anterior.setAlpha(atual[0] > 0 ? 1f : 0.4f);
                        proxima.setAlpha(atual[0] < total - 1 ? 1f : 0.4f);
                    } catch (Exception e) {
                        aviso("Falha ao desenhar a p\u00e1gina.");
                    }
                }
            };

            anterior.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (atual[0] > 0) { atual[0]--; desenhar.run(); }
                }
            });
            proxima.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (atual[0] < total - 1) { atual[0]++; desenhar.run(); }
                }
            });

            AlertDialog dialogo = new AlertDialog.Builder(this)
                    .setTitle(doc.optString("legenda", "Documento"))
                    .setView(caixa)
                    .setPositiveButton("Fechar", null)
                    .create();
            dialogo.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    if (renderizadorPaginas != null) {
                        try { renderizadorPaginas.close(); } catch (Exception ignored) { }
                        renderizadorPaginas = null;
                    }
                }
            });
            dialogo.show();
            desenhar.run();
        } catch (Exception e) {
            aviso("N\u00e3o consegui abrir as p\u00e1ginas deste documento.");
        }
    }

    private void girarImagem(int slot) {
        JSONObject doc = docDoSlot(slot);
        if (doc == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(doc.optString("doc"), android.util.Base64.NO_WRAP);
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b == null) { aviso("Não consegui girar esta imagem."); return; }
            b = girar(b, 270);
            doc.put("doc", android.util.Base64.encodeToString(jpeg(b, 1280), android.util.Base64.NO_WRAP));
            persistir();
            montar();
        } catch (Exception e) {
            aviso("Não consegui girar esta imagem.");
        }
    }

    private void renomear(final int slot) {
        JSONObject doc = docDoSlot(slot);
        if (doc == null) return;
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        int p = px(20);
        caixa.setPadding(p, p / 2, p, 0);
        final EditText campo = new EditText(this);
        campo.setHint("Nome do documento");
        campo.setText(doc.optString("legenda", doc.optString("nome", "")));
        caixa.addView(campo);
        new AlertDialog.Builder(this)
                .setTitle("Renomear documento")
                .setView(caixa)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        JSONObject doc = docDoSlot(slot);
                        if (doc == null) return;
                        String novo = campo.getText().toString().trim();
                        if (novo.isEmpty()) { aviso("Nome vazio — mantive o anterior."); return; }
                        try { doc.put("legenda", novo); } catch (Exception ignored) { }
                        persistir();
                        montar();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void remover(int slot) {
        JSONObject doc = docDoSlot(slot);
        if (doc == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Remover documento")
                .setMessage("Remover “" + doc.optString("legenda", "Documento") + "” deste local?")
                .setPositiveButton("Remover", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (slot < documentos.length()) documentos.remove(slot);
                        while (documentos.length() > 0
                                && documentos.optJSONObject(documentos.length() - 1) != null
                                && documentos.optJSONObject(documentos.length() - 1).length() == 0) {
                            documentos.remove(documentos.length() - 1);
                        }
                        persistir();
                        montar();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
