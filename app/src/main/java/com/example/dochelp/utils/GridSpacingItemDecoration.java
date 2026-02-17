package com.example.dochelp.utils;

import com.example.dochelp.R;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
    private final int spacing;

    public GridSpacingItemDecoration(int spacingPx) { this.spacing = spacingPx; }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        outRect.left = spacing;
        outRect.right = spacing;
        outRect.bottom = spacing;
        if (parent.getChildAdapterPosition(view) == 0) outRect.top = spacing;
    }
}

