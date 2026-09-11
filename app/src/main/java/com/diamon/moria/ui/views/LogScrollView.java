package com.diamon.moria.ui.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * LogScrollView prevents automatic scrolling when children (like selectable TextView)
 * request focus or selection updates, ensuring stable custom scroll behavior for logs.
 */
public class LogScrollView extends ScrollView {

    public LogScrollView(Context context) {
        super(context);
    }

    public LogScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LogScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (focused != null) {
            super.requestChildFocus(child, null);
        } else {
            super.requestChildFocus(child, focused);
        }
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }
}
