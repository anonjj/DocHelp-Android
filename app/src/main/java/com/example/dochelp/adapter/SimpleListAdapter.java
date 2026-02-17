package com.example.dochelp.adapter;

import com.example.dochelp.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class SimpleListAdapter extends ArrayAdapter<String> {
    public SimpleListAdapter(@NonNull Context context, @NonNull List<String> data) {
        super(context, android.R.layout.simple_list_item_1, data);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        TextView tv = v.findViewById(android.R.id.text1);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextColor(0xFF333333);
        return v;
    }
}
