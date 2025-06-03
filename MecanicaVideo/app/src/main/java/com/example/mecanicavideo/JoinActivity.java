package com.example.mecanicavideo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class JoinActivity extends AppCompatActivity {
    EditText editRoom;
    Button btnEntrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        editRoom = findViewById(R.id.editRoom);
        btnEntrar = findViewById(R.id.btnEntrar);

        btnEntrar.setOnClickListener(v -> {
            String roomId = editRoom.getText().toString().trim();
            if (!roomId.isEmpty()) {
                Intent intent = new Intent(JoinActivity.this, VideoStreamActivity.class);
                intent.putExtra("roomId", roomId);
                intent.putExtra("role", "mechanic"); // ou "client"
                startActivity(intent);
            }
        });
    }
}