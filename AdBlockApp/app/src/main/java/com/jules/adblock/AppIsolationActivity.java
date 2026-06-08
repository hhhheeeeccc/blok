package com.jules.adblock;

import android.content.Intent;
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
    private Set<String> isolatedAppsWifi;
    private Set<String> isolatedAppsMobile;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_isolation);

        prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE);
        isolatedAppsWifi = new HashSet<>(prefs.getStringSet("isolated_apps_wifi", new HashSet<>()));
        isolatedAppsMobile = new HashSet<>(prefs.getStringSet("isolated_apps_mobile", new HashSet<>()));

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
            info.isWifiIsolated = isolatedAppsWifi.contains(info.packageName);
            info.isMobileIsolated = isolatedAppsMobile.contains(info.packageName);
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

    private void triggerVpnUpdate() {
        Intent intent = new Intent(this, AdBlockVpnService.class);
        startService(intent);
    }

    private class AppInfo {
        String name;
        String packageName;
        Drawable icon;
        boolean isWifiIsolated;
        boolean isMobileIsolated;
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

            holder.wifiCheckBox.setOnCheckedChangeListener(null);
            holder.wifiCheckBox.setChecked(app.isWifiIsolated);

            holder.mobileCheckBox.setOnCheckedChangeListener(null);
            holder.mobileCheckBox.setChecked(app.isMobileIsolated);

            holder.wifiCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.isWifiIsolated = isChecked;
                if (isChecked) {
                    isolatedAppsWifi.add(app.packageName);
                } else {
                    isolatedAppsWifi.remove(app.packageName);
                }
                prefs.edit().putStringSet("isolated_apps_wifi", isolatedAppsWifi).apply();
                triggerVpnUpdate();
            });

            holder.mobileCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.isMobileIsolated = isChecked;
                if (isChecked) {
                    isolatedAppsMobile.add(app.packageName);
                } else {
                    isolatedAppsMobile.remove(app.packageName);
                }
                prefs.edit().putStringSet("isolated_apps_mobile", isolatedAppsMobile).apply();
                triggerVpnUpdate();
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, packageName;
            CheckBox wifiCheckBox, mobileCheckBox;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.app_icon);
                name = itemView.findViewById(R.id.app_name);
                packageName = itemView.findViewById(R.id.app_package);
                wifiCheckBox = itemView.findViewById(R.id.wifi_checkbox);
                mobileCheckBox = itemView.findViewById(R.id.mobile_checkbox);
            }
        }
    }
}
