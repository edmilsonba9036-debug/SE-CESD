package br.org.secesd.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * ImageView com ampliação para leitura:
 * pinça (dois dedos), arrastar e TOQUE DUPLO (amplia/desfaz).
 * A imagem começa encaixada no espaço disponível; zoom entre 1x e 5x.
 */
public class ImagemComZoomView extends ImageView {

    private final Matrix matriz = new Matrix();
    private final ScaleGestureDetector pinca;
    private final GestureDetector gestos;
    private float zoom = 1f; // fator sobre o encaixe inicial (1x = página inteira)

    public ImagemComZoomView(Context c) {
        super(c);
        pinca = new ScaleGestureDetector(c, new Pinca());
        gestos = new GestureDetector(c, new Gestos());
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setImageBitmap(Bitmap b) {
        super.setImageBitmap(b);
        encaixar();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        encaixar();
    }

    /** Encaixa a imagem inteira, centrada, sem corte (zoom volta a 1x). */
    private void encaixar() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float iw = d.getIntrinsicWidth();
        float ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) return;
        float esc = Math.min(getWidth() / iw, getHeight() / ih);
        zoom = 1f;
        matriz.reset();
        matriz.postScale(esc, esc);
        matriz.postTranslate((getWidth() - iw * esc) / 2f, (getHeight() - ih * esc) / 2f);
        setImageMatrix(matriz);
    }

    @Override
    public boolean onTouchEvent(MotionEvent evento) {
        pinca.onTouchEvent(evento);
        gestos.onTouchEvent(evento);
        return true;
    }

    private class Pinca extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float f = d.getScaleFactor();
            float novo = zoom * f;
            if (novo < 1f) f = 1f / zoom;
            if (novo > 5f) f = 5f / zoom;
            matriz.postScale(f, f, d.getFocusX(), d.getFocusY());
            zoom *= f;
            setImageMatrix(matriz);
            return true;
        }
    }

    private class Gestos extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent de, MotionEvent para, float dx, float dy) {
            if (zoom > 1f) {
                matriz.postTranslate(-dx, -dy);
                setImageMatrix(matriz);
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (zoom > 1f) {
                encaixar();
            } else {
                matriz.postScale(2.5f, 2.5f, e.getX(), e.getY());
                zoom = 2.5f;
                setImageMatrix(matriz);
            }
            return true;
        }
    }
}
