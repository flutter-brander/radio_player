/*
 *  RadioPlayerService.kt
 *
 *  Created by Ilia Chirkunov <xc@yar.net> on 30.12.2020.
 */

package com.cheebeez.radio_player

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cheebeez.radio_player.models.Station
import com.cheebeez.radio_player.models.StationMetadata
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.icy.IcyInfo
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit


/** Service for plays streaming audio content using ExoPlayer. */
class RadioPlayerService : MediaBrowserServiceCompat(), Player.Listener {

    companion object {
        const val TAG = "RadioPlayerServiceLog"
    }

    private lateinit var stationsRepository: StationsRepository

    // Timer
    private var localBinder = LocalBinder()
    private var isForegroundService = false
    private var workManager = WorkManager.getInstance(this)
    private lateinit var stopPlayerJobId: UUID

    // Common
    lateinit var context: Context
    private val localBroadcastManager: LocalBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(this)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())


    // Player data
    private var selectedStation: Station? = null
    private var stations: List<Station> = mutableListOf()
    private var playbackState = Player.STATE_IDLE

    // Player controllers
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private var mediaSession: MediaSessionCompat? = null
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }

    // Metadata
    private var metadata: StationMetadata? = null
    private var stationArtwork: Bitmap? = null
    private var metadataArtwork: Bitmap? = null

    inner class LocalBinder : Binder() {
        fun getService(): RadioPlayerService = this@RadioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (SERVICE_INTERFACE == intent?.action) {
            super.onBind(intent)
        } else {
            return localBinder
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        createNotificationManager()
        stationsRepository = StationsRepository(this)
        player.setRepeatMode(Player.REPEAT_MODE_ALL)
        player.addListener(this)
        Log.d(TAG, "onCreate: ")
    }

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(K.BROWSER_ROOT_PATH, null)
    }

    override fun onLoadChildren(
        parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        var stations = this.stations;
        if (stations.isEmpty()) {
            result.detach()
            stations = stationsRepository.getStations()
            setStations(stations, false)
        }

        // When stations is empty
        if (stations.isEmpty()) {
            result.sendResult(listOf<MediaBrowserCompat.MediaItem>().toMutableList())
            return;
        }

        // Convert to list result
        var mediaItems = stations.map { station ->
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().setMediaId("${station.id}").setTitle(station.title)
                    .setIconUri(Uri.parse(station.coverUrl)).build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
        }
        result.sendResult(mediaItems.toMutableList())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession?.release()
        playerNotificationManager?.setPlayer(null)
        player.release()
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    fun play() {
        if (player.mediaItemCount == 0) return;
        
        player.seekTo(0)
        player.playWhenReady = true
        Log.d(TAG, "play: ")
    }

    fun stop() {
        Log.d(TAG, "stop: ")
        player.playWhenReady = false
        player.stop()
    }

    fun pause() {
        Log.d(TAG, "pause: ")
        player.playWhenReady = false
    }

    fun setStations(stations: List<Station>, notifyAndroidAuto: Boolean = true) {
        try {
            this.stations = stations
            var mediaItems = stations.map { station -> MediaItem.fromUri(station.streamUrl) }
            player.clearMediaItems()
            player.setMediaItems(mediaItems)
            if (stations.isNotEmpty()) {
                selectStation(selectedStation?.id ?: stations.first().id)
            }

            if (notifyAndroidAuto) {
                stationsRepository.saveStations(stations)
                notifyChildrenChanged(K.BROWSER_ROOT_PATH)
            }
        } catch (er: Exception) {
            Log.d(TAG, "setStations error: $er")
        }
    }

    fun selectStation(stationId: Int) {
        try {
            Log.d(TAG, "selectStation: $stationId")
            if (stations.isEmpty()) return;
            var stationIndex = stations.indexOfFirst { it.id == stationId }
            if (stationIndex == -1) {
                Log.e(TAG, "selectStation: Station is not found")
                return;
            }
            selectedStation = stations[stationIndex];
            player.seekTo(stationIndex, 0)
            stationArtwork = null
            metadataArtwork = null
            metadata = null

            setMetadata(
                track = "",
                title = selectedStation!!.title,
                imageUrl = selectedStation!!.coverUrl,
                isStation = true
            );

            // Notify flutter side about change station
            val playerEventIntent = Intent(K.ACTION_STATE_PLAYER_EVENT)
            playerEventIntent.putExtra(
                K.ACTION_STATE_PLAYER_EVENT_EXTRA,
                arrayListOf(K.CHANGE_STATION_EVENT_NAME, stationId)
            )
            localBroadcastManager.sendBroadcast(playerEventIntent)
            Log.d(TAG, "selectStation (cancel): $stationId")
        } catch (error: Exception) {
            Log.d(TAG, "selectStation error: $error")
        }

    }

    /** Updates the player's metadata. */
    private fun setMetadata(
        title: String? = null,
        track: String = "",
        imageUrl: String? = null,
        isStation: Boolean = false
    ) {
        var metadataTitle = title ?: selectedStation?.title;
        var metadataTrack = track;
        downloadImage(imageUrl) { image ->
            if (isStation) {
                stationArtwork = image
            } else {
                metadataArtwork = image
            }
            var artwork = if (isStation) stationArtwork else metadataArtwork ?: stationArtwork;
            val mdc = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadataTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadataTrack)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, metadataTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, metadataTrack)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork).build()
            mediaSession?.setMetadata(mdc)
            playerNotificationManager?.invalidate()
        }
    }

    fun addToControlCenter() {
        playerNotificationManager.setPlayer(player);
        playerNotificationManager?.setUsePlayPauseActions(true)
        playerNotificationManager?.setUseNextAction(true)
        playerNotificationManager?.setUseNextActionInCompactView(true)
        playerNotificationManager?.setUsePreviousAction(true)
        playerNotificationManager?.setUsePreviousActionInCompactView(true)
        playerNotificationManager?.invalidate()
    }

    fun removeFromControlCenter() {
        player.stop()
        playerNotificationManager.setPlayer(null);
        playerNotificationManager?.setUsePlayPauseActions(false)
        playerNotificationManager?.setUseNextAction(false)
        playerNotificationManager?.setUseNextActionInCompactView(false)
        playerNotificationManager?.setUsePreviousAction(false)
        playerNotificationManager?.setUsePreviousActionInCompactView(false)
        playerNotificationManager?.invalidate()
    }

    /** Start timer job for delayed player stop **/
    fun startTimer(timerDuration: Double) {
        val countDownInterval = timerDuration.toLong() * 1000
        stopPlayerJobId = UUID.randomUUID()
        val stopPlayerJob = OneTimeWorkRequestBuilder<StopPlayerJob>().setInitialDelay(
            countDownInterval, TimeUnit.MILLISECONDS
        ).setId(stopPlayerJobId).build()
        workManager.beginWith(stopPlayerJob).enqueue()
    }

    /** Cancel timer job for delayed player stop **/
    fun cancelTimer() {
        workManager.cancelWorkById(stopPlayerJobId)
    }

    /** Gets information about player's playing state **/
    fun isPlaying(): Boolean = player.playWhenReady

    /** Creates a notification manager for background playback. */
    private fun createNotificationManager() {
        // Setup media session
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val pendingIntent =
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSessionCompat(
            baseContext, K.MEDIA_SESSION_COMPAT_TAG, null, pendingIntent
        )
        mediaSession?.isActive = true
        val mediaSessionConnector = MediaSessionConnector(mediaSession!!)
        val queueNavigator: TimelineQueueNavigator =
            object : TimelineQueueNavigator(mediaSession!!) {
                override fun getMediaDescription(
                    player: Player, windowIndex: Int
                ): MediaDescriptionCompat {
                    var station = stations[windowIndex];
                    return MediaDescriptionCompat.Builder().setMediaId("${station.id}")
                        .setTitle(station.title).setIconUri(
                            Uri.parse(station.coverUrl)
                        ).build()
                }
            }
        mediaSessionConnector.setQueueNavigator(queueNavigator)
        mediaSessionConnector.setPlayer(player)
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            ).build()
        )
        mediaSession?.setFlags(
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        )
        mediaSession?.setCallback(MediaSessionCallback())
        sessionToken = mediaSession?.sessionToken

        // Setup audio focus
        val audioAttributes: AudioAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()

        player.setAudioAttributes(audioAttributes, true);

        // Setup notification manager.
        val mediaDescriptionAdapter = object : MediaDescriptionAdapter {
            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val notificationIntent = Intent()
                notificationIntent.setClassName(
                    context.packageName, "${context.packageName}.MainActivity"
                )
                return PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            override fun getCurrentLargeIcon(player: Player, callback: BitmapCallback): Bitmap? {
                return metadataArtwork ?: stationArtwork;
            }

            override fun getCurrentContentTitle(player: Player): String {
                return selectedStation?.title ?: "";
            }

            override fun getCurrentContentText(player: Player): String? {
                return metadata?.trackTitle ?: "";
            }
        }

        val notificationListener = object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(
                notificationId: Int, notification: Notification, ongoing: Boolean
            ) {
                if (ongoing && !isForegroundService) {
                    startForeground(notificationId, notification)
                    isForegroundService = true
                }
            }

            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopForeground(true)
                isForegroundService = false
                stopSelf()
            }
        }

        playerNotificationManager =
            PlayerNotificationManager.Builder(this, K.NOTIFICATION_ID, K.NOTIFICATION_CHANNEL_ID)
                .setChannelNameResourceId(R.string.channel_name)
                .setMediaDescriptionAdapter(mediaDescriptionAdapter)
                .setNotificationListener(notificationListener).build().apply {
                    setUsePlayPauseActions(true)
                    setUsePreviousAction(true)
                    setUseNextAction(true)
                    setUseNextActionInCompactView(true)
                    setUsePreviousActionInCompactView(true)
                    setPlayer(player)
                    mediaSession?.let { setMediaSessionToken(it.sessionToken) }
                }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
            var stationId = mediaId?.toInt()
            if (stationId == null) {
                stationId = stations.first().id;
            }
            selectStation(stationId = stationId)
            play();
        }

        override fun onPlay() = play()

        override fun onPause() = pause()

        override fun onSkipToQueueItem(index: Long) = selectStation(stations[index.toInt()].id)

        override fun onSkipToNext() {
            val currentIndex = stations.indexOfFirst { it.id == selectedStation?.id }
            if (currentIndex != -1) {
                val nextIndex = (currentIndex + 1) % stations.size
                selectStation(stations[nextIndex].id)
            }
        }

        override fun onSkipToPrevious() {
            val currentIndex = stations.indexOfFirst { it.id == selectedStation?.id }
            if (currentIndex != -1) {
                val stationsCount = stations.size
                val previousIndex = (currentIndex - 1 + stationsCount) % stationsCount
                selectStation(stations[previousIndex].id)
            }
        }
    }

    /** Triggers on change index playing. */
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SEEK) {
            val currentIndex = newPosition.mediaItemIndex
            var currentStationIndex = stations.indexOfFirst { it.id == selectedStation?.id }
            if (currentStationIndex == currentIndex) return;
            Log.d(TAG, "Track changed to index: $currentIndex")
            var station = stations[currentIndex]
            selectStation(stationId = station.id);
        }
    }

    /** Triggers on play or pause. */
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)

        if (playbackState == Player.STATE_IDLE && playWhenReady) {
            player.prepare()
        }

        // Notify Flutter side about change playing state
        val playerEventIntent = Intent(K.ACTION_STATE_PLAYER_EVENT)
        val event = if (playWhenReady) "play" else "pause"
        playerEventIntent.putExtra(K.ACTION_STATE_PLAYER_EVENT_EXTRA, arrayListOf(event))
        localBroadcastManager.sendBroadcast(playerEventIntent)

        // Update metadata
        if (playWhenReady) {
            setMetadata(
                imageUrl = metadata?.cover,
                track = metadata?.trackTitle ?: "",
            )
        } else {
            setMetadata(
                title = selectedStation?.title,
                imageUrl = selectedStation?.coverUrl,
            )
        }
    }

    /** Triggers when player state changes. */
    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState = state
    }

    /** Triggers when metadata comes from the stream. */
    override fun onMetadata(rawMetadata: Metadata) {
        try {
            if (rawMetadata[0] !is IcyInfo) return
            val icyInfo: IcyInfo = rawMetadata[0] as IcyInfo
            val json: String = icyInfo.title ?: return
            Log.d(TAG, "onMetadata: $json")
            if (json.isEmpty()) return
            metadata = StationMetadata.fromJson(json)
            if (metadata?.isDefault == true) {
                metadata?.cover = selectedStation?.coverUrl
            }

            // Send the metadata to the Flutter side.
            val metadataIntent = Intent(K.ACTION_NEW_METADATA)
            metadataIntent.putStringArrayListExtra(
                K.ACTION_NEW_METADATA_EXTRA, arrayListOf(
                    metadata?.title, metadata?.artist, metadata?.cover
                ) as ArrayList<String>
            )
            localBroadcastManager.sendBroadcast(metadataIntent)

            // Update metadata
            if (isPlaying()) {
                setMetadata(
                    imageUrl = metadata?.cover,
                    track = metadata?.trackTitle ?: "",
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "onMetadata error:$error")
        }

    }

    private fun downloadImage(value: String?, callback: (Bitmap?) -> Unit) {
        if (value.isNullOrBlank()) {
            callback(null)
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL(value)
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                BitmapFactory.decodeStream(connection.getInputStream())?.let { originalBitmap ->
                    val resizedBitmap = Bitmap.createScaledBitmap(
                        originalBitmap, 512, 512, true
                    )
                    withContext(Dispatchers.Main) {
                        callback(resizedBitmap)
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    class StopPlayerJob(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
        override fun doWork(): Result {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as RadioPlayerService.LocalBinder
                    val radioPlayerService = binder.getService()
                    radioPlayerService.stop()
                }

                override fun onServiceDisconnected(arg0: ComponentName) {}
            }
            ctx.bindService(
                Intent(ctx, RadioPlayerService::class.java), connection, Context.BIND_AUTO_CREATE
            )
            return Result.success()
        }
    }
}
