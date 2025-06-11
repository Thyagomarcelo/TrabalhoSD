package com.example.mecanicavideo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    EditText editEmail, editSenha;
    Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editEmail = findViewById(R.id.editEmail);
        editSenha = findViewById(R.id.editSenha);
        btnLogin = findViewById(R.id.btnEntrar);

        Log.d("Tag", "Email e senha antes do click: " + editEmail.getText().toString() + editSenha.getText().toString());

        btnLogin.setOnClickListener(v -> fazerLogin());
    }

    private void fazerLogin() {
        new Thread(() -> {
            try {
                String email = editEmail.getText().toString();
                String senha = editSenha.getText().toString();

                Log.d("Tag", "Email e senha: " + email + ", " + senha);

                URL url = new URL("http://192.168.1.12:3000/api/mecanicos/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                // Cria o JSON
                JSONObject json = new JSONObject();
                json.put("email", email);
                json.put("senha", senha);
                String jsonString = json.toString();

                // Envia o JSON
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Recebe a resposta
                int responseCode = conn.getResponseCode();
                Log.d("Tag", "Código retornado: " + responseCode);

                InputStream is = (responseCode >= 200 && responseCode < 400)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader in = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line.trim());
                }
                in.close();

                String resposta = response.toString();
                Log.d("LoginResponse", resposta);

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(MainActivity.this, JoinActivity.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Falha no login: " + resposta, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erro de conexão: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}

