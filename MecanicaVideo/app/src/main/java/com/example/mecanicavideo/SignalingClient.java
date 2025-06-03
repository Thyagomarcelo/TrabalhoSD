package com.example.mecanicavideo;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;

public class SignalingClient {

    private static final String TAG = "SignalingClient";
    private static SignalingClient instance;
    private Socket socket;

    private String roomId;
    private String role;
    private SignalingEvents events;

    public interface SignalingEvents {
        void onPeerJoined(String id, String role);
        void onOffer(String fromId, String sdp);
        void onAnswer(String fromId, String sdp);
        void onIceCandidate(String fromId, JSONObject candidate);
        void onPeerLeft(String id);
    }

    private SignalingClient() { }

    public static SignalingClient getInstance() {
        if (instance == null) {
            instance = new SignalingClient();
        }
        return instance;
    }

    public void init(String serverUrl, String roomId, String role, SignalingEvents events) {
        this.roomId = roomId;
        this.role = role;
        this.events = events;

        try {
            socket = IO.socket(serverUrl);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected");
            joinRoom();
        });

        socket.on("peer-joined", args -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String id = data.getString("id");
                this.role = data.getString("role");
                events.onPeerJoined(id, role);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.on("offer", args -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String from = data.getString("from");
                String sdp = data.getString("sdp");
                events.onOffer(from, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.on("answer", args -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String from = data.getString("from");
                String sdp = data.getString("sdp");
                events.onAnswer(from, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.on("ice-candidate", args -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String from = data.getString("from");
                JSONObject candidate = data.getJSONObject("candidate");
                events.onIceCandidate(from, candidate);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.on("peer-left", args -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String id = data.getString("id");
                events.onPeerLeft(id);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.connect();
    }

    private void joinRoom() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("roomId", roomId);
            obj.put("role", role);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("join-room", obj);
    }

    public void sendOffer(String sdp) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("roomId", roomId);
            obj.put("sdp", sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("offer", obj);
    }

    public void sendAnswer(String sdp) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("roomId", roomId);
            obj.put("sdp", sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("answer", obj);
    }

    public void sendIceCandidate(JSONObject candidate) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("roomId", roomId);
            obj.put("candidate", candidate);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("ice-candidate", obj);
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
    }
}