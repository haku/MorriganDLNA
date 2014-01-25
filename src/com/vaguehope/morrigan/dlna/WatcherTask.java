package com.vaguehope.morrigan.dlna;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.teleal.cling.support.model.MediaInfo;
import org.teleal.cling.support.model.TransportInfo;
import org.teleal.cling.support.model.TransportState;
import org.teleal.cling.support.model.TransportStatus;

class WatcherTask implements Runnable {

	public static WatcherTask schedule (final ScheduledExecutorService scheduledExecutor, final String uriToWatch, final AtomicReference<String> currentUri, final AvTransport avTransport, final Runnable onEndOfTrack) {
		final WatcherTask task = new WatcherTask(uriToWatch, currentUri, avTransport, onEndOfTrack);
		final ScheduledFuture<?> scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(task, 1, 1, TimeUnit.SECONDS);
		task.setFuture(scheduledFuture);
		return task;
	}

	private final String uriToWatch;
	private final AtomicReference<String> currentUri;
	private final AvTransport avTransport;
	private final Runnable onEndOfTrack;
	private final AtomicBoolean trackEnded = new AtomicBoolean(false);
	private ScheduledFuture<?> scheduledFuture;

	private WatcherTask (final String uriToWatch, final AtomicReference<String> currentUri, final AvTransport avTransport, final Runnable onEndOfTrack) {
		this.uriToWatch = uriToWatch;
		this.currentUri = currentUri;
		this.avTransport = avTransport;
		this.onEndOfTrack = onEndOfTrack;
	}

	public void cancel () {
		if (this.scheduledFuture == null) return;
		this.scheduledFuture.cancel(false);
		this.scheduledFuture = null;
	}

	@Override
	public void run () {
		final String uri = this.currentUri.get();
		if (!this.uriToWatch.equals(uri)) { // Player is playing a different track.
			cancel();
			return;
		}

		final MediaInfo mi = this.avTransport.getMediaInfo();
		final String remoteUri = mi.getCurrentURI();
		if (!uri.equals(remoteUri)) { // Renderer is playing a different track.
			cancel();
			return;
		}

		final TransportInfo ti = this.avTransport.getTransportInfo();
		if (ti == null) {
			System.err.println("Failed to read transport info.");
			cancel();
			return;
		}
		if (ti.getCurrentTransportStatus() == TransportStatus.OK && ti.getCurrentTransportState() == TransportState.STOPPED) {
			System.err.println("finished: " + uri);
			if (this.trackEnded.compareAndSet(false, true)) this.onEndOfTrack.run();
			cancel();
		}
	}

	private void setFuture (final ScheduledFuture<?> scheduledFuture) {
		this.scheduledFuture = scheduledFuture;
	}

}
