package com.example.mecanicavideo;

import org.webrtc.PeerConnection;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public abstract class PeerConnectionAdapter implements PeerConnection.Observer {

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) { }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) { }

    @Override
    public void onIceConnectionReceivingChange(boolean b) { }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) { }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) { }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) { }

    @Override
    public void onAddStream(org.webrtc.MediaStream mediaStream) { }

    @Override
    public void onRemoveStream(org.webrtc.MediaStream mediaStream) { }

    @Override
    public void onDataChannel(org.webrtc.DataChannel dataChannel) { }

    @Override
    public void onRenegotiationNeeded() { }

    @Override
    public void onAddTrack(org.webrtc.RtpReceiver rtpReceiver, org.webrtc.MediaStream[] mediaStreams) { }
}