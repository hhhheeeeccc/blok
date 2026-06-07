package com.jules.adblock;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppIsolationActivity extends AppCompatActivity {

    private AppAdapter adapter;
    private List<AppInfo> allApps = new ArrayList<>();
    private Set<String> isolatedApps;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_isolation);

        prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE);
        isolatedApps = new HashSet<>(prefs.getStringSet("isolated_apps", new HashSet<>()));

        RecyclerView recyclerView = findViewById(R.id.recycler_view_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadApps();

        adapter = new AppAdapter(allApps);
        recyclerView.setAdapter(adapter);

        EditText searchBox = findViewById(R.id.search_apps);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : apps) {
            // Skip self
            if (appInfo.packageName.equals(getPackageName())) continue;
            
            // Show only non-system apps or system apps that have a launch intent
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (isSystemApp && pm.getLaunchIntentForPackage(appInfo.packageName) == null) continue;

            AppInfo info = new AppInfo();
            info.name = appInfo.loadLabel(pm).toString();
            info.packageName = appInfo.packageName;
            info.icon = appInfo.loadIcon(pm);
            info.isIsolated = isolatedApps.contains(info.packageName);
            allApps.add(info);
        }

        Collections.sort(allApps, (a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    private void filter(String text) {
        List<AppInfo> filteredList = new ArrayList<>();
        for (AppInfo item : allApps) {
            if (item.name.toLowerCase().contains(text.toLowerCase()) ||
                item.packageName.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.updateList(filteredList);
    }

    private class AppInfo {
        String name;
        String packageName;
        Drawable icon;
        boolean isIsolated;
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        private List<AppInfo> apps;

        AppAdapter(List<AppInfo> apps) {
            this.apps = new ArrayList<>(apps);
        }

        void updateList(List<AppInfo> newList) {
            this.apps = new ArrayList<>(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.name.setText(app.name);
            holder.packageName.setText(app.packageName);
            holder.icon.setImageDrawable(app.icon);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(app.isIsolated);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.isIsolated = isChecked;
                if (isChecked) {
                    isolatedApps.add(app.packageName);
                } else {
                    isolatedApps.remove(app.packageName);
                }
                prefs.edit().putStringSet("isolated_apps", isolatedApps).apply();
            });

            holder.itemView.setOnClickListener(v -> holder.checkBox.performClick());
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, packageName;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.app_icon);
                name = itemView.findViewById(R.id.app_name);
                packageName = itemView.findViewById(R.id.app_package);
                checkBox = itemView.findViewById(R.id.app_checkbox);
            }
        }
    }
}
