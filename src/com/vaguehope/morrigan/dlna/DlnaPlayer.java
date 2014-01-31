package com.vaguehope.morrigan.dlna;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.support.model.PositionInfo;
import org.teleal.cling.support.model.TransportInfo;
import org.teleal.cling.support.model.TransportStatus;

import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.AbstractPlayer;
import com.vaguehope.morrigan.player.OrderHelper;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.server.ServerConfig;
import com.vaguehope.morrigan.util.ErrorHelper;

public class DlnaPlayer extends AbstractPlayer {

	private final AvTransport avTransport;
	private final MediaServer mediaServer;
	private final ScheduledExecutorService scheduledExecutor;

	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();
	private final AtomicReference<String> currentUri = new AtomicReference<String>();
	private final AtomicReference<WatcherTask> watcher = new AtomicReference<WatcherTask>(null);

	public DlnaPlayer (final int id, final PlayerRegister register, final ControlPoint controlPoint, final RemoteService avTransportSvc, final MediaServer mediaServer, final ScheduledExecutorService scheduledExecutor) {
		super(id, avTransportSvc.getDevice().getDetails().getFriendlyName(), register);
		this.avTransport = new AvTransport(controlPoint, avTransportSvc);
		this.mediaServer = mediaServer;
		this.scheduledExecutor = scheduledExecutor;
		try {
			setPlaybackOrder(new ServerConfig().getPlaybackOrder()); // TODO share this.
		}
		catch (final IOException e) {
			System.err.println("Failed to read server config: " + ErrorHelper.getCauseTrace(e));
		}
	}

	@Override
	protected void onDispose () {
		System.err.println("Disposed player: " + toString());
	}

	@Override
	public boolean isPlaybackEngineReady () {
		return !isDisposed();
	}

	@Override
	protected void loadAndStartPlaying (final PlayItem item, final File file) throws Exception {
		final String id = MediaServer.idForFile(file);
		final String uri = this.mediaServer.uriForFile(id, file);
		final File coverArt = item.getTrack().findCoverArt();
		final String coverArtUri = coverArt != null ? this.mediaServer.uriForFile(coverArt) : null;
		System.err.println("loading: " + id);
		stopPlaying();
		this.avTransport.setUri(id, uri, item.getTrack().getTitle(), file, coverArtUri);
		this.currentUri.set(uri);
		this.avTransport.play();
		this.currentItem.set(item);
		startWatcher(uri, item);
	}

	private void startWatcher (final String uri, final PlayItem item) {
		final WatcherTask oldWatcher = this.watcher.getAndSet(null);
		if (oldWatcher != null) oldWatcher.cancel();

		final WatcherTask task = WatcherTask.schedule(this.scheduledExecutor, uri, this.currentUri, this.avTransport,
				getListeners(),
				new OnTrackStarted(this, item), new OnTrackComplete(this, item));
		if (!this.watcher.compareAndSet(null, task)) {
			task.cancel();
			System.err.println("Failed to configure watcher as another got there first.");
		}
	}

	protected void recordTrackStarted (final PlayItem item) {
		this.scheduledExecutor.execute(new RecordTrackStarted(item));
	}

	protected void recordTrackCompleted (final PlayItem item) {
		this.scheduledExecutor.execute(new RecordTrackCompleted(item));
	}

	@Override
	public void pausePlaying () {
		checkAlive();
		if (getPlayState() == PlayState.PAUSED) {
			this.avTransport.play();
		}
		else {
			this.avTransport.pause();
		}
	}

	@Override
	public void stopPlaying () {
		checkAlive();
		try {
			this.avTransport.stop();
		}
		finally {
			this.currentItem.set(null);
		}
	}

	@Override
	public void nextTrack () {
		checkAlive();
		final PlayItem nextItemToPlay = getNextItemToPlay();
		if (nextItemToPlay != null) loadAndStartPlaying(nextItemToPlay);
	}

	private PlayItem getNextItemToPlay () {
		final PlayItem queueItem = this.getQueue().takeFromQueue();
		if (queueItem != null) return queueItem;

		final PlayItem lastItem = getCurrentItem();
		if (lastItem == null || !lastItem.hasList()) return null;

		final IMediaTrack nextTrack = OrderHelper.getNextTrack(lastItem.getList(), lastItem.getTrack(), getPlaybackOrder());
		if (nextTrack != null) return new PlayItem(lastItem.getList(), nextTrack);

		return null;
	}

	@Override
	public PlayState getPlayState () {
		checkAlive();
		final TransportInfo ti = this.avTransport.getTransportInfo();
		return transportIntoToPlayState(ti);
	}

	@Override
	public PlayItem getCurrentItem () {
		return this.currentItem.get();
	}

	@Override
	public IMediaTrackList<? extends IMediaTrack> getCurrentList () {
		final PlayItem item = this.currentItem.get();
		return item == null ? null : item.getList();
	}

	@Override
	public long getCurrentPosition () {
		checkAlive();
		final PositionInfo pi = this.avTransport.getPositionInfo();
		if (pi == null) return 0;
		return pi.getTrackElapsedSeconds();
	}

	@Override
	public int getCurrentTrackDuration () {
		checkAlive();
		final PositionInfo pi = this.avTransport.getPositionInfo();
		if (pi == null) return 0;
		return (int) pi.getTrackDurationSeconds();
	}

	@Override
	public void seekTo (final double d) {
		checkAlive();
		this.avTransport.seek((long) (getCurrentTrackDuration() * d));
	}

	@Override
	public List<PlayItem> getHistory () {
		return Collections.emptyList();
	}

	@Override
	public Map<Integer, String> getMonitors () {
		return Collections.emptyMap();
	}

	@Override
	public void goFullscreen (final int monitor) {
		// Should never be called as getMontors() always returns nothing.
	}

	public static PlayState transportIntoToPlayState (final TransportInfo ti) {
		if (ti == null) return PlayState.STOPPED;
		if (ti.getCurrentTransportStatus() == TransportStatus.OK) {
			switch (ti.getCurrentTransportState()) {
				case PLAYING:
				case RECORDING:
					return PlayState.PLAYING;
				case PAUSED_PLAYBACK:
				case PAUSED_RECORDING:
					return PlayState.PAUSED;
				case TRANSITIONING:
				case CUSTOM:
					return PlayState.LOADING;
				case STOPPED:
				case NO_MEDIA_PRESENT:
					return PlayState.STOPPED;
			}
		}
		return PlayState.STOPPED;
	}

}
