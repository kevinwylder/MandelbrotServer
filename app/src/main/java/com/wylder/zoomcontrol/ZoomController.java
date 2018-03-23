package com.wylder.zoomcontrol;

import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;

import java.util.ArrayDeque;

/**
 * Created by kevin on 12/26/17.
 *
 */

public class ZoomController implements Runnable {

    class EventHistoryElement {

        EventHistoryElement(MandelbrotPoint point, double scale, double distance, long time) {
            this.real = point.real;
            this.imaginary = point.imaginary;
            this.scale = scale;
            this.distance = distance;
            this.time = time;
        }

        double real;
        double imaginary;
        double distance;
        double scale;
        long time;
    }

    private double realOffset = -2f;
    private double imagOffset = -2f;
    private double windowSize = 4f;
    private double windowRatio = -1;

    private static final double TRANSLATION_DECAY = .97;
    private static final double SCALE_DECAY = .9;
    private static final int ANIMATION_DELAY = 30;
    private static final int HISTORY_INTERPOLATION_TIME = 100;

    private ArrayDeque<EventHistoryElement> history = new ArrayDeque<>();
    private MandelbrotPoint translationalVelocity = new MandelbrotPoint(0, 0);
    private MandelbrotPoint lastScaleCenter = null;
    private double scaleVelocity = 0;
    private int fingers = 0;

    Handler handler = new Handler();

    private MandelbrotView view;

    public ZoomController(MandelbrotView view) {
        this.view = view;
    }

    private MandelbrotPoint getPosition(MotionEvent event) {
        float x, y;
        if (event.getPointerCount() == 2) {
            x = (event.getX(0) + event.getX(1)) / 2f;
            y = (event.getY(0) + event.getY(1)) / 2f;
        } else if (event.getPointerCount() == 1) {
            x = event.getX();
            y = event.getY();
        } else {
            x = 0;
            y = 0;
        }
        return new MandelbrotPoint(
                realOffset + (x * windowSize / (double) view.getWidth()),
                imagOffset + (y * windowSize / (double) view.getWidth())
        );
    }

    private double getDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        double dx = event.getX(0) - event.getX(1);
        double dy = event.getY(0) - event.getY(1);
        return Math.max(Math.sqrt(dx * dx + dy * dy), 50);
    }

    private void addToHistory(MandelbrotPoint pos, double scale, double distance) {
        long time = System.currentTimeMillis();
        EventHistoryElement thisFrame = new EventHistoryElement(pos, scale, distance, time);
        history.addFirst(thisFrame);
        while (history.size() > 0 && history.peekLast().time + HISTORY_INTERPOLATION_TIME < time) {
            history.pollLast();
        }
    }

    public int getScreenSize() {
        return view.getWidth();
    }

    private void computeScaleVelocity() {
        /*if (history.size() > 1) {
            EventHistoryElement first = history.getFirst();
            EventHistoryElement last = history.getLast();
            int dt = (int) (last.time - first.time);
            scaleVelocity = ANIMATION_DELAY * (last.scale - first.scale) / dt;
        }
        Log.e("KevinRuntime", " " + scaleVelocity);*/
    }

    private void computeTranslateVelocity() {
        /*if (history.size() > 1) {
            EventHistoryElement first = history.getFirst();
            EventHistoryElement last = history.getLast();
            int dt = (int) (first.time - last.time);
            Log.e("KevinRuntime", "recipe " + ANIMATION_DELAY + " * (" + last.real + " - " + first.real + ") / " + dt);
            Log.e("KevinRuntime", "is the same? " + (last == first));
            translationalVelocity = new MandelbrotPoint(
                    ANIMATION_DELAY * (last.real - first.real) / dt,
                    ANIMATION_DELAY * (last.imaginary - first.imaginary) / dt
            );
            Log.e("KevinRuntime", translationalVelocity.real + ", " + translationalVelocity.imaginary);
        }*/
    }

    private void setWindowSize(double newSize, MandelbrotPoint centered) {
        double oldWindowSize = windowSize;
        windowSize = newSize;
        realOffset += ((centered.real - realOffset) / windowSize) * (oldWindowSize - windowSize);
        imagOffset += ((centered.imaginary - imagOffset) / windowSize) * (oldWindowSize - windowSize);
    }

    public void feed(MotionEvent event) {
        if (windowRatio == -1) {
            this.windowRatio = ((double) view.getHeight() / (double) view.getWidth());
        }

        if (event.getPointerCount() > 2) {
            return;
        }

        MandelbrotPoint thisPosition = getPosition(event);
        double thisDistance = getDistance(event);
        int numFingers = event.getPointerCount() - ((event.getAction() == MotionEvent.ACTION_UP) ? 1 : 0);

        if (numFingers > fingers) {
            // added finger
            history.clear();
            translationalVelocity = new MandelbrotPoint(0, 0);
            scaleVelocity = 0;
            addToHistory(thisPosition, windowSize, thisDistance);
            if (numFingers == 2) {
                lastScaleCenter = new MandelbrotPoint(realOffset, imagOffset);
            }
        } else if (numFingers < fingers) {
            // dropped finger
            if (numFingers == 1) {
                // it was scaling
                computeScaleVelocity();
                history.clear();
            } else if (numFingers == 0) {
                // it was translating only
                computeTranslateVelocity();
                history.clear();
            }
        } else {
            // no finger up or down

            // ensure a point in history
            if (history.size() < 1) {
                addToHistory(thisPosition, windowSize, thisDistance);
            }

            EventHistoryElement recent = history.peekFirst();
            double dx = 0;
            double dy = 0;
            if (numFingers == 1) {
                dx = recent.real - thisPosition.real;
                dy = recent.imaginary - thisPosition.imaginary;
            } else if (numFingers == 2) {
                dx = recent.real - thisPosition.real;
                dy = recent.imaginary - thisPosition.imaginary;
                lastScaleCenter = thisPosition;
                setWindowSize(windowSize * recent.distance / thisDistance, thisPosition);
            }


            realOffset += dx;
            imagOffset += dy;
            thisPosition.real += dx;
            thisPosition.imaginary += dy;
            addToHistory(thisPosition, windowSize, thisDistance);
        }

        fingers = numFingers;
        lastCorners = null;
        handler.post(this);
    }

    public double getReal() {
        return realOffset;
    }

    public double getImag() {
        return imagOffset;
    }

    public double getScale() {
        return windowSize;
    }

    private MandelbrotPoint[] lastCorners = null;
    public MandelbrotPoint[] getCorners() {
        if (lastCorners == null) {
            lastCorners = new MandelbrotPoint[] {
                new MandelbrotPoint(realOffset, imagOffset),
                new MandelbrotPoint(realOffset + windowSize, imagOffset),
                new MandelbrotPoint(realOffset + windowSize, imagOffset + windowSize * windowRatio),
                new MandelbrotPoint(realOffset, imagOffset + windowSize * windowRatio)
            };
        }
        return lastCorners;
    }

    int moveCounter = 0;
    @Override
    public void run() {

        boolean moved = false;
        if (Math.abs(scaleVelocity / windowSize) > .001) {
            setWindowSize(windowSize + scaleVelocity, lastScaleCenter);
            scaleVelocity *= SCALE_DECAY;
            moved = true;
        }

        if (Math.abs(translationalVelocity.real / windowSize) > .001) {
            realOffset += translationalVelocity.real;
            translationalVelocity.real *= TRANSLATION_DECAY;
            moved = true;
        }

        if (Math.abs(translationalVelocity.imaginary / windowSize) > .001) {
            imagOffset += translationalVelocity.imaginary;
            translationalVelocity.imaginary *= TRANSLATION_DECAY;
            moved = true;
        }

        realOffset = Math.max(Math.min(realOffset + windowSize, 2) - windowSize, -2);
        imagOffset = Math.max(Math.min(imagOffset + windowSize, 2) - windowSize, -2);
        windowSize = Math.min(windowSize, 4);

        if (!moved || (moveCounter % 5 == 0)) {
            view.notifyWindowChange();
        } else {
            view.invalidate();
        }

        if (moved) {
            moveCounter++;
            handler.postDelayed(this, ANIMATION_DELAY);
        } else {
            moveCounter = 0;
        }
    }
}
