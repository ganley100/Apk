package tvfix;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObbImportActivity extends Activity {
    static final String OBB = "main.30722.com.metalheadsoftware.smb.obb";
    TextView status;
    EditText link;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(80, 60, 80, 60);

        TextView title = new TextView(this);
        title.setText("Import game data (OBB)\nTarget: " + OBB);
        title.setTextSize(22);
        root.addView(title);

        link = new EditText(this);
        link.setHint("Google Drive share link");
        link.setSingleLine(true);
        try {
            InputStream in = getAssets().open("drive_link.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            String s = r.readLine();
            r.close();
            if (s != null && s.contains("drive.google")) link.setText(s.trim());
        } catch (Exception ignored) {}
        root.addView(link);

        Button dl = new Button(this);
        dl.setText("Download from Drive link");
        dl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startDownload(); }
        });
        root.addView(dl);

        Button folder = new Button(this);
        folder.setText("Copy from Download folder");
        folder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copyFromDownload(); }
        });
        root.addView(folder);

        Button pick = new Button(this);
        pick.setText("Pick file (Drive / USB)");
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickFile(); }
        });
        root.addView(pick);

        Button play = new Button(this);
        play.setText("Start game");
        play.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startGame(); }
        });
        root.addView(play);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 40, 0, 0);
        File have = new File(getObbDir(), OBB);
        status.setText(have.isFile()
            ? "OBB already installed (" + (have.length() >> 20) + " MB). Tap Start game."
            : "OBB not installed yet. Pick a button above.");
        root.addView(status);

        setContentView(root);
    }

    void say(final String s) {
        runOnUiThread(new Runnable() { public void run() { status.setText(s); } });
    }

    void startGame() {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), "com.metalheadsoftware.smb.BigflyLeanbackActivity");
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent();
                i.setClassName(getPackageName(), "com.metalheadsoftware.smb.BigflyActivity");
                startActivity(i);
            } catch (Exception e2) {
                say("Couldn't start the game: " + e2);
            }
        }
    }

    void copyFromDownload() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 2);
            say("Allow storage access. If nothing happens afterward, tap the button again.");
            return;
        }
        File dl = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        File[] candidates = new File[] {
            new File(dl, OBB),
            new File(android.os.Environment.getExternalStorageDirectory(), OBB)
        };
        File found = null;
        for (File f : candidates) {
            if (f.isFile()) { found = f; break; }
        }
        if (found == null) {
            say("Couldn't see " + OBB + " in\n" + dl.getAbsolutePath()
                + "\n(or the storage root). Check the exact file name, or the app may not be allowed to see it.");
            return;
        }
        final File src = found;
        new Thread(new Runnable() {
            public void run() {
                try {
                    copy(new FileInputStream(src), src.length());
                } catch (Exception e) {
                    say("Failed: " + e);
                }
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != 2) return;
        if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            copyFromDownload();
        } else {
            say("Storage permission was denied.");
        }
    }

    void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, 1);
        } catch (ActivityNotFoundException e) {
            say("No file picker on this device. Use the Drive link button instead.");
        } catch (Exception e) {
            say("Picker error: " + e);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != 1 || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    copy(in, -1);
                } catch (Exception e) {
                    say("Failed: " + e);
                }
            }
        }).start();
    }

    void startDownload() {
        final String text = link.getText().toString();
        Matcher m = Pattern.compile("/d/([A-Za-z0-9_-]+)").matcher(text);
        String id = null;
        if (m.find()) id = m.group(1);
        else {
            m = Pattern.compile("[?&]id=([A-Za-z0-9_-]+)").matcher(text);
            if (m.find()) id = m.group(1);
        }
        if (id == null) { say("Couldn't find a file ID in that link."); return; }
        final String url = "https://drive.usercontent.google.com/download?id=" + id + "&export=download&confirm=t";
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(30000);
                    String type = c.getContentType();
                    if (c.getResponseCode() != 200 || (type != null && type.contains("text/html"))) {
                        say("Drive returned a web page instead of the file (HTTP " + c.getResponseCode()
                            + "). Make sure sharing is set to 'Anyone with the link'.");
                        return;
                    }
                    copy(c.getInputStream(), c.getContentLength());
                } catch (Exception e) {
                    say("Download failed: " + e);
                }
            }
        }).start();
    }

    void copy(InputStream in, long total) throws IOException {
        File dir = getObbDir();
        if (dir == null) { say("Storage not available."); return; }
        dir.mkdirs();
        File part = new File(dir, OBB + ".part");
        File out = new File(dir, OBB);
        OutputStream os = new FileOutputStream(part);
        byte[] buf = new byte[1 << 20];
        long done = 0, last = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
            done += n;
            long now = System.currentTimeMillis();
            if (now - last > 400) {
                last = now;
                say("Copying... " + (done >> 20) + " MB" + (total > 0 ? " of " + (total >> 20) + " MB" : ""));
            }
        }
        os.close();
        in.close();
        if (out.exists()) out.delete();
        if (part.renameTo(out)) say("Done! " + (done >> 20) + " MB saved to:\n" + out.getAbsolutePath());
        else say("Copied but couldn't rename to " + out.getAbsolutePath());
    }
}
