package com.wylder.zoomcontrol;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;

/**
 * Created by kevin on 1/5/18.
 *
 * Represents an area of the mandelbrot set that can be downloaded from the server
 */

public class Area {

    static Resources resources;

    final String representation;
    Drawable drawable = null;
    boolean loaded = false;

    double real;
    double imaginary;
    double size;

    Area[] children = new Area[9];
    Area parent;

    public Area(String representation, Area parent) {
        this.parent = parent;
        this.representation = representation;
        real = -2;
        imaginary = -2;
        size = 4;

        char[] str = representation.toCharArray();
        for (int i = 1; i < str.length; i++) {
            size /= 3;
            switch (str[i]) {
                case '7':
                case '8':
                case '9':
                    imaginary += size;
                case '4':
                case '5':
                case '6':
                    imaginary += size;
                default:
            }
            switch (str[i]) {
                case '3':
                case '6':
                case '9':
                    real += size;
                case '2':
                case '5':
                case '8':
                    real += size;
                default:
            }
        }

    }

    public void unload() {
        drawable = null;
        loaded = false;
    }


    public void load(MandelbrotView view) {
        if (representation.contains("0")) {
            Log.e("KevinRuntime", "Tried to get illegal tile " + representation);
            loaded = false;
            return;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), "mandelbrot");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                loaded = false;
                return;
            }
        }
        File img = new File(dir, representation + ".png");
        if (img.exists()) {
            // cached
            drawable = Drawable.createFromPath(img.getPath());
            view.postInvalidate();
        } else {
            Log.e("KevinRuntime", "Downloading " + representation);
            try {
                URL url = new URL("http://192.168.1.126/fractal/" + representation);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream stream = connection.getInputStream();

                if (representation.length() < 6) {
                    // save to file
                    if (!img.createNewFile()) {
                        loaded = false;
                    }
                    FileOutputStream fileOut = new FileOutputStream(img);
                    byte[] buffer = new byte[256];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, read);
                    }

                    // load the file
                    drawable = Drawable.createFromPath(img.getPath());
                } else {
                    // load directly
                    BufferedInputStream bis = new BufferedInputStream(stream, BUFFER_IO_SIZE);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    BufferedOutputStream bos = new BufferedOutputStream(baos, BUFFER_IO_SIZE);
                    copy(bis, bos);
                    bos.flush();

                    drawable = new BitmapDrawable(resources, BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size()));
                }
                stream.close();
                view.postInvalidate();
            } catch (IOException exception) {
                exception.printStackTrace();
                Log.e("KevinRuntime", "Couldn't download " + representation);
                loaded = false;
                return;
            }
        }
        loaded = true;
    }

    public void searchForAreas(ZoomController controller, ArrayDeque<Area> overlap, int depth) {
        overlap.addLast(this);
        if (depth <= 0) return;

        int left = 3;
        int right = -1;
        int top = 3;
        int bottom = -1;
        MandelbrotPoint[] corners = controller.getCorners();
        for (int i = 0; i < corners.length; i++) {
            // find where this corner lies in the area
            int col = (int) (3 * (corners[i].real - real) / size);
            int row = (int) (3 * (corners[i].imaginary - imaginary) / size);

            Log.e("KevinRuntime", "(" + col + ", " + row + ")");

            if (col > 2 || col < 0) {
                continue;
            }
            if (row > 2){
                if (i > 1) row = 2;
                else continue;
            } else if (row < 0) {
                if (i < 2) row = 0;
                else continue;
            }

            // offer this corner to the maximum variables
            left = Math.min(left, col);
            right = Math.max(right, col);
            top = Math.min(top, row);
            bottom = Math.max(bottom, row);
        }
        Log.e("KevinRuntime", "(" + left + ", " + top + ") -- (" + right + ", " + bottom + ")");
        // iterate across the maximums of each corner
        for (int col = left; col <= right; col++) {
            for (int row = top; row <= bottom; row++) {
                int index = col + 3 * row;
                if (children[index] == null) {
                    children[index] = new Area(representation + "123456789".toCharArray()[index], this);
                }
                // recursively add children to the search
                children[index].searchForAreas(controller, overlap, depth - 1);
            }
        }

    }

    public void drawSelf(Canvas canvas, ZoomController controller) {
        if (drawable == null) {
            return;
        }
        drawable.setBounds(
                (int) (canvas.getWidth() * (real - controller.getReal()) / controller.getScale()),
                (int) (canvas.getWidth() * (imaginary - controller.getImag()) / controller.getScale()),
                (int) (canvas.getWidth() * (real + size - controller.getReal()) / controller.getScale()),
                (int) (canvas.getWidth() * (imaginary + size - controller.getImag()) / controller.getScale())
        );
        drawable.draw(canvas);
    }

    public double percentVisible(ZoomController controller) {
        MandelbrotPoint[] window = controller.getCorners();
        double percentX = 1;
        double percentY = 1;
        if (real < window[0].real) {
            percentX -= (window[0].real - real) / size;
        }
        if (real + size > window[2].real) {
            percentX -= (real + size - window[2].real) / size;
        }
        if (imaginary < window[0].imaginary) {
            percentY -= (window[0].imaginary - imaginary) / size;
        }
        if (imaginary + size > window[2].imaginary) {
            percentY -= (imaginary + size - window[2].imaginary) / size;
        }
        return percentX * percentY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Area && ((Area) o).representation.equals(this.representation);
    }


    private static final int BUFFER_IO_SIZE = 8000;

    private void copy(final InputStream bis, final OutputStream baos) throws IOException {
        byte[] buf = new byte[256];
        int l;
        while ((l = bis.read(buf)) >= 0) baos.write(buf, 0, l);
    }

}
