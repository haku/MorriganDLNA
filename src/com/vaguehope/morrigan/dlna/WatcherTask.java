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
			return;
		}

		final String uri = this.currentUri.get();
		if (!this.uriToWatch.equals(uri)) { // Player is playing a different track.
			cancel();
			return;
		}

		final MediaInfo mi = this.avTransport.getMediaInfo();
		final String remoteUri = mi.getCurrentURI();
		if (!uri.equals(remoteUri)) { // Renderer is playing a different track.
			this.listener.currentItemChanged(null); // TODO parse currentURIMetadata and create mock item with track title?
			cancel();
			return;
		}

		final TransportInfo ti = this.avTransport.getTransportInfo();
		this.listener.playStateChanged(DlnaPlayer.transportIntoToPlayState(ti));
		if (ti == null) {
			cancel();
			return;
		}

		if (ti.getCurrentTransportStatus() != TransportStatus.OK) return;

		if (ti.getCurrentTransportState() == TransportState.STOPPED) {
			LOG.info("finished: " + uri);
			callEndOfTrack();
			cancel();
		}

		final PositionInfo pi = this.avTransport.getPositionInfo();
		if (pi == null) {
			this.listener.positionChanged(-1, -1);
		}
		else {
			this.listener.positionChanged(pi.getTrackElapsedSeconds(), (int) pi.getTrackDurationSeconds());
			if (!this.trackStarted.get() && pi.getTrackElapsedSeconds() > COUNTS_AS_STARTED_SECONDS) callStartOfTrack();
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
