package com.ferzferaz.godstein;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GODstein Launcher - Tela Inicial Modular
 * Modo Esfera Holo 3D + Dock Limpo (sem barra de pesquisa fixa) + Gaveta com Auto-Launch.
 * Idealizado por @ferzferaz.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "godstein_prefs";
    private static final String KEY_AUTO_LAUNCH = "auto_launch_enabled";
    private static final String KEY_AUTO_KEYBOARD = "auto_keyboard_enabled";
    private static final String KEY_AUTO_SPIN = "auto_spin_enabled";
    private static final String KEY_SHOW_LABELS = "show_labels_enabled";
    private static final String KEY_SHOW_DOCK = "show_dock_enabled";
    private static final String KEY_DOCK_PACKAGES = "dock_packages";

    private SharedPreferences prefs;
    private float density;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private final List<String> dockPackages = new ArrayList<>();

    private FrameLayout rootLayout;
    private TextView clockView;
    private TextView dateView;
    private HoloSphereView holoSphereView;
    private LinearLayout dockContainer;
    private LinearLayout dockItemsRow;

    // Gaveta de aplicativos
    private LinearLayout drawerOverlay;
    private EditText searchInput;
    private TextView autoLaunchBadge;
    private GridView drawerGrid;
    private DrawerAdapter drawerAdapter;
    private boolean isDrawerOpen = false;
    private boolean suppressAutoLaunch = false;

    private boolean autoLaunchEnabled;
    private boolean autoKeyboardEnabled;
    private boolean autoSpinEnabled;
    private boolean showLabelsEnabled;
    private boolean showDockEnabled;

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mainHandler.postDelayed(this, 15000L);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInstalledApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;

        autoLaunchEnabled = prefs.getBoolean(KEY_AUTO_LAUNCH, BuildConfigPatches.ENABLE_AUTO_LAUNCH_SEARCH);
        autoKeyboardEnabled = prefs.getBoolean(KEY_AUTO_KEYBOARD, true);
        autoSpinEnabled = prefs.getBoolean(KEY_AUTO_SPIN, true);
        showLabelsEnabled = prefs.getBoolean(KEY_SHOW_LABELS, true);
        showDockEnabled = prefs.getBoolean(KEY_SHOW_DOCK, BuildConfigPatches.ENABLE_CLEAN_DOCK);

        setupEdgeToEdge();
        buildUI();
        loadDockPreferences();
        loadInstalledApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        mainHandler.removeCallbacks(clockTicker);
        mainHandler.postDelayed(clockTicker, 15000L);
        if (holoSphereView != null) {
            holoSphereView.invalidate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(clockTicker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ao pressionar botao Home estando com a gaveta aberta, fecha a gaveta suavemente
        if (isDrawerOpen) {
            closeDrawer();
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen) {
            closeDrawer();
        }
        // Launcher nao fecha ao apertar Voltar na tela inicial
    }

    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private void buildUI() {
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        // Leve vinheta escura para dar contraste a Esfera Holo sobre qualquer papel de parede
        rootLayout.setBackgroundColor(0x44000000);

        // Container principal da Home (Topo + Esfera + Dock)
        LinearLayout homeContainer = new LinearLayout(this);
        homeContainer.setOrientation(LinearLayout.VERTICAL);
        homeContainer.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        homeContainer.setPadding(dp(14), dp(42), dp(14), dp(28));

        // 1. TOPO: Relogio + Botao discreto GODstein (Creditos & Modulos)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(6), dp(4), dp(6), dp(4));

        LinearLayout clockColumn = new LinearLayout(this);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clockColumn.setLayoutParams(clockParams);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setShadowLayer(6f, 0f, 2f, 0xAA000000);

        dateView = new TextView(this);
        dateView.setTextColor(0xFFD0D7DE);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dateView.setShadowLayer(4f, 0f, 1f, 0xAA000000);

        clockColumn.addView(clockView);
        clockColumn.addView(dateView);

        TextView creditsBtn = new TextView(this);
        creditsBtn.setText("⚡ GODstein");
        creditsBtn.setTextColor(0xFF38BDF8);
        creditsBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        creditsBtn.setTypeface(Typeface.DEFAULT_BOLD);
        creditsBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x990D1117);
        btnBg.setCornerRadius(dp(18));
        btnBg.setStroke(dp(1), 0x6638BDF8);
        creditsBtn.setBackground(btnBg);
        creditsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreditsDialog();
            }
        });

        topBar.addView(clockColumn);
        topBar.addView(creditsBtn);
        homeContainer.addView(topBar);

        // 2. CENTRO: Esfera Holo 3D (ocupando todo espaco, ZERO barra de pesquisa)
        holoSphereView = new HoloSphereView(this);
        holoSphereView.setAutoSpin(autoSpinEnabled);
        holoSphereView.setShowLabels(showLabelsEnabled);
        LinearLayout.LayoutParams sphereParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        holoSphereView.setLayoutParams(sphereParams);
        holoSphereView.setListener(new HoloSphereView.Listener() {
            @Override
            public void onAppClicked(AppItem item) {
                launchApp(item);
            }

            @Override
            public void onAppLongClicked(AppItem item) {
                showAppOptionsDialog(item);
            }

            @Override
            public void onDoubleTapEmpty() {
                if (BuildConfigPatches.ENABLE_QUICK_GESTURES) {
                    showCreditsDialog();
                }
            }

            @Override
            public void onSwipeUpFromBottom() {
                if (BuildConfigPatches.ENABLE_QUICK_GESTURES) {
                    openDrawer();
                }
            }
        });
        homeContainer.addView(holoSphereView);

        // 3. BASE: Dock Limpo (apenas icones favoritos + botao Gaveta)
        dockContainer = new LinearLayout(this);
        dockContainer.setOrientation(LinearLayout.HORIZONTAL);
        dockContainer.setGravity(Gravity.CENTER);
        dockContainer.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(0xAA0D1117);
        dockBg.setCornerRadius(dp(28));
        dockBg.setStroke(dp(1), 0x44FFFFFF);
        dockContainer.setBackground(dockBg);

        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dockParams.topMargin = dp(6);
        dockContainer.setLayoutParams(dockParams);

        dockItemsRow = new LinearLayout(this);
        dockItemsRow.setOrientation(LinearLayout.HORIZONTAL);
        dockItemsRow.setGravity(Gravity.CENTER);
        dockItemsRow.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dockContainer.addView(dockItemsRow);

        // Gesto de deslizar para cima no Dock abre a Gaveta direto com teclado
        dockContainer.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startY = event.getY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (startY - event.getY() > dp(35)) {
                        openDrawer();
                        return true;
                    }
                }
                return false;
            }
        });

        homeContainer.addView(dockContainer);
        updateDockVisibility();

        rootLayout.addView(homeContainer);

        // 4. OVERLAY DA GAVETA DE APLICATIVOS (Com Busca + Teclado Automatico + Auto-Launch)
        buildDrawerOverlay();
        rootLayout.addView(drawerOverlay);

        setContentView(rootLayout);
        updateClock();
    }

    private void buildDrawerOverlay() {
        drawerOverlay = new LinearLayout(this);
        drawerOverlay.setOrientation(LinearLayout.VERTICAL);
        drawerOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        drawerOverlay.setBackgroundColor(0xEB0A0E17);
        drawerOverlay.setPadding(dp(16), dp(46), dp(16), dp(24));
        drawerOverlay.setVisibility(View.GONE);
        drawerOverlay.setClickable(true);
        drawerOverlay.setFocusable(true);

        // Barra superior da Gaveta (Campo de Busca + Botao Fechar)
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(14), dp(6), dp(10), dp(6));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0xFF161B22);
        searchBg.setCornerRadius(dp(24));
        searchBg.setStroke(dp(1), 0x6638BDF8);
        searchRow.setBackground(searchBg);

        searchInput = new EditText(this);
        searchInput.setHint("Digitar app para abrir...");
        searchInput.setHintTextColor(0xFF8B949E);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setFocusable(true);
        searchInput.setFocusableInTouchMode(true);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Forca abertura do teclado ao tocar no campo de pesquisa (Fix Android 16 / One UI)
        searchInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceShowKeyboard();
            }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE) && !filteredApps.isEmpty()) {
                    launchApp(filteredApps.get(0));
                    return true;
                }
                return false;
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Se esta apagando texto (after < count), nao aciona auto-launch ao voltar
                suppressAutoLaunch = (after < count);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterApps(s != null ? s.toString() : "", !suppressAutoLaunch);
            }
        });

        TextView clearBtn = new TextView(this);
        clearBtn.setText("✕");
        clearBtn.setTextColor(0xFF8B949E);
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        clearBtn.setPadding(dp(12), dp(6), dp(10), dp(6));
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchInput.getText().length() > 0) {
                    suppressAutoLaunch = true;
                    searchInput.setText("");
                } else {
                    closeDrawer();
                }
            }
        });

        searchRow.addView(searchInput);
        searchRow.addView(clearBtn);
        drawerOverlay.addView(searchRow);

        // Linha de status / Auto-launch badge
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(6), dp(8), dp(6), dp(8));

        autoLaunchBadge = new TextView(this);
        updateAutoLaunchBadge();
        autoLaunchBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        autoLaunchBadge.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoLaunchBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoLaunchEnabled = !autoLaunchEnabled;
                prefs.edit().putBoolean(KEY_AUTO_LAUNCH, autoLaunchEnabled).apply();
                updateAutoLaunchBadge();
            }
        });

        TextView closeDrawerBtn = new TextView(this);
        closeDrawerBtn.setText("Voltar à Esfera ↓");
        closeDrawerBtn.setTextColor(0xFF38BDF8);
        closeDrawerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeDrawerBtn.setPadding(dp(10), dp(4), dp(6), dp(4));
        closeDrawerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });

        statusRow.addView(autoLaunchBadge);
        statusRow.addView(closeDrawerBtn);
        drawerOverlay.addView(statusRow);

        // Grade de Apps na Gaveta
        drawerGrid = new GridView(this);
        drawerGrid.setNumColumns(4);
        drawerGrid.setVerticalSpacing(dp(14));
        drawerGrid.setHorizontalSpacing(dp(8));
        drawerGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        drawerGrid.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        drawerAdapter = new DrawerAdapter();
        drawerGrid.setAdapter(drawerAdapter);

        drawerGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < filteredApps.size()) {
                    launchApp(filteredApps.get(position));
                }
            }
        });

        drawerGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < filteredApps.size()) {
                    showAppOptionsDialog(filteredApps.get(position));
                    return true;
                }
                return false;
            }
        });

        drawerOverlay.addView(drawerGrid);
    }

    private void updateAutoLaunchBadge() {
        if (autoLaunchBadge == null) return;
        if (autoLaunchEnabled) {
            autoLaunchBadge.setText("⚡ Auto-Launch ATIVO (toque para alternar)");
            autoLaunchBadge.setTextColor(0xFF4ADE80);
        } else {
            autoLaunchBadge.setText("○ Auto-Launch desativado (toque para ativar)");
            autoLaunchBadge.setTextColor(0xFF8B949E);
        }
    }

    private void updateClock() {
        Date now = new Date();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, dd MMM", new Locale("pt", "BR"));
        if (clockView != null) clockView.setText(timeFmt.format(now));
        if (dateView != null) dateView.setText(dateFmt.format(now));
    }

    private void openDrawer() {
        isDrawerOpen = true;
        suppressAutoLaunch = true;
        searchInput.setText("");
        suppressAutoLaunch = false;
        filterApps("", false);

        drawerOverlay.setVisibility(View.VISIBLE);
        drawerOverlay.setAlpha(0f);
        drawerOverlay.animate().alpha(1f).setDuration(160).start();

        if (autoKeyboardEnabled) {
            forceShowKeyboard();
        }
    }

    private void closeDrawer() {
        isDrawerOpen = false;
        hideKeyboard();
        drawerOverlay.setVisibility(View.GONE);
    }

    private void forceShowKeyboard() {
        if (searchInput == null) return;
        searchInput.requestFocus();
        searchInput.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isDrawerOpen) return;
                searchInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 90L);
    }

    private void hideKeyboard() {
        if (searchInput == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }

    private void filterApps(String rawQuery, boolean allowAutoLaunch) {
        String q = AppItem.normalize(rawQuery);
        filteredApps.clear();
        if (q.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            // Prioridade 1: apps que comecam com o texto digitado
            List<AppItem> containsMatches = new ArrayList<>();
            for (AppItem item : allApps) {
                if (item.normalizedLabel.startsWith(q)) {
                    filteredApps.add(item);
                } else if (item.normalizedLabel.contains(q)) {
                    containsMatches.add(item);
                }
            }
            filteredApps.addAll(containsMatches);
        }

        if (drawerAdapter != null) {
            drawerAdapter.notifyDataSetChanged();
        }

        // Motor Auto-Launch: se restou exatamente 1 aplicativo correspondente enquanto digita, abre direto!
        if (allowAutoLaunch && autoLaunchEnabled && !q.isEmpty() && filteredApps.size() == 1) {
            final AppItem match = filteredApps.get(0);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isDrawerOpen) {
                        launchApp(match);
                    }
                }
            });
        }
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
        allApps.clear();

        Set<String> seen = new HashSet<>();
        int iconPx = dp(52);

        for (ResolveInfo ri : resolved) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String act = ri.activityInfo.name;
            if (pkg.equals(getPackageName())) continue; // evita listar o proprio launcher na esfera

            String key = pkg + "/" + act;
            if (seen.contains(key)) continue;
            seen.add(key);

            CharSequence cs = ri.loadLabel(pm);
            String label = cs != null ? cs.toString() : pkg;
            Drawable drawable = ri.loadIcon(pm);
            Bitmap bmp = drawableToBitmap(drawable, iconPx);

            allApps.add(new AppItem(label, pkg, act, drawable, bmp));
        }

        Collections.sort(allApps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return a.normalizedLabel.compareTo(b.normalizedLabel);
            }
        });

        if (holoSphereView != null) {
            holoSphereView.setApps(allApps);
        }
        rebuildDock();
        filterApps(searchInput != null && isDrawerOpen ? searchInput.getText().toString() : "", false);
    }

    private Bitmap drawableToBitmap(Drawable drawable, int sizePx) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) drawable).getBitmap();
            if (b != null) {
                return Bitmap.createScaledBitmap(b, sizePx, sizePx, true);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, sizePx, sizePx);
        drawable.draw(canvas);
        return bitmap;
    }

    private void loadDockPreferences() {
        dockPackages.clear();
        String saved = prefs.getString(KEY_DOCK_PACKAGES, "");
        if (!saved.isEmpty()) {
            for (String p : saved.split(",")) {
                if (!p.trim().isEmpty()) {
                    dockPackages.add(p.trim());
                }
            }
        }
    }

    private void saveDockPreferences() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dockPackages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(dockPackages.get(i));
        }
        prefs.edit().putString(KEY_DOCK_PACKAGES, sb.toString()).apply();
    }

    private void rebuildDock() {
        if (dockItemsRow == null) return;
        dockItemsRow.removeAllViews();

        // Se o usuario nao personalizou o dock ainda, auto-preenche com até 4 apps populares instalados
        List<AppItem> dockItems = new ArrayList<>();
        if (dockPackages.isEmpty() && !allApps.isEmpty()) {
            List<String> preferredKeywords = Arrays.asList("dialer", "phone", "telefone", "contacts", "messaging", "whatsapp", "chrome", "browser", "camera");
            for (String kw : preferredKeywords) {
                for (AppItem item : allApps) {
                    if (dockItems.size() >= 4) break;
                    if (!dockItems.contains(item) && (item.packageName.toLowerCase(Locale.ROOT).contains(kw)
                            || item.normalizedLabel.contains(kw))) {
                        dockItems.add(item);
                        dockPackages.add(item.packageName);
                    }
                }
                if (dockItems.size() >= 4) break;
            }
            for (AppItem item : allApps) {
                if (dockItems.size() >= 4) break;
                if (!dockItems.contains(item)) {
                    dockItems.add(item);
                    dockPackages.add(item.packageName);
                }
            }
            saveDockPreferences();
        } else {
            for (String pkg : dockPackages) {
                for (AppItem item : allApps) {
                    if (item.packageName.equals(pkg)) {
                        dockItems.add(item);
                        break;
                    }
                }
            }
        }

        // Renderiza apps do dock
        for (final AppItem item : dockItems) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(item.iconDrawable);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(10), 0, dp(10), 0);
            icon.setLayoutParams(lp);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchApp(item);
                }
            });
            icon.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppOptionsDialog(item);
                    return true;
                }
            });
            dockItemsRow.addView(icon);
        }

        // Botao dedicado para abrir a Gaveta de Aplicativos (com busca auto-launch)
        TextView drawerBtn = new TextView(this);
        drawerBtn.setText("⋮⋮⋮");
        drawerBtn.setTextColor(0xFF38BDF8);
        drawerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        drawerBtn.setTypeface(Typeface.DEFAULT_BOLD);
        drawerBtn.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x3338BDF8);
        circle.setStroke(dp(1), 0x8838BDF8);
        drawerBtn.setBackground(circle);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        btnLp.setMargins(dp(10), 0, dp(10), 0);
        drawerBtn.setLayoutParams(btnLp);
        drawerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        dockItemsRow.addView(drawerBtn);
    }

    private void updateDockVisibility() {
        if (dockContainer != null) {
            dockContainer.setVisibility(showDockEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void launchApp(AppItem item) {
        if (item == null) return;
        try {
            if (isDrawerOpen) {
                closeDrawer();
            }
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(item.packageName, item.activityName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir " + item.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppOptionsDialog(final AppItem item) {
        final boolean isInDock = dockPackages.contains(item.packageName);
        String dockOption = isInDock ? "Remover do Dock" : "Fixar no Dock";
        String[] options = new String[]{"Abrir aplicativo", dockOption, "Informações do App"};

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(item.label)
            .setItems(options, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    if (which == 0) {
                        launchApp(item);
                    } else if (which == 1) {
                        if (isInDock) {
                            dockPackages.remove(item.packageName);
                        } else {
                            if (dockPackages.size() >= 5) {
                                Toast.makeText(MainActivity.this, "Dock cheio (máx. 5 atalhos).", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            dockPackages.add(item.packageName);
                        }
                        saveDockPreferences();
                        rebuildDock();
                    } else if (which == 2) {
                        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        info.setData(Uri.parse("package:" + item.packageName));
                        startActivity(info);
                    }
                }
            })
            .show();
    }

    private void showCreditsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(14), dp(20), dp(16));
        scroll.addView(container);

        // Secao Criador / Idealizador
        TextView creatorTitle = new TextView(this);
        creatorTitle.setText("CRIADOR & IDEALIZAÇÃO");
        creatorTitle.setTextColor(0xFF38BDF8);
        creatorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        creatorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(creatorTitle);

        LinearLayout creatorCard = new LinearLayout(this);
        creatorCard.setOrientation(LinearLayout.HORIZONTAL);
        creatorCard.setGravity(Gravity.CENTER_VERTICAL);
        creatorCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF161B22);
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), 0x4438BDF8);
        creatorCard.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(6);
        cardLp.bottomMargin = dp(16);
        creatorCard.setLayoutParams(cardLp);

        TextView creatorName = new TextView(this);
        creatorName.setText("@" + BuildConfigPatches.CURATOR_USER + "\nIdealizador do GODstein");
        creatorName.setTextColor(Color.WHITE);
        creatorName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        creatorName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button creatorGhBtn = new Button(this);
        creatorGhBtn.setText("GitHub ↗");
        creatorGhBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        creatorGhBtn.setAllCaps(false);
        creatorGhBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(BuildConfigPatches.CURATOR_GITHUB);
            }
        });

        creatorCard.addView(creatorName);
        creatorCard.addView(creatorGhBtn);
        container.addView(creatorCard);

        // Secao Configuracoes Rapidas em Tempo Real
        TextView cfgTitle = new TextView(this);
        cfgTitle.setText("OPÇÕES MODULARES EM TEMPO REAL");
        cfgTitle.setTextColor(0xFF38BDF8);
        cfgTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        cfgTitle.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(cfgTitle);

        addToggleOption(container, "Auto-Launch na busca (abre app único ao digitar)", autoLaunchEnabled, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoLaunchEnabled = isChecked;
                prefs.edit().putBoolean(KEY_AUTO_LAUNCH, isChecked).apply();
                updateAutoLaunchBadge();
            }
        });

        addToggleOption(container, "Abrir teclado automaticamente na gaveta", autoKeyboardEnabled, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoKeyboardEnabled = isChecked;
                prefs.edit().putBoolean(KEY_AUTO_KEYBOARD, isChecked).apply();
            }
        });

        addToggleOption(container, "Giro automático suave na Esfera Holo", autoSpinEnabled, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoSpinEnabled = isChecked;
                prefs.edit().putBoolean(KEY_AUTO_SPIN, isChecked).apply();
                if (holoSphereView != null) holoSphereView.setAutoSpin(isChecked);
            }
        });

        addToggleOption(container, "Mostrar nomes dos apps na Esfera", showLabelsEnabled, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showLabelsEnabled = isChecked;
                prefs.edit().putBoolean(KEY_SHOW_LABELS, isChecked).apply();
                if (holoSphereView != null) holoSphereView.setShowLabels(isChecked);
            }
        });

        addToggleOption(container, "Mostrar Dock inferior (sem barra de pesquisa)", showDockEnabled, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showDockEnabled = isChecked;
                prefs.edit().putBoolean(KEY_SHOW_DOCK, isChecked).apply();
                updateDockVisibility();
            }
        });

        // Secao Creditos dos Projetos Open-Source Originais
        TextView creditsTitle = new TextView(this);
        creditsTitle.setText("CRÉDITOS & PROJETOS OPEN-SOURCE ORIGINAIS");
        creditsTitle.setTextColor(0xFF38BDF8);
        creditsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        creditsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams cTitleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cTitleLp.topMargin = dp(16);
        cTitleLp.bottomMargin = dp(6);
        creditsTitle.setLayoutParams(cTitleLp);
        container.addView(creditsTitle);

        TextView creditsSubtitle = new TextView(this);
        creditsSubtitle.setText("Funcionalidades e inspirações reunidas nesta build. Clique em GitHub para acessar o código-fonte original dos autores:");
        creditsSubtitle.setTextColor(0xFF8B949E);
        creditsSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        creditsSubtitle.setPadding(0, 0, 0, dp(10));
        container.addView(creditsSubtitle);

        for (final BuildConfigPatches.CreditEntry entry : BuildConfigPatches.getActiveCredits()) {
            LinearLayout itemCard = new LinearLayout(this);
            itemCard.setOrientation(LinearLayout.VERTICAL);
            itemCard.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF161B22);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), 0x33FFFFFF);
            itemCard.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            itemCard.setLayoutParams(lp);

            TextView title = new TextView(this);
            title.setText(entry.originApp + " — " + entry.moduleName);
            title.setTextColor(Color.WHITE);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

            TextView meta = new TextView(this);
            meta.setText("Autor: " + entry.author + "\n" + entry.description);
            meta.setTextColor(0xFFB1BAC4);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            meta.setPadding(0, dp(3), 0, dp(6));

            TextView link = new TextView(this);
            link.setText("🔗 Abrir no GitHub: " + entry.githubUrl);
            link.setTextColor(0xFF58A6FF);
            link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            link.setTypeface(Typeface.DEFAULT_BOLD);
            link.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUrl(entry.githubUrl);
                }
            });

            itemCard.addView(title);
            itemCard.addView(meta);
            itemCard.addView(link);
            container.addView(itemCard);
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("GODstein Launcher • Acervo & Créditos")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show();
    }

    private void addToggleOption(LinearLayout parent, String text, boolean initial, CompoundButton.OnCheckedChangeListener listener) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.WHITE);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener(listener);
        cb.setPadding(dp(4), dp(6), dp(4), dp(6));
        parent.addView(cb);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum navegador encontrado para abrir: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    private class DrawerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return filteredApps.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView iconView;
            TextView labelView;

            if (convertView == null) {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(4), dp(6), dp(4), dp(6));

                iconView = new ImageView(MainActivity.this);
                iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
                iconView.setId(android.R.id.icon);

                labelView = new TextView(MainActivity.this);
                labelView.setId(android.R.id.text1);
                labelView.setTextColor(Color.WHITE);
                labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                labelView.setGravity(Gravity.CENTER);
                labelView.setMaxLines(2);
                labelView.setPadding(0, dp(4), 0, 0);

                cell.addView(iconView);
                cell.addView(labelView);
            } else {
                cell = (LinearLayout) convertView;
                iconView = cell.findViewById(android.R.id.icon);
                labelView = cell.findViewById(android.R.id.text1);
            }

            AppItem item = filteredApps.get(position);
            iconView.setImageDrawable(item.iconDrawable);
            labelView.setText(item.label);
            return cell;
        }
    }
}
