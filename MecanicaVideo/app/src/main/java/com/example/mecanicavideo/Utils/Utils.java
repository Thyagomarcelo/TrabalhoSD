package com.example.mecanicavideo;

import org.webrtc.PeerConnection;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static List<PeerConnection.IceServer> getIceServers() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();

        // STUN server público do Google
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        // Aqui você pode adicionar TURN servers se precisar (exemplo comentado):
        // iceServers.add(PeerConnection.IceServer.builder("turn:turn.example.com:3478")
        //    .setUsername("user")
        //    .setPassword("pass")
        //    .createIceServer());

        return iceServers;
    }
}