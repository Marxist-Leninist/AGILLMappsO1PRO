package com.opentransformers.apkextractor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_BASE = 101;
    private static final int REQ_SAVE_BUNDLE = 102;

    private PackageManager pm;
    private AppAdapter adapter;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();
    private EditText search;
    private CheckBox includeSystem;
    private TextView status;
    private ProgressBar progress;
    private AppEntry pendingApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        setContentView(buildUi());
        loadApps();
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, 0);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = new TextView(this);
        title.setText("APK Extractor");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Extract installed apps as APK files");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = dp(12);
        root.addView(subtitle, subLp);

        search = new EditText(this);
        search.setHint("Search apps or package names");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        includeSystem = new CheckBox(this);
        includeSystem.setText("Include system apps");
        includeSystem.setChecked(false);
        filters.addView(includeSystem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        filters.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(filters, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setText("Loading…");
        status.setTextSize(13);
        status.setTextColor(Color.DKGRAY);
        stateRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        stateRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        root.addView(stateRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new AppAdapter(this, visibleApps);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(list, listLp);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });
        includeSystem.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilter());
        refresh.setOnClickListener(v -> loadApps());
        list.setOnItemClickListener((parent, view, position, id) -> showActions(visibleApps.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            openAppDetails(visibleApps.get(position));
            return true;
        });

        return root;
    }

    private void loadApps() {
        progress.setVisibility(View.VISIBLE);
        status.setText("Scanning installed apps…");
        new Thread(() -> {
            List<ApplicationInfo> infos = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppEntry> loaded = new ArrayList<>();
            for (ApplicationInfo info : infos) {
                if (info.packageName.equals(getPackageName())) continue;
                try {
                    CharSequence labelCs = pm.getApplicationLabel(info);
                    String label = labelCs == null ? info.packageName : labelCs.toString();
                    boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    PackageInfo pi = pm.getPackageInfo(info.packageName, 0);
                    String version = pi.versionName == null ? "" : pi.versionName;
                    loaded.add(new AppEntry(label, info.packageName, version, info, system));
                } catch (Exception ignored) {
                }
            }
            Collections.sort(loaded, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(loaded);
                progress.setVisibility(View.GONE);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        if (adapter == null) return;
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean showSystem = includeSystem != null && includeSystem.isChecked();
        visibleApps.clear();
        for (AppEntry app : allApps) {
            if (!showSystem && app.system) continue;
            if (!q.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(q)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            visibleApps.add(app);
        }
        adapter.notifyDataSetChanged();
        status.setText(visibleApps.size() + " apps shown • " + allApps.size() + " detected");
    }

    private void showActions(AppEntry app) {
        int splitCount = app.info.splitSourceDirs == null ? 0 : app.info.splitSourceDirs.length;
        String details = app.packageName + (app.version.isEmpty() ? "" : "\nVersion " + app.version)
                + "\n" + (splitCount > 0 ? (splitCount + " split APKs detected") : "Single APK install");

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setMessage(details)
                .setItems(new String[]{
                        "Extract base APK",
                        "Export complete .APKS bundle",
                        "Open Android app info"
                }, (dialog, which) -> {
                    if (which == 0) chooseBaseDestination(app);
                    else if (which == 1) chooseBundleDestination(app);
                    else openAppDetails(app);
                })
                .show();
    }

    private void chooseBaseDestination(AppEntry app) {
        pendingApp = app;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_TITLE, safeName(app.label) + "-" + app.packageName + ".apk");
        startActivityForResult(intent, REQ_SAVE_BASE);
    }

    private void chooseBundleDestination(AppEntry app) {
        pendingApp = app;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, safeName(app.label) + "-" + app.packageName + ".apks");
        startActivityForResult(intent, REQ_SAVE_BUNDLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingApp == null) return;
        Uri uri = data.getData();
        AppEntry app = pendingApp;
        pendingApp = null;
        if (requestCode == REQ_SAVE_BASE) extractBase(app, uri);
        else if (requestCode == REQ_SAVE_BUNDLE) extractBundle(app, uri);
    }

    private void extractBase(AppEntry app, Uri uri) {
        setBusy("Extracting " + app.label + "…");
        new Thread(() -> {
            try (InputStream in = new BufferedInputStream(new FileInputStream(app.info.sourceDir));
                 OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(uri, "w"))) {
                if (out == null) throw new IllegalStateException("Could not open destination");
                copy(in, out);
                runOnUiThread(() -> finishBusy("Saved base APK for " + app.label));
            } catch (Exception e) {
                runOnUiThread(() -> failBusy(e));
            }
        }).start();
    }

    private void extractBundle(AppEntry app, Uri uri) {
        setBusy("Building .APKS bundle for " + app.label + "…");
        new Thread(() -> {
            try (OutputStream raw = new BufferedOutputStream(getContentResolver().openOutputStream(uri, "w"));
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                addFileToZip(zip, new File(app.info.sourceDir), "base.apk");
                if (app.info.splitSourceDirs != null) {
                    for (String splitPath : app.info.splitSourceDirs) {
                        File split = new File(splitPath);
                        addFileToZip(zip, split, split.getName());
                    }
                }
                zip.finish();
                runOnUiThread(() -> finishBusy("Saved complete .APKS bundle for " + app.label));
            } catch (Exception e) {
                runOnUiThread(() -> failBusy(e));
            }
        }).start();
    }

    private void addFileToZip(ZipOutputStream zip, File file, String entryName) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            copy(in, zip);
        }
        zip.closeEntry();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[128 * 1024];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        out.flush();
    }

    private void setBusy(String message) {
        progress.setVisibility(View.VISIBLE);
        status.setText(message);
    }

    private void finishBusy(String message) {
        progress.setVisibility(View.GONE);
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void failBusy(Exception e) {
        progress.setVisibility(View.GONE);
        status.setText("Extraction failed");
        new AlertDialog.Builder(this)
                .setTitle("Could not extract APK")
                .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void openAppDetails(AppEntry app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.packageName));
        startActivity(intent);
    }

    private static String safeName(String value) {
        String s = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
        return s.isEmpty() ? "app" : s;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String version;
        final ApplicationInfo info;
        final boolean system;

        AppEntry(String label, String packageName, String version, ApplicationInfo info, boolean system) {
            this.label = label;
            this.packageName = packageName;
            this.version = version;
            this.info = info;
            this.system = system;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppEntry> apps;

        AppAdapter(Context context, List<AppEntry> apps) {
            this.context = context;
            this.apps = apps;
        }

        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                LinearLayout line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(8), dp(8), dp(8), dp(8));
                line.setMinimumHeight(dp(72));

                ImageView icon = new ImageView(context);
                line.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout textCol = new LinearLayout(context);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setPadding(dp(14), 0, 0, 0);
                TextView name = new TextView(context);
                name.setTextSize(16);
                name.setTextColor(Color.rgb(20, 20, 20));
                name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                TextView pkg = new TextView(context);
                pkg.setTextSize(12);
                pkg.setTextColor(Color.DKGRAY);
                textCol.addView(name);
                textCol.addView(pkg);
                line.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView badge = new TextView(context);
                badge.setTextSize(11);
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(7), dp(4), dp(7), dp(4));
                line.addView(badge);

                row = new Row(icon, name, pkg, badge);
                line.setTag(row);
                convertView = line;
            } else {
                row = (Row) convertView.getTag();
            }

            AppEntry app = apps.get(position);
            row.name.setText(app.label);
            row.pkg.setText(app.packageName + (app.version.isEmpty() ? "" : " • " + app.version));
            try {
                Drawable icon = app.info.loadIcon(pm);
                row.icon.setImageDrawable(icon);
            } catch (Exception e) {
                row.icon.setImageDrawable(getDrawable(R.drawable.ic_launcher));
            }
            int splits = app.info.splitSourceDirs == null ? 0 : app.info.splitSourceDirs.length;
            row.badge.setText(splits > 0 ? (splits + " splits") : "APK");
            row.badge.setTextColor(Color.DKGRAY);
            return convertView;
        }
    }

    private static final class Row {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final TextView badge;

        Row(ImageView icon, TextView name, TextView pkg, TextView badge) {
            this.icon = icon;
            this.name = name;
            this.pkg = pkg;
            this.badge = badge;
        }
    }
}
