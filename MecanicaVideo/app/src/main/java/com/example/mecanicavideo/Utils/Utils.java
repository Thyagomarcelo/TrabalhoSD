package com.example.mecanicavideo;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static List<PeerConnection.IceServer> getIceServers() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        // Adicione TURN se precisar
        return iceServers;
    }
}