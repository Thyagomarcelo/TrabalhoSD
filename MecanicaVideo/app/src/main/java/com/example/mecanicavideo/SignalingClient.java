package com.example.mecanicavideo;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SignalingClient {
    private static final String TAG = "SignalingClient";
    private static SignalingClient instance;

    private Socket socket;
    private String serverUrl;
    private String roomId;
    private String role;

    private SignalingEvents events;

    public interface SignalingEvents {
        void onOffer(String fromId, String sdp);
        void onAnswer(String fromId, String sdp);
        void onIceCandidate(String fromId, JSONObject candidate);
        void onPeerJoined(String id, String role);
        void onPeerLeft(String id);
    }

    private SignalingClient() {}

    public static SignalingClient getInstance() {
        if (instance == null) {
            instance = new SignalingClient();
        }
        return instance;
    }

    public void init(String serverUrl, String roomId, String role, SignalingEvents events) {
        this.serverUrl = serverUrl;
        this.roomId = roomId;
        this.role = role;
        this.events = events;

        try {
            socket = IO.socket(serverUrl);

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.i(TAG, "Socket conectado");
                JSONObject data = new JSONObject();
                try {
                    data.put("roomId", roomId);
                    data.put("role", role);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                socket.emit("join-room", data);
            });

            socket.on("offer", args -> {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    JSONObject sdpObj = data.getJSONObject("sdp");
                    String sdpStr = sdpObj.getString("sdp");
                    if (events != null) events.onOffer(from, sdpStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            socket.on("answer", args -> {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    JSONObject sdpObj = data.getJSONObject("sdp");
                    String sdpStr = sdpObj.getString("sdp");
                    if (events != null) events.onAnswer(from, sdpStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            socket.on("ice-candidate", args -> {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    JSONObject candidate = data.getJSONObject("candidate");
                    if (events != null) events.onIceCandidate(from, candidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            socket.on("peer-joined", args -> {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    String peerRole = data.getString("role");
                    if (events != null) events.onPeerJoined(id, peerRole);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            socket.on("peer-left", args -> {
                String id = (String) args[0];
                if (events != null) events.onPeerLeft(id);
            });

            socket.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendOffer(String sdp) {
        JSONObject data = new JSONObject();
        try {
            Log.d("TAG", "SendOffer");
            data.put("roomId", roomId);
            JSONObject sdpJson = new JSONObject();
            sdpJson.put("type", "offer");
            sdpJson.put("sdp", sdp);
            data.put("sdp", sdpJson);
            Log.d("Tag","sendOffer");
            socket.emit("offer", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendAnswer(SessionDescription sdp) {
        JSONObject data = new JSONObject();
        try {
            data.put("roomId", roomId);
            JSONObject sdpJson = new JSONObject();
            sdpJson.put("type", "answer");
            sdpJson.put("sdp", sdp.description);
            data.put("sdp", sdpJson);
            socket.emit("answer", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendIceCandidate(JSONObject candidate) {
        JSONObject data = new JSONObject();
        try {
            data.put("roomId", roomId);
            data.put("candidate", candidate);
            socket.emit("ice-candidate", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (socket != null) socket.disconnect();
    }
}