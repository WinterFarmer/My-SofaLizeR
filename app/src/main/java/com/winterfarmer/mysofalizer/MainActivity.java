package com.winterfarmer.mysofalizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int REQ_PICK_VIDEO = 101;
    private static final int REQ_PICK_SOFA = 102;
    private static final int REQ_PICK_EQ = 103;
    private static final int REQ_PICK_DIR = 104;

    private TextView tvVideoName, tvVideoInfo, tvSofaName, tvSofaFile, tvSofaSub;
    private TextView tvEqGuide, tvEqProfileName, tvEqFileName, tvOutputDir;
    private View boxEqFile;
    private Switch switchEq;
    private LinearLayout optOutputVideo, optOutputAudio;
    private boolean isOutputVideo = true;

    private String selectedVideoPath = "";
    private String selectedSofaPath = "";
    private String selectedEqPath = "";
    private String outputDirPath = "/storage/emulated/0/Music/";
    private long totalDurationMs = 0;

    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView tvProgressPercent, tvProgressStatus;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Process ffmpegProcess;
    private boolean isRendering = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkStoragePermission();
        initViews();
        setupEvents();
        setupHyperlinks();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(fallback);
                }
            }
        }
    }

    private void initViews() {
        tvVideoName = findViewById(R.id.tvVideoName);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);
        tvSofaName = findViewById(R.id.tvSofaName);
        tvSofaFile = findViewById(R.id.tvSofaFile);
        tvSofaSub = findViewById(R.id.tvSofaSub);

        switchEq = findViewById(R.id.switchEq);
        tvEqGuide = findViewById(R.id.tvEqGuide);
        boxEqFile = findViewById(R.id.boxEqFile);
        tvEqProfileName = findViewById(R.id.tvEqProfileName);
        tvEqFileName = findViewById(R.id.tvEqFileName);

        optOutputVideo = findViewById(R.id.optOutputVideo);
        optOutputAudio = findViewById(R.id.optOutputAudio);
        tvOutputDir = findViewById(R.id.tvOutputDir);
        tvOutputDir.setText(outputDirPath);
    }

    private void setupHyperlinks() {
        if (tvSofaSub != null) {
            String sofaText = "Dapatkan profile HRTF di <a href='https://www.sofaconventions.org'><font color='#00E5FF'><u>www.sofaconventions.org</u></font></a>";
            tvSofaSub.setText(Html.fromHtml(sofaText, Html.FROM_HTML_MODE_LEGACY));
            tvSofaSub.setMovementMethod(LinkMovementMethod.getInstance());
        }
        if (tvEqGuide != null) {
            String eqText = "Custom EQ berfungsi untuk menetralkan tuning bawaan dari headphone/IEM anda. Download konfigurasi EQ dari <a href='https://autoeq.app'><font color='#FFD166'><u>autoeq.app</u></font></a>";
            tvEqGuide.setText(Html.fromHtml(eqText, Html.FROM_HTML_MODE_LEGACY));
            tvEqGuide.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private String parseAutoEqFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        File file = new File(filePath);
        if (!file.exists()) return "";

        StringBuilder filters = new StringBuilder();
        Pattern pkPattern = Pattern.compile("(?i)(PK|LSC|HSC|LS|HS)\\s+Fc\\s+([\\d\\.]+)\\s*Hz\\s+Gain\\s+([\\-\\+\\d\\.]+)\\s*dB\\s+Q\\s+([\\d\\.]+)");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // 1. Parsing Preamp (-X dB)
                if (line.toLowerCase().startsWith("preamp:")) {
                    String gain = line.substring(7).replace("dB", "").replace("db", "").trim();
                    if (!gain.isEmpty()) {
                        if (filters.length() > 0) filters.append(",");
                        filters.append("volume=").append(gain).append("dB");
                    }
                }
                // 2. Parsing Filter Parametrik
                else if (line.toLowerCase().startsWith("filter")) {
                    if (line.toUpperCase().contains("OFF")) continue;
                    Matcher m = pkPattern.matcher(line);
                    if (m.find()) {
                        String type = m.group(1).toUpperCase();
                        String fc = m.group(2);
                        String gain = m.group(3);
                        String q = m.group(4);

                        if (filters.length() > 0) filters.append(",");
                        if (type.equals("PK")) {
                            filters.append("equalizer=f=").append(fc).append(":width_type=q:w=").append(q).append(":g=").append(gain);
                        } else if (type.equals("LSC") || type.equals("LS")) {
                            filters.append("lowshelf=f=").append(fc).append(":width_type=q:w=").append(q).append(":g=").append(gain);
                        } else if (type.equals("HSC") || type.equals("HS")) {
                            filters.append("highshelf=f=").append(fc).append(":width_type=q:w=").append(q).append(":g=").append(gain);
                        }
                    }
                }
                // 3. Parsing GraphicEQ
                else if (line.startsWith("GraphicEQ:")) {
                    String data = line.substring(10).trim();
                    String[] points = data.split(";");
                    StringBuilder geq = new StringBuilder("firequalizer=gain_entry='");
                    boolean first = true;
                    for (String pt : points) {
                        String[] kv = pt.trim().split("\\s+");
                        if (kv.length >= 2) {
                            if (!first) geq.append(";");
                            geq.append("entry(").append(kv[0]).append(",").append(kv[1]).append(")");
                            first = false;
                        }
                    }
                    geq.append("'");
                    if (!first) {
                        if (filters.length() > 0) filters.append(",");
                        filters.append(geq);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filters.toString();
    }
    private void setupEvents() {
        switchEq.setOnCheckedChangeListener((btn, isChecked) -> {
            tvEqGuide.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            boxEqFile.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        optOutputVideo.setOnClickListener(v -> {
            isOutputVideo = true;
            optOutputVideo.setBackgroundResource(R.drawable.bg_output_active);
            optOutputAudio.setBackgroundResource(R.drawable.bg_output_inactive);
        });

        optOutputAudio.setOnClickListener(v -> {
            isOutputVideo = false;
            optOutputVideo.setBackgroundResource(R.drawable.bg_output_inactive);
            optOutputAudio.setBackgroundResource(R.drawable.bg_output_active);
        });

        findViewById(R.id.btnPickVideo).setOnClickListener(v -> openFilePicker("video/*", REQ_PICK_VIDEO));
        findViewById(R.id.btnPickSofa).setOnClickListener(v -> openFilePicker("*/*", REQ_PICK_SOFA));
        findViewById(R.id.btnPickEq).setOnClickListener(v -> openFilePicker("text/plain", REQ_PICK_EQ));

        findViewById(R.id.btnPickFolder).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQ_PICK_DIR);
        });

        findViewById(R.id.btnStartRender).setOnClickListener(v -> startRealRendering());

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            selectedVideoPath = "";
            selectedSofaPath = "";
            selectedEqPath = "";
            totalDurationMs = 0;
            tvVideoName.setText("");
            tvVideoInfo.setText("");
            tvSofaName.setText("");
            tvSofaFile.setText("");
            switchEq.setChecked(false);
            Toast.makeText(this, "Pengaturan direset", Toast.LENGTH_SHORT).show();
        });
    }

    private void openFilePicker(String mimeType, int reqCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        startActivityForResult(Intent.createChooser(intent, "Pilih File"), reqCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            if (requestCode == REQ_PICK_VIDEO) {
                selectedVideoPath = uriToActualPath(uri);
                File f = new File(selectedVideoPath);
                tvVideoName.setText(f.getName());

                // Ambil durasi total video untuk menghitung progress
                try {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(selectedVideoPath);
                    String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dur != null) totalDurationMs = Long.parseLong(dur);
                    mmr.release();
                } catch (Exception ignored) {}

                tvVideoInfo.setText("Ukuran: " + (f.length() / (1024 * 1024)) + " MB • Surround Siap");
            } else if (requestCode == REQ_PICK_SOFA) {
                selectedSofaPath = uriToActualPath(uri);
                tvSofaName.setText("Custom HRTF Loaded");
                tvSofaFile.setText(new File(selectedSofaPath).getName());
            } else if (requestCode == REQ_PICK_EQ) {
                selectedEqPath = uriToActualPath(uri);
                tvEqProfileName.setText("AutoEQ Profile Loaded");
                tvEqFileName.setText(new File(selectedEqPath).getName());
            } else if (requestCode == REQ_PICK_DIR) {
                outputDirPath = uriToActualPath(uri);
                if (!outputDirPath.endsWith("/")) outputDirPath += "/";
                tvOutputDir.setText(outputDirPath);
            }
        }
    }

    private String uriToActualPath(Uri uri) {
        String path = uri.getPath();
        if (path != null && path.contains(":")) {
            String[] split = path.split(":");
            if (split.length > 1) return "/storage/emulated/0/" + split[1];
        }
        return path != null ? path : "";
    }

    private void startRealRendering() {
        if (selectedVideoPath.isEmpty()) {
            Toast.makeText(this, "Silakan pilih video input terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        progressBar = dialogView.findViewById(R.id.progressBar);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        tvProgressPercent = dialogView.findViewById(R.id.tvProgressPercent);
        tvProgressPercent.setText("0%");

        tvProgressStatus = dialogView.findViewById(R.id.tvProgressStatus);
        tvProgressStatus.setText("Menginisialisasi kompresi & HRTF...");

        Button btnCancel = dialogView.findViewById(R.id.btnCancelRender);

        progressDialog = builder.create();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        progressDialog.show();
        isRendering = true;

        btnCancel.setOnClickListener(v -> {
            if (ffmpegProcess != null) ffmpegProcess.destroy();
            isRendering = false;
            progressDialog.dismiss();
            Toast.makeText(this, "Rendering dibatalkan!", Toast.LENGTH_SHORT).show();
        });

        new Thread(this::runFFmpegCommand).start();
    }

    private void runFFmpegCommand() {
        try {
            String ffmpegBin = getApplicationInfo().nativeLibraryDir + "/libffmpeg.so";
            String outExt = isOutputVideo ? ".mkv" : ".flac";
            String outFileName = "Binaural_" + System.currentTimeMillis() + outExt;
            String finalOutputPath = outputDirPath + outFileName;

            // Rancang rantai filter (Sofalizer + AutoEQ)
            StringBuilder af = new StringBuilder();
            if (!selectedSofaPath.isEmpty()) {
                af.append("sofalizer=sofa=").append(selectedSofaPath).append(":type=freq");
            } else {
                af.append("pan=stereo|c0=FL+0.7*FC+0.5*BL+0.5*SL|c1=FR+0.7*FC+0.5*BR+0.5*SR");
            }

            if (switchEq.isChecked() && !selectedEqPath.isEmpty()) {
                String eqChain = parseAutoEqFile(selectedEqPath);
                if (!eqChain.isEmpty()) {
                    af.append(",").append(eqChain);
                }
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegBin);
            cmd.add("-y");
            // Aktifkan reporting progress real-time ke stdout
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
            cmd.add("-i");
            cmd.add(selectedVideoPath);
            cmd.add("-af");
            cmd.add(af.toString());

            if (isOutputVideo) {
                cmd.add("-c:v");
                cmd.add("copy");
                cmd.add("-c:a");
                cmd.add("flac");
            } else {
                cmd.add("-vn");
                cmd.add("-c:a");
                cmd.add("flac");
            }

            cmd.add(finalOutputPath);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            ffmpegProcess = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));
            String line;
            String currentSpeed = "1.0x";

            while ((line = reader.readLine()) != null) {
                if (!isRendering) break;

                // Tangkap kecepatan render (speed)
                if (line.startsWith("speed=")) {
                    currentSpeed = line.substring(6).trim();
                }

                // Tangkap waktu pemrosesan real-time (out_time=HH:MM:SS.ms)
                if (line.startsWith("out_time=")) {
                    String timeStr = line.substring(9).trim();
                    long currentMs = parseTimeToMs(timeStr);

                    if (totalDurationMs > 0) {
                        int percent = (int) ((currentMs * 100) / totalDurationMs);
                        if (percent > 100) percent = 100;
                        int finalPercent = percent;
                        String finalSpeed = currentSpeed;
                        String shortTime = timeStr.length() >= 8 ? timeStr.substring(0, 8) : timeStr;

                        handler.post(() -> {
                            progressBar.setProgress(finalPercent);
                            tvProgressPercent.setText(finalPercent + "%");
                            tvProgressStatus.setText("Rendering: " + shortTime + " (" + finalSpeed + ")");
                        });
                    }
                }
            }

            int exitCode = ffmpegProcess.waitFor();
            handler.post(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (exitCode == 0) {
                    Toast.makeText(this, "Sukses! Tersimpan di: " + outFileName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Rendering gagal (Exit code: " + exitCode + ")", Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            handler.post(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private long parseTimeToMs(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return (long) ((hours * 3600 + minutes * 60 + seconds) * 1000);
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
