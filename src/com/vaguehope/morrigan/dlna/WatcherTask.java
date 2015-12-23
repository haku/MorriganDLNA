package com.vaguehope.morrigan.dlna;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.model.TransportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.player.Player.PlayerEventListener;

final class WatcherTask implements Runnable {

	private static final int COUNTS_AS_STARTED_SECONDS = 5;
	private static final Logger LOG = LoggerFactory.getLogger(WatcherTask.class);

	public static WatcherTask schedule (
			final ScheduledExecutorService scheduledExecutor,
			final String uriToWatch,
			final AtomicReference<String> currentUri,
			final AvTransport avTransport,
			final PlayerEventListener listener,
			final Runnable onStartOfTrack, final Runnable onEndOfTrack
			) {
		final WatcherTask task = new WatcherTask(uriToWatch, currentUri, avTransport, listener, onStartOfTrack, onEndOfTrack);
		final ScheduledFuture<?> scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(task, 1, 1, TimeUnit.SECONDS);
		task.setFuture(scheduledFuture);
		return task;
	}

	private final String uriToWatch;
	private final AtomicReference<String> currentUri;
	private final AvTransport avTransport;
	private final PlayerEventListener listener;
	private final Runnable onStartOfTrack;
	private final Runnable onEndOfTrack;

	private final AtomicBoolean trackStarted = new AtomicBoolean(false);
	private final AtomicBoolean trackEnded = new AtomicBoolean(false);

	private ScheduledFuture<?> scheduledFuture;
	private volatile long lastElapsedSeconds = -1;
	private volatile long lastDurationSeconds = -1;

	private WatcherTask (final String uriToWatch, final AtomicReference<String> currentUri, final AvTransport avTransport,
			final PlayerEventListener listener, final Runnable onStartOfTrack, final Runnable onEndOfTrack) {
		this.uriToWatch = uriToWatch;
		this.currentUri = currentUri;
		this.avTransport = avTransport;
		this.listener = listener;
		this.onStartOfTrack = onStartOfTrack;
		this.onEndOfTrack = onEndOfTrack;
	}

	public void cancel () {
		if (this.scheduledFuture == null) return;
		this.scheduledFuture.cancel(false);
		this.scheduledFuture = null;
	}

	@Override
	public void run () {
		if (this.trackEnded.get()) {
			cancel();
			LOG.info("Watcher cancelled; track ended.");
			return;
		}

		final String uri = this.currentUri.get();
		if (!this.uriToWatch.equals(uri)) { // Player is playing a different track.
			cancel();
			LOG.info("Watcher cancelled; player's currentUri changed to: {}.", uri);
			return;
		}

		// Basically, does it look like its been playing OK for a bit?
		final boolean probabblyBeenPlayingOk =
				this.lastDurationSeconds > 0
				? (this.lastElapsedSeconds / (double) this.lastDurationSeconds) > 0.9
				: this.lastElapsedSeconds > 30;

		final MediaInfo mi = this.avTransport.getMediaInfo();
		final String remoteUri = mi.getCurrentURI();
		if (remoteUri == null && probabblyBeenPlayingOk) {
			LOG.info("Probably finished: " + uri);
			callEndOfTrack();
			cancel();
			return;
		}
		// Cancelled /
		if (!uri.equals(remoteUri)) { // Renderer is playing a different track.
			this.listener.currentItemChanged(null); // TODO parse currentURIMetadata and create mock item with track title?
			cancel();
			LOG.info("Watcher cancelled; renderer's currentUri changed to: {}.", remoteUri);
			return;
		}

		final TransportInfo ti = this.avTransport.getTransportInfo();
		this.listener.playStateChanged(DlnaPlayer.transportIntoToPlayState(ti));
		if (ti == null) {
			cancel();
			LOG.info("Watcher cancelled; renderer returned null transport info.");
			return;
		}

		if (ti.getCurrentTransportStatus() != TransportStatus.OK) {
			LOG.warn("Current transport status: {}", ti.getCurrentTransportStatus());
			return;
		}

		if (ti.getCurrentTransportState() == TransportState.STOPPED
				|| ti.getCurrentTransportState() == TransportState.NO_MEDIA_PRESENT) {
			if (probabblyBeenPlayingOk) {
				LOG.info("Finished: " + uri);
				callEndOfTrack();
			}
			cancel();
		}

		final PositionInfo pi = this.avTransport.getPositionInfo();
		if (pi == null) {
			this.listener.positionChanged(-1, -1);
		}
		else {
			final long elapsedSeconds = pi.getTrackElapsedSeconds();
			final long durationSeconds = pi.getTrackDurationSeconds();

			if (elapsedSeconds > 0) this.lastElapsedSeconds = elapsedSeconds;
			if (durationSeconds > 0) this.lastDurationSeconds = durationSeconds;

			this.listener.positionChanged(elapsedSeconds, (int) durationSeconds);
			if (!this.trackStarted.get() && elapsedSeconds > COUNTS_AS_STARTED_SECONDS) callStartOfTrack();

			// TODO consider writing duration back to DB.
		}
	}

	private void callStartOfTrack () {
		if (this.trackStarted.compareAndSet(false, true)) this.onStartOfTrack.run();
	}

	private void callEndOfTrack () {
		callStartOfTrack();
		if (this.trackEnded.compareAndSet(false, true)) this.onEndOfTrack.run();
	}

	private void setFuture (final ScheduledFuture<?> scheduledFuture) {
		this.scheduledFuture = scheduledFuture;
	}

}
