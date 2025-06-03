package com.example.mecanicavideo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Collections;
import java.util.List;

public class VideoStreamActivity extends AppCompatActivity implements SignalingClient.SignalingEvents {

    private static final String TAG = "VideoStreamActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;

    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;

    private EglBase rootEglBase;

    private SignalingClient signalingClient;

    private String roomId;
    private String role;

    private SurfaceTextureHelper surfaceTextureHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_stream);

        localView = findViewById(R.id.local_view);
        remoteView = findViewById(R.id.remote_view);

        roomId = getIntent().getStringExtra("roomId");
        role = getIntent().getStringExtra("role");

        rootEglBase = EglBase.create();

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            init();
        }
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                init();
            } else {
                Log.e(TAG, "Permissões não concedidas");
                finish();
            }
        }
    }

    private void init() {
        rootEglBase = EglBase.create();

        localView.init(rootEglBase.getEglBaseContext(), null);
        localView.setMirror(true);
        remoteView.init(rootEglBase.getEglBaseContext(), null);
        remoteView.setMirror(false);

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        VideoCapturer videoCapturer = createVideoCapturer();

        VideoSource videoSource = factory.createVideoSource(false);
        videoCapturer.initialize(
                surfaceTextureHelper,
                getApplicationContext(),
                videoSource.getCapturerObserver());
        videoCapturer.startCapture(640, 480, 30);

        localVideoTrack = factory.createVideoTrack("100", videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localView);

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("101", audioSource);
        localAudioTrack.setEnabled(true);

        peerConnection = createPeerConnection();

        peerConnection.addTrack(localVideoTrack, Collections.singletonList("mediaStream"));
        peerConnection.addTrack(localAudioTrack, Collections.singletonList("mediaStream"));

        signalingClient = SignalingClient.getInstance();
        signalingClient.init("http://192.168.1.12:3000", roomId, role, this);
    }

    private VideoCapturer createVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }

        // Se não encontrar frontal, tenta qualquer uma
        for (String deviceName : deviceNames) {
            VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) {
                return capturer;
            }
        }

        throw new RuntimeException("Não foi possível encontrar capturador de vídeo");
    }

    private PeerConnection createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(com.example.mecanicavideo.Utils.getIceServers());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        return factory.createPeerConnection(rtcConfig, new PeerConnectionAdapter() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    JSONObject candidate = new JSONObject();
                    candidate.put("sdpMid", iceCandidate.sdpMid);
                    candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    candidate.put("candidate", iceCandidate.sdp);
                    signalingClient.sendIceCandidate(candidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                if (mediaStream.videoTracks.size() > 0) {
                    mediaStream.videoTracks.get(0).addSink(remoteView);
                }
            }
        });
    }

    @Override
    public void onPeerJoined(String id, String role) {
        Log.d(TAG, "Peer joined: " + id + ", role: " + role);
        if ("client".equals(this.role)) {
            createOffer();
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new org.webrtc.SdpObserver() {
            @Override
            public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(this, sessionDescription);
                signalingClient.sendOffer(sessionDescription.description);
            }

            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String s) { Log.e(TAG, "Offer failure: " + s); }
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set local description failure: " + s); }
        }, constraints);
    }

    @Override
    public void onOffer(String fromId, String sdp) {
        Log.d(TAG, "Offer received from " + fromId);
        peerConnection.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override
            public void onSetSuccess() {
                createAnswer();
            }
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set remote desc failure: " + s); }
            @Override public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) { }
            @Override public void onCreateFailure(String s) { }
        }, new org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.OFFER, sdp));
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createAnswer(new org.webrtc.SdpObserver() {
            @Override
            public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(this, sessionDescription);
                signalingClient.sendAnswer(sessionDescription.description);
            }

            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String s) { Log.e(TAG, "Answer failure: " + s); }
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set local desc failure: " + s); }
        }, constraints);
    }

    @Override
    public void onAnswer(String fromId, String sdp) {
        Log.d(TAG, "Answer received from " + fromId);
        peerConnection.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override
            public void onSetSuccess() { }
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set remote desc failure: " + s); }
            @Override public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) { }
            @Override public void onCreateFailure(String s) { }
        }, new org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.ANSWER, sdp));
    }

    @Override
    public void onIceCandidate(String fromId, JSONObject candidate) {
        try {
            IceCandidate iceCandidate = new IceCandidate(
                    candidate.getString("sdpMid"),
                    candidate.getInt("sdpMLineIndex"),
                    candidate.getString("candidate")
            );
            peerConnection.addIceCandidate(iceCandidate);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPeerLeft(String id) {
        Log.d(TAG, "Peer left: " + id);
        // Aqui pode fazer limpeza se quiser
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }
        if (signalingClient != null) {
            signalingClient.disconnect();
        }
    }
}