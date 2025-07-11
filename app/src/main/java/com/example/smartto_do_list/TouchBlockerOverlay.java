package com.example.smartto_do_list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TouchBlockerOverlay extends FrameLayout {

    private View allowedView = null;

    public TouchBlockerOverlay(@NonNull Context context) {
        super(context);
        init();
    }

    public TouchBlockerOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchBlockerOverlay(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true); // Ensure it intercepts all touches
    }

    public void setAllowedView(View view) {
        this.allowedView = view;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (allowedView != null) {
            int[] location = new int[2];
            allowedView.getLocationOnScreen(location);

            float x = ev.getRawX();
            float y = ev.getRawY();

            Rect bounds = new Rect(
                    location[0],
                    location[1],
                    location[0] + allowedView.getWidth(),
                    location[1] + allowedView.getHeight()
            );

            if (bounds.contains((int) x, (int) y)) {
                return false; // Allow touch to pass through to allowedView
            }
        }

        return true; // Block everything else
    }
}
