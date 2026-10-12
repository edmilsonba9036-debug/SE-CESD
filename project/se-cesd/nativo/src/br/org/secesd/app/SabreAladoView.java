package br.org.secesd.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Sabre Alado — símbolo da Força Aérea Brasileira — desenhado em vetor,
 * em ouro (sem imagens externas). Lâmina para baixo, asas abertas para cima.
 */
public class SabreAladoView extends View {

    public SabreAladoView(Context contexto) {
        super(contexto);
    }

    @Override
    protected void onDraw(Canvas tela) {
        super.onDraw(tela);
        float w = getWidth();
        float h = getHeight();
        float lado = Math.min(w, h);
        float cx = w / 2f;
        float un = lado / 100f; // unidade lógica (0..100)

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFC9A227); // ouro
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.ROUND);

        // pomo (esfera no topo do punho)
        tela.drawCircle(cx, 12 * un, 4.5f * un, p);

        // punho
        RectF punho = new RectF(cx - 3 * un, 17 * un, cx + 3 * un, 30 * un);
        tela.drawRoundRect(punho, 3 * un, 3 * un, p);

        // guarda-mão (barra horizontal)
        RectF guarda = new RectF(cx - 16 * un, 30 * un, cx + 16 * un, 36 * un);
        tela.drawRoundRect(guarda, 3 * un, 3 * un, p);

        // lâmina (ponta para baixo)
        Path lamina = new Path();
        lamina.moveTo(cx - 4.2f * un, 36 * un);
        lamina.lineTo(cx + 4.2f * un, 36 * un);
        lamina.lineTo(cx, 86 * un);
        lamina.close();
        tela.drawPath(lamina, p);

        // asas: três penas de cada lado, varrendo para cima e para fora
        p.setStyle(Paint.Style.STROKE);
        float[] espessuras = {6f, 4.5f, 3.2f};
        float[] alcances = {14f, 10f, 4f};
        float[] alturas = {18f, 26f, 31f};
        for (int ladoAsa = 0; ladoAsa < 2; ladoAsa++) {
            int dir = ladoAsa == 0 ? -1 : 1;
            for (int i = 0; i < 3; i++) {
                p.setStrokeWidth(espessuras[i] * un);
                Path pena = new Path();
                pena.moveTo(cx + dir * 15 * un, 34 * un);
                pena.quadTo(
                        cx + dir * (15 + alcances[i]) * un, (34 - alturas[i] * 0.30f) * un,
                        cx + dir * (15 + alcances[i]) * un, (34 - alturas[i]) * un);
                tela.drawPath(pena, p);
            }
        }
    }
}
