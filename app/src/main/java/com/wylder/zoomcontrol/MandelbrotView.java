package com.wylder.zoomcontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by kevin on 1/5/18.
 *
 * A view extension that brings together the AreaController and ZoomController, while accepting events
 */

public class MandelbrotView extends View {

    AreaController queue;
    ZoomController controller;

    public MandelbrotView(Context context) {
        super(context);
        controller = new ZoomController(this);
        queue = new AreaController(this);
        queue.setWindow(controller);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        controller.feed(event);
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        for (Area a : queue.current) {
            a.drawSelf(canvas, controller);
        }
    }

    public void notifyWindowChange() {
        queue.setWindow(controller);
        invalidate();
    }
}
