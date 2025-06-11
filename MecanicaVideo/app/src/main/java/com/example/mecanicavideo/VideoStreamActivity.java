package com.example.mecanicavideo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

public class VideoStreamActivity extends AppCompatActivity implements SignalingClient.SignalingEvents {

    private static final String TAG = "VideoStreamActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private SurfaceViewRenderer localView;

    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;

    private EglBase rootEglBase;

    private SignalingClient signalingClient;

    private String roomId;
    private String role;

    private SurfaceTextureHelper surfaceTextureHelper;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String recordedFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_stream);

        localView = findViewById(R.id.remote_view);

        roomId = getIntent().getStringExtra("roomId");
        role = getIntent().getStringExtra("role");

        Button btnEnviar = findViewById(R.id.btnEnviar);

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            init();
        }

        btnEnviar.setOnClickListener(view -> {
            stopRecording(); // para garantir que o vídeo foi salvo
            uploadVideo(recordedFilePath);
        });
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
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

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .setFieldTrials("WebRTC-IntelVP8/Enabled/")
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

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        VideoCapturer videoCapturer = createVideoCapturer();

        VideoSource videoSource = factory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
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
        startRecording();
    }

    private VideoCapturer createVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isBackFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        for (String deviceName : enumerator.getDeviceNames()) {
            VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) return capturer;
        }
        throw new RuntimeException("Não foi possível encontrar capturador de vídeo");
    }

    private PeerConnection createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(com.example.mecanicavideo.Utils.getIceServers());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection pc = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Log.d(TAG, "onIceCandidate: " + candidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                Log.d(TAG, "onIceCandidatesRemoved");
            }

            @Override
            public void onAddStream(MediaStream stream) {
                Log.d(TAG, "onAddStream");
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
                Log.d(TAG, "onRemoveStream");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack");
            }
        });

        return pc;
    }

    // Métodos do SignalingClient.SignalingEvents (exemplo simples)

    @Override
    public void onPeerJoined(String id, String role) {
        if ("client".equals(role)) {
            Log.d(TAG, "Vai criar a offer");
            createOffer();
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new org.webrtc.SdpObserver() {

            @Override
            public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: Offer criada");
                Log.d(TAG, "setLocalDescription vai ser chamado");
                peerConnection.setLocalDescription(new org.webrtc.SdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: setLocalDescription OK");
                        try {
                            signalingClient.sendOffer(sessionDescription.description);
                            Log.d(TAG, "Offer enviada");
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao enviar offer", e);
                        }
                    }
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "onSetFailure: " + s);
                    }
                    @Override public void onCreateSuccess(SessionDescription sdp) {}
                    @Override public void onCreateFailure(String s) {}
                }, sessionDescription);
                Log.d(TAG, "setLocalDescription chamado");
            }

            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) { Log.e(TAG, "Offer failure: " + s); }
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set local desc failure: " + s); }
        }, constraints);
    }

    @Override
    public void onOffer(String fromId, String sdp) {
        // Se só enviar, você pode ignorar essa parte, ou adaptar se quiser responder
    }

    @Override
    public void onAnswer(String fromId, String sdp) {
        peerConnection.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override
            public void onSetSuccess() {}
            @Override public void onSetFailure(String s) { Log.e(TAG, "Set remote desc failure: " + s); }
            @Override public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) {}
            @Override public void onCreateFailure(String s) {}
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

    }

    private void startRecording() {
        if (isRecording) return;

        try {
            // Define onde salvar
            File file = new File(getExternalFilesDir(null), "gravacao.mp4");
            recordedFilePath = file.getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(recordedFilePath);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(640, 480);

            mediaRecorder.prepare();

            // ⚠️ IMPORTANTE: isso cria uma Surface que DEVE ser usada pela câmera
            Surface recordingSurface = mediaRecorder.getSurface();

            // Agora você precisa redirecionar os frames para essa surface
            // Isso só é possível com Camera2 API, NÃO com WebRTC direto.
            // Então aqui está a limitação: WebRTC não pode compartilhar a surface com MediaRecorder diretamente.
            // Como solução simples: grave da câmera separadamente (fora do WebRTC) OU use uma biblioteca que suporte múltiplas saídas.

            Log.e(TAG, "⚠️ MediaRecorder preparado, mas NÃO está recebendo frames pois não há ligação com a câmera.");

            mediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar gravação", e);
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parar gravação", e);
        }
    }

    private void uploadVideo(String filePath) {
        new Thread(() -> {
            try {
                File videoFile = new File(filePath);
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

                URL url = new URL("http://192.168.1.12:3000/api/upload/upload-video");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setUseCaches(false);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                DataOutputStream request = new DataOutputStream(conn.getOutputStream());
                request.writeBytes("--" + boundary + "\r\n");
                request.writeBytes("Content-Disposition: form-data; name=\"video\"; filename=\"" + videoFile.getName() + "\"\r\n");
                request.writeBytes("Content-Type: video/mp4\r\n\r\n");

                FileInputStream inputStream = new FileInputStream(videoFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    request.write(buffer, 0, bytesRead);
                }
                inputStream.close();

                request.writeBytes("\r\n--" + boundary + "--\r\n");
                request.flush();
                request.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Upload resposta: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fazer upload: ", e);
            }
        }).start();
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
    }
}