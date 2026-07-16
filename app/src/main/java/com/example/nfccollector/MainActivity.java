package com.example.nfccollector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "nfc_records";
    private static final String KEY_RECORDS = "records";
    private static final int REQUEST_EXPORT = 1001;
    private static final long DUPLICATE_SCAN_GUARD_MS = 1500L;

    private final ArrayList<NfcRecord> records = new ArrayList<>();
    private final Set<String> currentCodes = new HashSet<>();
    private final SimpleDateFormat displayTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat fileTimeFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);

    private NfcAdapter nfcAdapter;
    private TextView statusText;
    private TextView countText;
    private LinearLayout tableBody;
    private Button exportButton;
    private Button clearButton;
    private String lastScanCode = "";
    private long lastScanTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        loadRecords();
        buildUi();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableNfcReading();
        updateNfcStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    private void enableNfcReading() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            return;
        }

        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_NFC_BARCODE;

        nfcAdapter.enableReaderMode(this, tag -> runOnUiThread(() -> handleTag(tag)), flags, null);
    }

    private void handleTag(Tag tag) {
        String code = bytesToHex(tag.getId());
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "未读取到 NFC 编码", Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        if (code.equals(lastScanCode) && now - lastScanTime < DUPLICATE_SCAN_GUARD_MS) {
            return;
        }
        lastScanCode = code;
        lastScanTime = now;

        if (currentCodes.contains(code)) {
            Toast.makeText(this, "当前清单中已存在该编码", Toast.LENGTH_SHORT).show();
            return;
        }

        NfcRecord record = new NfcRecord(records.size() + 1, code, displayTimeFormat.format(new Date(now)));
        records.add(record);
        currentCodes.add(code);
        saveRecords();
        refreshUi();
        Toast.makeText(this, "已记录第 " + record.index + " 个", Toast.LENGTH_SHORT).show();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(246, 248, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), getStatusBarHeight() + dp(18), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("NFC 编码采集");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(Color.rgb(18, 24, 38));
        title.setGravity(Gravity.START);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setTextColor(Color.rgb(67, 76, 94));
        statusText.setPadding(0, dp(8), 0, dp(8));
        statusText.setOnClickListener(v -> {
            if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            }
        });
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        countText = new TextView(this);
        countText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        countText.setTextColor(Color.rgb(26, 115, 232));
        countText.setTypeface(null, 1);
        countText.setPadding(0, dp(4), 0, dp(10));
        root.addView(countText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(buttonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        exportButton = createButton("导出 Excel", Color.rgb(26, 115, 232), Color.WHITE);
        exportButton.setOnClickListener(v -> exportRecords());
        buttonRow.addView(exportButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        SpaceView space = new SpaceView(this);
        buttonRow.addView(space, new LinearLayout.LayoutParams(dp(10), 1));

        clearButton = createButton("清零", Color.WHITE, Color.rgb(190, 38, 51));
        clearButton.setOnClickListener(v -> confirmClear());
        buttonRow.addView(clearButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        ScrollView verticalScroll = new ScrollView(this);
        verticalScroll.setFillViewport(true);
        verticalScroll.setVerticalScrollBarEnabled(true);
        verticalScroll.setScrollbarFadingEnabled(false);
        verticalScroll.setPadding(0, dp(14), 0, 0);
        root.addView(verticalScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        tableBody = new LinearLayout(this);
        tableBody.setOrientation(LinearLayout.VERTICAL);
        verticalScroll.addView(tableBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private Button createButton(String text, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTextColor(textColor);
        button.setBackgroundColor(backgroundColor);
        button.setAllCaps(false);
        return button;
    }

    private void refreshUi() {
        countText.setText("已采集：" + records.size() + " 个");
        exportButton.setEnabled(!records.isEmpty());
        clearButton.setEnabled(!records.isEmpty());
        exportButton.setAlpha(records.isEmpty() ? 0.45f : 1f);
        clearButton.setAlpha(records.isEmpty() ? 0.45f : 1f);

        tableBody.removeAllViews();
        tableBody.addView(createRow("序号", "NFC 编码", "扫描时间", true));

        if (records.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("请将手机靠近 NFC 贴片，应用会按扫描顺序自动记录。");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setTextColor(Color.rgb(89, 99, 116));
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            tableBody.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        for (NfcRecord record : records) {
            tableBody.addView(createRow(
                    String.valueOf(record.index),
                    record.code,
                    record.scannedAt,
                    false
            ));
        }
    }

    private LinearLayout createRow(String index, String code, String scannedAt, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(header ? Color.rgb(232, 238, 245) : Color.WHITE);

        row.addView(createCell(index, dp(54), 0f, header, Gravity.CENTER));
        row.addView(createCell(code, 0, 1f, header, Gravity.CENTER_VERTICAL));
        row.addView(createCell(scannedAt, dp(104), 0f, header, Gravity.CENTER_VERTICAL));
        return row;
    }

    private TextView createCell(String text, int width, float weight, boolean header, int gravity) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setSingleLine(false);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 15 : 14);
        cell.setTextColor(header ? Color.rgb(18, 24, 38) : Color.rgb(36, 44, 59));
        cell.setTypeface(null, header ? 1 : 0);
        cell.setGravity(gravity);
        cell.setPadding(dp(8), dp(10), dp(8), dp(10));
        cell.setBackgroundColor(header ? Color.rgb(232, 238, 245) : Color.WHITE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(0, 0, dp(1), dp(1));
        cell.setLayoutParams(params);
        return cell;
    }

    private void exportRecords() {
        if (records.isEmpty()) {
            Toast.makeText(this, "当前没有可导出的数据", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.ms-excel");
        intent.putExtra(Intent.EXTRA_TITLE, "NFC编码清单_" + fileTimeFormat.format(new Date()) + ".xls");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        try {
            writeExcelXml(uri);
            Toast.makeText(this, "导出成功，可按需清零后开始下一盒", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeExcelXml(Uri uri) throws IOException {
        OutputStream outputStream = getContentResolver().openOutputStream(uri);
        if (outputStream == null) {
            throw new IOException("无法打开导出文件");
        }

        try (Writer writer = new OutputStreamWriter(outputStream, "UTF-8")) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<?mso-application progid=\"Excel.Sheet\"?>\n");
            writer.write("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
            writer.write("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
            writer.write("<Worksheet ss:Name=\"NFC编码清单\"><Table>\n");
            writer.write("<Row><Cell><Data ss:Type=\"String\">序号</Data></Cell>");
            writer.write("<Cell><Data ss:Type=\"String\">NFC编码</Data></Cell>");
            writer.write("<Cell><Data ss:Type=\"String\">扫描时间</Data></Cell></Row>\n");

            for (NfcRecord record : records) {
                writer.write("<Row>");
                writer.write("<Cell><Data ss:Type=\"Number\">" + record.index + "</Data></Cell>");
                writer.write("<Cell><Data ss:Type=\"String\">" + xmlEscape(record.code) + "</Data></Cell>");
                writer.write("<Cell><Data ss:Type=\"String\">" + xmlEscape(record.scannedAt) + "</Data></Cell>");
                writer.write("</Row>\n");
            }

            writer.write("</Table></Worksheet></Workbook>");
        }
    }

    private void confirmClear() {
        if (records.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("清零当前清单？")
                .setMessage("清零后将删除当前已记录数据，下一次扫描会从序号 1 重新开始。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清零", (dialog, which) -> {
                    records.clear();
                    currentCodes.clear();
                    lastScanCode = "";
                    lastScanTime = 0L;
                    saveRecords();
                    refreshUi();
                    Toast.makeText(this, "已清零，可开始下一盒", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void updateNfcStatus() {
        if (nfcAdapter == null) {
            statusText.setText("此设备不支持 NFC");
        } else if (!nfcAdapter.isEnabled()) {
            statusText.setText("NFC 未开启，点击这里打开系统 NFC 设置");
        } else {
            statusText.setText("NFC 已就绪，请按顺序靠近贴片扫描");
        }
    }

    private void loadRecords() {
        records.clear();
        currentCodes.clear();

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = preferences.getString(KEY_RECORDS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                NfcRecord record = new NfcRecord(
                        object.optInt("index", i + 1),
                        object.optString("code", ""),
                        object.optString("scannedAt", "")
                );
                if (!TextUtils.isEmpty(record.code)) {
                    records.add(record);
                    currentCodes.add(record.code);
                }
            }
        } catch (JSONException ignored) {
            records.clear();
            currentCodes.clear();
        }
    }

    private void saveRecords() {
        JSONArray array = new JSONArray();
        for (NfcRecord record : records) {
            JSONObject object = new JSONObject();
            try {
                object.put("index", record.index);
                object.put("code", record.code);
                object.put("scannedAt", record.scannedAt);
                array.put(object);
            } catch (JSONException ignored) {
                // JSONObject only receives local primitive values here.
            }
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORDS, array.toString())
                .apply();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    private static class NfcRecord {
        final int index;
        final String code;
        final String scannedAt;

        NfcRecord(int index, String code, String scannedAt) {
            this.index = index;
            this.code = code;
            this.scannedAt = scannedAt;
        }
    }

    private static class SpaceView extends View {
        SpaceView(Activity activity) {
            super(activity);
        }
    }
}
