package com.example.myapplicationasd

import OnSwipeTouchListener
import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app.theme.ThemeManager
import com.example.app.theme.ThemeOption
import com.example.app.theme.ThemeStore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.example.myapplicationasd.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PlayerState { STOPPED, PLAYING, PAUSED, FINISHED }

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var accessToken: String? = null

    private var playerState = PlayerState.STOPPED
    private var lastTrackUri: String? = null
    private var trackDuration: Long = 0L
    private var progressAnimator: ValueAnimator? = null
    private var currentProgress = 0f
    private var lastScanned = ""
    private var isProgressRunning = false
    private var isProgressFinished = false

    private var sessionId: Long = 0L

    companion object {
        private const val clientId = "da0095c04df945a2874dd9e91bc80fc9"
        private const val redirectUri = "hitsterclone://callback"
    }
    private lateinit var themeStore: ThemeStore
    private var currentTheme: ThemeOption = ThemeOption.Classic
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showOnlyQrIcon()
        themeStore = ThemeStore(this)

        lifecycleScope.launch {
            currentTheme = themeStore.themeFlow.first()
            ThemeManager.currentTheme = currentTheme
            ThemeManager.applyTheme(this@MainActivity, currentTheme)
        }

        binding.btnTheme.setOnClickListener {
            showThemePicker()
        }
        binding.qrIcon.setOnClickListener {
            checkCameraPermissionAndStart()
        }

        binding.playPauseButton.setOnClickListener {
            when (playerState) {
                PlayerState.PLAYING -> pauseMusic()
                PlayerState.PAUSED -> resumeOrRestart()
                PlayerState.FINISHED, PlayerState.STOPPED -> restartFromBeginning()
            }
        }

        binding.cancelButton.setOnClickListener {
            resetAll()
        }

        binding.root.setOnTouchListener(object : OnSwipeTouchListener(this) {
            override fun onSwipeRight() {
                resetAll()
            }

            override fun onSwipeLeft() {
                resetAll()
            }
        })


        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    // UI FLOW
    private fun showOnlyQrIcon() {
        binding.qrIcon.visibility = View.VISIBLE
        binding.playPauseButton.visibility = View.GONE
        binding.progressCircle.visibility = View.GONE
        binding.progressCircle.progress = 0f
        currentProgress = 0f
        playerState = PlayerState.STOPPED
        isProgressRunning = false
        isProgressFinished = false
    }

    private fun showPlayerUi() {
        binding.qrIcon.visibility = View.GONE
        binding.playPauseButton.visibility = View.VISIBLE
        binding.progressCircle.visibility = View.VISIBLE
    }

    // ZENE+ANIMÁCIÓ INDÍTÁS QR UTÁN
    private fun startMusicAndAnimation(uri: String) {
        val mySession = ++sessionId  // minden új flow új sessionId-t kap!
        lastTrackUri = uri
        playerState = PlayerState.PLAYING
        isProgressRunning = false
        isProgressFinished = false
        showPlayerUi()

        binding.playPauseButton.setImageResource(R.drawable.ic_pause)
        binding.playPauseButton.visibility = View.VISIBLE
        binding.progressCircle.visibility = View.VISIBLE
        binding.qrIcon.visibility = View.GONE

        progressAnimator?.cancel()
        currentProgress = 0f
        binding.progressCircle.progress = 0f

        spotifyAppRemote?.let { remote ->
            remote.playerApi.play(uri)
            remote.playerApi.seekTo(0L)
            remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
                // Csak akkor dolgozunk, ha még él az aktuális session!
                if (sessionId != mySession) return@setEventCallback
                val track = state.track
                if (track != null && track.uri == uri && !isProgressRunning && !isProgressFinished) {
                    isProgressRunning = true
                    isProgressFinished = false
                    trackDuration = track.duration
                    startProgressAnimation(track.duration, mySession)
                }
            }
        }
    }

    private fun pauseMusic() {
        spotifyAppRemote?.playerApi?.pause()
        progressAnimator?.pause()
        playerState = PlayerState.PAUSED
        updatePlayPauseIcon()
    }

    private fun resumeOrRestart() {
        if (!isProgressFinished) {
            spotifyAppRemote?.playerApi?.resume()
            progressAnimator?.resume()
            playerState = PlayerState.PLAYING
        } else {
            restartFromBeginning()
        }
        updatePlayPauseIcon()
    }

    private fun restartFromBeginning() {
        lastTrackUri?.let {
            startMusicAndAnimation(it)
        }
    }

    private fun updatePlayPauseIcon() {
        binding.playPauseButton.setImageResource(
            when (playerState) {
                PlayerState.PLAYING -> R.drawable.ic_pause
                else -> R.drawable.ic_play
            }
        )
    }

    private fun startProgressAnimation(duration: Long, animSession: Long = sessionId) {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(currentProgress, 360f).apply {
            setDuration(((1f - (currentProgress / 360f)) * (duration - 500)).toLong())
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                if (sessionId != animSession) return@addUpdateListener
                val angle = animation.animatedValue as Float
                currentProgress = angle
                binding.progressCircle.progress = angle
            }
            doOnEnd {
                if (sessionId == animSession) finishMusic()
            }
            start()
        }
    }

    private fun finishMusic() {
        spotifyAppRemote?.playerApi?.pause()
        progressAnimator?.cancel()
        currentProgress = 0f
        binding.progressCircle.progress = 0f
        playerState = PlayerState.FINISHED
        isProgressRunning = false
        isProgressFinished = true
        updatePlayPauseIcon()
        binding.playPauseButton.visibility = View.VISIBLE
    }

    private fun resetAll() {
        sessionId++    // <-- minden régi callback innentől "lejár"
        stopcamera()
        spotifyAppRemote?.playerApi?.pause()
        progressAnimator?.cancel()
        currentProgress = 0f
        binding.progressCircle.progress = 0f
        lastTrackUri = null

        playerState = PlayerState.STOPPED
        isProgressRunning = false
        isProgressFinished = false

        binding.qrIcon.visibility = View.VISIBLE
        binding.playPauseButton.visibility = View.GONE
        binding.progressCircle.visibility = View.GONE
        binding.previewView.visibility = View.GONE
    }

    // QR + Camera
    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        } else {
            startCamera()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        binding.qrIcon.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analyzer.clearAnalyzer()
            analyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    val scanner = BarcodeScanning.getClient()
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val value = barcode.rawValue
                                if (!value.isNullOrBlank() && value != lastScanned) {
                                    lastScanned = value
                                    val hitsterClassHandle = HitsterClassHandle()
                                    if (hitsterClassHandle.isHitsterLink(value)) {
                                        val songinfo: Songinfo =
                                            hitsterClassHandle.getSpotyURL(this, value)
                                        searchTrackOnSpotyWithArtistAndSong(
                                            songinfo.song,
                                            songinfo.artist
                                        ) { uri ->
                                            if (uri != null) {
                                                binding.previewView.visibility = View.GONE
                                                startMusicAndAnimation(uri)
                                            } else {
                                                Toast.makeText(
                                                    this,
                                                    "Zeneszám nem található",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            stopcamera()
                                        }
                                    } else {
                                        if (isSpotifyUrl(value)) {
                                            binding.previewView.visibility = View.GONE
                                            startMusicAndAnimation(spotifyUrlToUri(value))
                                            stopcamera()
                                        } else if (isValidSpotifyUri(value)) {
                                            binding.previewView.visibility = View.GONE
                                            startMusicAndAnimation(value)
                                            stopcamera()
                                        } else {
                                            stopcamera()
                                            resetAll()
                                            Toast.makeText(
                                                this,
                                                "Nem megfelelő formátum",
                                                Toast.LENGTH_SHORT
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("Scanner", "Hiba: ", it)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                    stopcamera()
                }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                Log.e("Camera", "Hiba a kamera indításkor", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopcamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
        lastScanned = ""
        binding.previewView.visibility = View.GONE
    }

    fun isValidSpotifyUri(uri: String): Boolean {
        // Spotify URI formátum: spotify:type:id
        val regex = Regex("^spotify:(track|album|playlist|artist):[a-zA-Z0-9]+$")
        return regex.matches(uri)
    }

    private fun isSpotifyUrl(url: String): Boolean {
        val regex =
            Regex("^https?://open\\.spotify\\.com/(track|album|playlist|artist)/[a-zA-Z0-9]+")
        return regex.containsMatchIn(url)
    }

    fun spotifyUrlToUri(url: String): String {
        // Először ellenőrzi, hogy Spotify URL-e
        val regex =
            Regex("^https?://open\\.spotify\\.com/(track|album|playlist|artist)/([a-zA-Z0-9]+)")
        val match = regex.find(url)
        return if (match != null) {
            val type = match.groupValues[1]
            val id = match.groupValues[2]
            "spotify:$type:$id"
        } else {
            Toast.makeText(this, "Nem megfelelő formátum", Toast.LENGTH_SHORT)
            resetAll()
            ""
        }
    }

    private fun searchTrackOnSpotyWithArtistAndSong(
        title: String,
        artist: String,
        callback: (String?) -> Unit
    ) {
        val token = accessToken
        if (token.isNullOrEmpty()) {
            callback(null); return
        }

        Thread {
            val client = OkHttpClient()
            try {
                // 1) első próbálkozás: track:"title" artist:"artist"
                val uri = querySpotifyForUri(client, token, title, artist)
                // 2) ha nincs találat: swap → track:"artist" artist:"title"
                val result = uri ?: querySpotifyForUri(client, token, artist, title)
                runOnUiThread { callback(result) }
            } catch (t: Throwable) {
                t.printStackTrace()
                runOnUiThread { callback(null) }
            }
        }.start()
    }

    private fun querySpotifyForUri(
        client: OkHttpClient,
        token: String,
        trackTitle: String,
        artistName: String
    ): String? {
        val safeTitle = trackTitle.replace("\"", "\\\"")
        val safeArtist = artistName.replace("\"", "\\\"")
        val query = "track:\"$safeTitle\" artist:\"$safeArtist\""
        val url = "https://api.spotify.com/v1/search?q=${Uri.encode(query)}&type=track&limit=1"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val items = root.optJSONObject("tracks")?.optJSONArray("items") ?: return null
            if (items.length() == 0) return null
            return items.getJSONObject(0).optString("uri", null)
        }
    }

    // Spotify SDK lifecycle, auth, stb.
    override fun onStart() {
        super.onStart()
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf("app-remote-control", "streaming"))
            .build()
        AuthorizationClient.openLoginActivity(this, 1337, request)
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == 1337) {
            val response = AuthorizationClient.getResponse(resultCode, intent)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    accessToken = response.accessToken
                    Log.d("Auth", "Access token: ${response.accessToken}")
                }

                AuthorizationResponse.Type.ERROR -> {
                    Log.e("Auth", "Error: ${response.error}")
                }

                else -> {
                    Log.d("Auth", "Cancelled or unknown")
                }
            }
        }
    }


    private fun showThemePicker() {
        val options = ThemeOption.entries.toTypedArray()
        val labels = options.map { it.name }.toTypedArray()

        val materialCtx = ContextThemeWrapper(this, R.style.ForceMaterial3DialogWrapper)

        val titleView = TextView(materialCtx).apply {
            text = "Válassz témát"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        MaterialAlertDialogBuilder(materialCtx, currentTheme.dialogTheme)
            .setCustomTitle(titleView)
            .setItems(labels) { d, which ->
                val chosen = options[which]
                lifecycleScope.launch {
                    themeStore.setTheme(chosen)
                    currentTheme = chosen
                    ThemeManager.currentTheme = currentTheme
                    ThemeManager.applyTheme(this@MainActivity, chosen)
                }
                d.dismiss()
            }
            .show()

    }

    override fun onPause() {
        super.onPause()
        spotifyAppRemote?.playerApi?.pause()
        progressAnimator?.pause()
        if (playerState == PlayerState.PLAYING) {
            playerState = PlayerState.PAUSED
            updatePlayPauseIcon()
        }
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
    }
}
