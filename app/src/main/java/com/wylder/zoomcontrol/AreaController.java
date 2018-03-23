package com.wylder.zoomcontrol;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Created byr kevin on 1/8/18.
 *
 * 24:5B:A7:55:68:A0
 */

public class AreaController extends Thread {

    private static final int MAX_STORED = 50;
    MandelbrotView view;

    ArrayDeque<Area> loaded = new ArrayDeque<>();
    ArrayDeque<Area> current = new ArrayDeque<>();
    ZoomController controller;
    Area top = new Area("m", null);
    double depth;
    boolean running = true;

    public AreaController(MandelbrotView view) {
        this.view = view;
        Area.resources = view.getResources();
        start();
    }

    public void setWindow(ZoomController controller) {
        depth = Math.log(controller.getScreenSize() / (250.0 * controller.getScale())) / Math.log(3);
        this.controller = controller;

        // fill the deque with the visible areas
        ArrayDeque<Area> areas = new ArrayDeque<>();
        top.searchForAreas(controller, areas, (int) depth);

        current.clear();
        Area largestCurrent = top;
        for (Area area : areas) {
            if (area.representation.length() + 1 > depth) {
                Log.e("KevinRuntime", area.representation);
                // the end of the list has the deeper areas
                current.addLast(area);
                if (area.percentVisible(controller) > largestCurrent.percentVisible(controller)) {
                    largestCurrent = area;
                }
                // go up the tree and add the closest loaded parent.
                if (!area.loaded) {
                    Area a = area.parent;
                    while (a != null) {
                        current.addFirst(a);
                        if (!a.loaded) {
                            a = a.parent;
                        } else {
                            break;
                        }
                    }
                }
            }
        }

    }

    @Override
    public void run() {
        ArrayList<Area> areas = new ArrayList<>();
        while (running) {
            areas.clear();
            areas.addAll(current);
            // look for the best area to load
            Area candidate = null;
            for (Area area : areas) {
                if (!area.loaded) {
                    if (candidate == null) {
                        candidate = area;
                    } else {
                        if (
                                (candidate.representation.length() <= area.representation.length()) &&
                                        (candidate.percentVisible(controller) < area.percentVisible(controller))) {
                            candidate = area;
                        }
                    }
                } else {
                    loaded.remove(area);
                    loaded.addFirst(area);
                }
            }

            // load the area, or sleep
            if (candidate != null) {
                try {
                    candidate.load(view);
                    loaded.addFirst(candidate);
                    if (loaded.size() > MAX_STORED) {
                        Area a = loaded.removeLast();
                        if (a.representation.equals("m")) {
                            loaded.addFirst(a);
                            a = loaded.removeLast();
                        }
                        a.unload();
                    }
                } catch (OutOfMemoryError error) {
                    candidate.loaded = false;
                    System.gc();
                    error.printStackTrace();
                }
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
