package com.dan74tech.smartscan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.dan74tech.smartscan.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SMART SCAN REGISTRY - CORE DASHBOARD
 * Main activity handling attendance oversight, data synchronization,
 * and reporting logic for teacher sessions.
 * Version: 2.2
 */
public class DashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // --- API & NETWORK CONFIGURATION ---
    private static final String BASE_URL = "https://dantech.onrender.com/api/index.php";
    private static final int SYNC_TIMEOUT_MS = 90000;
    private static final int MAX_RETRIES = 0;

    // --- DATA FIELDS ---
    private String selectedClass, selectedLesson, periodType, teacherName, schoolName, teacherDepartment, teacherPhone;
    private int teacherId;
    private DBHelper dbHelper;
    private RequestQueue requestQueue;

    // --- UI COMPONENTS ---
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private TextView tvSchoolHeader, tvSessionInfo, tvDailyCount, tvWeeklyCount, tvSyncSummary;
    private Button btnResetApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Theme Logic must come before super.onCreate
        SharedPreferences themePrefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String theme = themePrefs.getString("theme", "light");
        if ("dark".equals(theme)) {
            setTheme(R.style.Theme_Dark);
        } else {
            setTheme(R.style.Theme_Light);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // 2. Initialize Core Utilities
        dbHelper = new DBHelper(this);
        requestQueue = Volley.newRequestQueue(this);

        // 3. Data Recovery & Session Setup
        extractSessionData();

        // 4. UI Binding & Setup
        bindViews();

        // 5. Initial Dashboard State
        refreshDashboardUI();
        checkNetworkAndWarmServer();
    }

    private void extractSessionData() {
        teacherId = getIntent().getIntExtra("teacher_id", -1);
        teacherName = getIntent().getStringExtra("teacher_name");
        schoolName = getIntent().getStringExtra("school_name");
        teacherDepartment = getIntent().getStringExtra("teacher_department");
        selectedClass = getIntent().getStringExtra("selected_class");
        selectedLesson = getIntent().getStringExtra("selected_lesson");
        periodType = getIntent().getStringExtra("period_type");
        teacherPhone = getIntent().getStringExtra("teacher_phone");

        SharedPreferences prefs = getSharedPreferences("SmartScanPrefs", MODE_PRIVATE);
        if (teacherId == -1) {
            teacherId = prefs.getInt("teacher_id", -1);
            teacherName = prefs.getString("teacher_name", "Teacher");
            schoolName = prefs.getString("school_name", "Smart Scan");
            teacherDepartment = prefs.getString("teacher_department", "Department");
            teacherPhone = prefs.getString("teacher_phone", "");
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("teacher_id", teacherId);
            editor.putString("teacher_name", teacherName);
            editor.putString("school_name", schoolName);
            editor.putString("teacher_department", teacherDepartment);
            editor.putString("teacher_phone", teacherPhone);
            editor.apply();
        }
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        toolbar = findViewById(R.id.toolbar);
        tvSchoolHeader = findViewById(R.id.tvSchoolNameHeader);
        tvSessionInfo = findViewById(R.id.tvSelectedClassLesson);
        tvDailyCount = findViewById(R.id.tvDailyCount);
        tvWeeklyCount = findViewById(R.id.tvWeeklyCount);
        tvSyncSummary = findViewById(R.id.tvSyncSummary);
        btnResetApp = findViewById(R.id.btnResetApp);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.openDrawer, R.string.closeDrawer);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        setupHeaderClickListener();

        tvSchoolHeader.setText(dbHelper.toTitleCase(schoolName));
        String sessionDisplay = String.format("%s | %s | %s",
                selectedClass != null ? selectedClass : "No Class",
                selectedLesson != null ? selectedLesson : "No Lesson",
                periodType != null ? periodType : "Normal");
        tvSessionInfo.setText(sessionDisplay);

        findViewById(R.id.btnScanAttendance).setOnClickListener(v -> {
            Intent i = new Intent(this, QRScanActivity.class);
            passData(i);
            startActivity(i);
        });

        findViewById(R.id.btnSyncDashboard).setOnClickListener(v -> startTwoWaySync());

        if (btnResetApp != null) {
            btnResetApp.setOnClickListener(v -> showResetConfirmation());
        }

        setupCardListeners();
    }

    private void setupHeaderClickListener() {
        View headerView = navigationView.getHeaderView(0);
        LinearLayout headerLayout = headerView.findViewById(R.id.header_layout);

        if (headerLayout != null) {
            headerLayout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                String message = "I am using your Smart scan app and I like it. I would like to .....";
                String phoneNumber = "254790435584";
                String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + Uri.encode(message);

                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupCardListeners() {
        findViewById(R.id.daily_attendance_card).setOnClickListener(v -> {
            Intent i = new Intent(this, AttendanceActivity.class);
            passData(i);
            startActivity(i);
        });

        findViewById(R.id.weekly_report_card).setOnClickListener(v -> {
            Intent i = new Intent(this, AnalyticsActivity.class);
            passData(i);
            startActivity(i);
        });

        findViewById(R.id.master_student_card).setOnClickListener(v -> {
            Intent i = new Intent(this, StudentRegistrationActivity.class);
            passData(i);
            startActivity(i);
        });

        findViewById(R.id.individual_report_card).setOnClickListener(v -> {
            Intent i = new Intent(this, SelectStudentActivity.class);
            passData(i);
            startActivity(i);
        });

        findViewById(R.id.change_session_card).setOnClickListener(v -> {
            Intent i = new Intent(this, SelectClassActivity.class);
            passData(i);
            startActivity(i);
            finish();
        });

        findViewById(R.id.whatsapp_card).setOnClickListener(v -> generateWhatsAppReport());
    }

    private void startTwoWaySync() {
        // 1. Validation Toast
        if (schoolName == null || schoolName.trim().isEmpty() || schoolName.equals("Smart Scan")) {
            Toast.makeText(this, "⚠️ Sync Denied: School name is missing or default", Toast.LENGTH_LONG).show();
            return;
        }

        JSONArray unsyncedAttendance = dbHelper.getUnsyncedAttendance();
        JSONArray unsyncedStudents = dbHelper.getUnsyncedStudents();

        // 2. Monitoring the decision logic
        if (unsyncedAttendance.length() > 0 || unsyncedStudents.length() > 0) {
            // Inform the user that local changes were found and need to be pushed first
            Toast.makeText(this, "Found " + (unsyncedAttendance.length() + unsyncedStudents.length())
                    + " unsynced items. Starting upload...", Toast.LENGTH_SHORT).show();
            uploadAllData();
        } else {
            // Inform the user that local data is clean, so we are just refreshing from cloud
            Toast.makeText(this, "Local data up-to-date. Refreshing from cloud...", Toast.LENGTH_SHORT).show();
            fetchMasterData();
        }
    }

    private void uploadAllData() {
        String url = BASE_URL + "?type=upload_all&school_name=" + Uri.encode(schoolName);

        try {
            JSONArray attendance = dbHelper.getUnsyncedAttendance();
            JSONArray students = dbHelper.getUnsyncedStudents();

            // 1. Initiation Toast - Monitor what is about to be sent
            Toast.makeText(this, "Uploading: " + attendance.length() + " records & "
                    + students.length() + " new students...", Toast.LENGTH_SHORT).show();

            JSONObject postParams = new JSONObject();
            postParams.put("school_name", schoolName);
            postParams.put("teacher_id", teacherId);
            postParams.put("department", teacherDepartment);
            postParams.put("attendance_data", attendance);
            postParams.put("students", students);

            JsonObjectRequest uploadRequest = new JsonObjectRequest(Request.Method.POST, url, postParams,
                    response -> {
                        String status = response.optString("status");
                        if (status.equals("success")) {
                            dbHelper.markAsSynced();

                            // 2. Success Toast - Confirm cloud acknowledgment
                            Toast.makeText(this, "Cloud Sync Complete ✓", Toast.LENGTH_SHORT).show();

                            fetchMasterData();
                        } else {
                            // 3. Server-side Error Toast
                            String msg = response.optString("message", "Unknown Server Error");
                            Toast.makeText(this, "Sync Denied: " + msg, Toast.LENGTH_LONG).show();
                        }
                    },
                    error -> {
                        // 4. Network/Timeout Error Toast
                        String errorMsg = "Network Error: Check connection or Server status";
                        if (error.networkResponse == null) errorMsg = "Server Timeout (Cold Start)";

                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        Log.e("SYNC_ERROR", error.toString());
                    });

            uploadRequest.setRetryPolicy(new DefaultRetryPolicy(SYNC_TIMEOUT_MS, MAX_RETRIES, 1f));
            requestQueue.add(uploadRequest);

        } catch (Exception e) {
            Toast.makeText(this, "Critical Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("SYNC_EXCEPTION", e.getMessage());
        }
    }

    private void fetchMasterData() {
        String url = BASE_URL + "?type=fetch_master_data&school_name=" + Uri.encode(schoolName);
        JsonObjectRequest fetchRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    dbHelper.updateMasterTables(response, teacherId);
                    refreshStats();
                },
                error -> Log.e("SYNC_ERROR", "Error fetching"));

        fetchRequest.setRetryPolicy(new DefaultRetryPolicy(60000, 0, 1f));
        requestQueue.add(fetchRequest);
    }

    private void generateWhatsAppReport() {
        String todayDisplay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        String dbDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        int dailyCount = dbHelper.getDailyCount(selectedClass, selectedLesson, dbDate);
        int weeklyCount = dbHelper.getWeeklyTotalForClass(selectedClass);

        StringBuilder msg = new StringBuilder();
        msg.append("*🚨 SMART SCAN MASTER REPORT 🚨*\n\n");
        msg.append("🏫 *School:* ").append(schoolName).append("\n");
        msg.append("👨‍🏫 *Teacher:* ").append(teacherName).append("\n");
        msg.append("📋 *Class:* ").append(selectedClass).append("\n");
        msg.append("📖 *Unit:* ").append(selectedLesson).append("\n");
        msg.append("📅 *Date:* ").append(todayDisplay).append("\n\n");
        msg.append("Today's Attendance: ").append(dailyCount).append("\n");

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, msg.toString());
        intent.setPackage("com.whatsapp");
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(Intent.createChooser(intent, "Share Report via"));
        }
    }

    public void refreshStats() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int dailyCount = dbHelper.getPresentCount(selectedClass, selectedLesson, today);
        int weeklyCount = dbHelper.getWeeklyTotalForClass(selectedClass);

        if (tvDailyCount != null) tvDailyCount.setText(String.valueOf(dailyCount));
        if (tvWeeklyCount != null) tvWeeklyCount.setText(String.valueOf(weeklyCount));
    }

    private void refreshDashboardUI() {
        refreshStats();
    }

    private void checkNetworkAndWarmServer() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            fetchMasterData();
        }
    }

    private void passData(Intent intent) {
        intent.putExtra("teacher_id", teacherId);
        intent.putExtra("teacher_name", teacherName);
        intent.putExtra("school_name", schoolName);
        intent.putExtra("teacher_department", teacherDepartment);
        intent.putExtra("selected_class", selectedClass);
        intent.putExtra("selected_lesson", selectedLesson);
        intent.putExtra("period_type", periodType);
        intent.putExtra("teacher_phone", teacherPhone);
    }

    private void showResetConfirmation() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_confirm, null);
        TextView etEmail = dialogView.findViewById(R.id.etResetEmail);
        TextView etPassword = dialogView.findViewById(R.id.etResetPassword);

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Factory Reset")
                .setView(dialogView)
                .setPositiveButton("Reset", (dialog, which) -> {
                    if (dbHelper.checkTeacherLogin(etEmail.getText().toString(), etPassword.getText().toString())) {
                        dbHelper.nukeLocalDatabase();
                        startActivity(new Intent(this, SignUpActivity.class));
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (id == R.id.nav_register_student) {
            Intent i = new Intent(this, StudentRegistrationActivity.class);
            passData(i);
            startActivity(i);
        } else if (id == R.id.nav_analytics) {
            Intent i = new Intent(this, AnalyticsActivity.class);
            passData(i);
            startActivity(i);
        } else if (id == R.id.nav_settings) {
            // Toggle Theme Example
            SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
            String current = prefs.getString("theme", "light");
            prefs.edit().putString("theme", current.equals("light") ? "dark" : "light").apply();
            recreate();
        } else if (id == R.id.nav_about) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("About Smart Scan")
                    .setMessage(getString(R.string.app_about_text))
                    .setPositiveButton("OK", null)
                    .setIcon(R.drawable.ic_info) // Optional: Add an info icon if you have one
                    .show();
        } else if (id == R.id.nav_logout) {
            new AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        getSharedPreferences("SmartScanPrefs", MODE_PRIVATE).edit().clear().apply();
                        Intent i = new Intent(this, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    })
                    .setNegativeButton("No", null)
                    .show();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }
}