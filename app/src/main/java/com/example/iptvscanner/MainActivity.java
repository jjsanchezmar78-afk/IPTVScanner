
package com.example.iptvscanner;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    EditText m3uUrl, server, username, password;
    TextView output;
    ArrayList<String> channels = new ArrayList<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("IPTV SCANNER");
        title.setTextSize(27);
        title.setPadding(0, 10, 0, 20);
        layout.addView(title);

        m3uUrl = new EditText(this);
        m3uUrl.setHint("URL de tu lista M3U");
        layout.addView(m3uUrl);

        addButton(layout, "Cargar M3U desde URL", v -> loadM3U());

        addButton(layout, "Abrir archivo M3U", v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        });

        addButton(layout, "Comprobar enlaces", v -> checkChannels());

        TextView subtitle = new TextView(this);
        subtitle.setText("\nCONSULTA XTREAM CODES");
        subtitle.setTextSize(20);
        layout.addView(subtitle);

        server = new EditText(this);
        server.setHint("Servidor (https://ejemplo.com:8080)");
        layout.addView(server);

        username = new EditText(this);
        username.setHint("Usuario");
        layout.addView(username);

        password = new EditText(this);
        password.setHint("Contraseña");
        password.setInputType(129);
        layout.addView(password);

        addButton(layout, "Consultar cuenta Xtream", v -> checkXtream());

        output = new TextView(this);
        output.setTextSize(15);
        output.setPadding(0, 25, 0, 20);
        layout.addView(output);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private void addButton(LinearLayout layout, String text,
                           View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        layout.addView(button);
    }

    private void showResult(String text) {
        runOnUiThread(() -> output.append(text + "\n"));
    }

    private String download(String address) throws Exception {
        URL url = new URL(address);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "IPTVScanner/1.0");

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            reader.close();
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void loadM3U() {
        String address = m3uUrl.getText().toString().trim();

        if (!address.startsWith("http://") &&
                !address.startsWith("https://")) {
            output.setText("Introduce una URL M3U válida.");
            return;
        }

        output.setText("Descargando lista...\n");

        executor.execute(() -> {
            try {
                parseM3U(download(address));
            } catch (Exception e) {
                showResult("Error al cargar M3U: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();

            executor.execute(() -> {
                try {
                    InputStream input = getContentResolver()
                            .openInputStream(uri);

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(input,
                                    StandardCharsets.UTF_8));

                    StringBuilder content = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }

                    reader.close();
                    parseM3U(content.toString());

                } catch (Exception e) {
                    showResult("Error al abrir archivo: " + e.getMessage());
                }
            });
        }
    }

    private void parseM3U(String content) {
        channels.clear();

        for (String line : content.split("\\r?\\n")) {
            String value = line.trim();

            if (value.startsWith("http://") ||
                    value.startsWith("https://")) {
                channels.add(value);
            }
        }

        runOnUiThread(() -> {
            output.setText("LISTA M3U CARGADA\n");
            output.append("Canales encontrados: " +
                    channels.size() + "\n");
            output.append("Pulsa Comprobar enlaces para analizarlos.\n");
        });
    }

    private void checkChannels() {
        if (channels.isEmpty()) {
            output.setText("Primero carga una lista M3U.");
            return;
        }

        output.setText("COMPROBANDO ENLACES...\n");

        executor.execute(() -> {
            int limit = Math.min(channels.size(), 20);
            int working = 0;

            for (int i = 0; i < limit; i++) {
                String address = channels.get(i);

                try {
                    HttpURLConnection connection =
                            (HttpURLConnection)
                                    new URL(address).openConnection();

                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Range", "bytes=0-0");

                    int code = connection.getResponseCode();
                    connection.disconnect();

                    if (code >= 200 && code < 400) {
                        working++;
                        showResult("OK - Canal " + (i + 1));
                    } else {
                        showResult("ERROR HTTP " + code +
                                " - Canal " + (i + 1));
                    }

                } catch (Exception e) {
                    showResult("NO DISPONIBLE - Canal " + (i + 1));
                }
            }

            showResult("RESULTADO: " + working +
                    " de " + limit + " enlaces responden.");
        });
    }

    private void checkXtream() {
        String host = server.getText().toString().trim();
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();

        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            output.setText("Rellena servidor, usuario y contraseña.");
            return;
        }

        output.setText("Consultando cuenta autorizada...\n");

final String cleanHost = host.replaceAll("/+$", "");

executor.execute(() -> {
    try {
        String address = cleanHost + "/player_api.php?username=" +
                        URLEncoder.encode(user, "UTF-8") +
                        "&password=" +
                        URLEncoder.encode(pass, "UTF-8");

                JSONObject response = new JSONObject(download(address));
                JSONObject info = response.optJSONObject("user_info");

                if (info == null) {
                    showResult("Respuesta no reconocida del servidor.");
                    return;
                }

                boolean authenticated = info.optInt("auth", 0) == 1;

                showResult("AUTENTICACIÓN: " +
                        (authenticated ? "Correcta" : "No válida"));

                showResult("ESTADO: " +
                        info.optString("status", "No disponible"));

                String expiry = info.optString("exp_date", "");

                if (!expiry.isEmpty() && !expiry.equals("null")) {
                    try {
                        long timestamp = Long.parseLong(expiry);
                        Date date = new Date(timestamp * 1000L);
                        showResult("CADUCIDAD: " + date.toString());
                    } catch (Exception e) {
                        showResult("CADUCIDAD: " + expiry);
                    }
                } else {
                    showResult("CADUCIDAD: No disponible");
                }

                showResult("CONEXIONES MÁXIMAS: " +
                        info.optString("max_connections", "No disponible"));

                showResult("CONEXIONES ACTIVAS: " +
                        info.optString("active_cons", "No disponible"));

            } catch (Exception e) {
                showResult("Error de consulta: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
