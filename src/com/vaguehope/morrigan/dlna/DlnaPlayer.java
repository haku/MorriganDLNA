package com.vaguehope.morrigan.dlna;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.support.model.PositionInfo;
import org.teleal.cling.support.model.TransportInfo;
import org.teleal.cling.support.model.TransportStatus;

import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.model.Register;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.DefaultPlayerQueue;
import com.vaguehope.morrigan.player.OrderHelper;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.server.ServerConfig;
import com.vaguehope.morrigan.util.ErrorHelper;

public class DlnaPlayer implements Player {

	private final int playerId;
	private final String playerName;
	private final AvTransport avTransport;
	private final Register<Player> register;
	private final MediaServer mediaServer;
	private final ScheduledExecutorService scheduledExecutor;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final Object[] loadLock = new Object[] {};
	private final AtomicReference<PlaybackOrder> playbackOrder = new AtomicReference<PlaybackOrder>(PlaybackOrder.SEQUENTIAL);
	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();
	private final AtomicReference<String> currentUri = new AtomicReference<String>();
	private final PlayerQueue queue;
	private final AtomicReference<WatcherTask> watcher = new AtomicReference<WatcherTask>(null);

	public DlnaPlayer (final int id, final PlayerRegister register, final ControlPoint controlPoint, final RemoteService avTransportSvc, final MediaServer mediaServer, final ScheduledExecutorService scheduledExecutor) {
		this.playerId = id;
		this.playerName = avTransportSvc.getDevice().getDetails().getFriendlyName();
		this.register = register;
		this.avTransport = new AvTransport(controlPoint, avTransportSvc);
		this.mediaServer = mediaServer;
		this.scheduledExecutor = scheduledExecutor;
		this.queue = new DefaultPlayerQueue();
		try {
			this.playbackOrder.set(new ServerConfig().getPlaybackOrder()); // TODO share this.
		}
		catch (final IOException e) {
			System.err.println("Failed to read server config: " + ErrorHelper.getCauseTrace(e));
		}
	}

	private void checkAlive () {
		if (!this.alive.get()) throw new IllegalStateException("Player is disposed: " + toString());
	}

	@Override
	public String toString () {
		return new StringBuilder("DlnaPlayer{")
				.append("id=").append(getId())
				.append(" name=").append(getName())
				.append(" order=").append(getPlaybackOrder())
				.append("}").toString();
	}

	@Override
	public int getId () {
		return this.playerId;
	}

	@Override
	public String getName () {
		return this.playerName;
	}

	@Override
	public void dispose () {
		if (this.alive.compareAndSet(true, false)) {
			this.register.unregister(this);
			System.err.println("Disposed player: " + toString());
		}
	}

	@Override
	public boolean isPlaybackEngineReady () {
		return this.alive.get();
	}

	@Override
	public void loadAndStartPlaying (final IMediaTrackList<? extends IMediaTrack> list) {
		final IMediaTrack nextTrack = OrderHelper.getNextTrack(list, null, this.playbackOrder.get());
		loadAndStartPlaying(list, nextTrack);
	}

	@Override
	public void loadAndStartPlaying (final IMediaTrackList<? extends IMediaTrack> list, final IMediaTrack track) {
		if (track == null) throw new IllegalArgumentException("track must not be null.");
		loadAndStartPlaying(new PlayItem(list, track));
	}

	@Override
	public void loadAndStartPlaying (final PlayItem item) {
		checkAlive();
		try {
			final File file = new File(item.item.getFilepath());
			if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
			final String id = MediaServer.idForFile(file);
			final String uri = this.mediaServer.uriForFile(id, file);
			final File coverArt = item.item.findCoverArt();
			final String coverArtUri = coverArt != null ? this.mediaServer.uriForFile(coverArt) : null;
			synchronized (this.loadLock) {
				System.err.println("loading: " + id);
				stopPlaying();
				this.avTransport.setUri(id, uri, item.item.getTitle(), file, coverArtUri);
				this.currentUri.set(uri);
				this.avTransport.play();
				this.currentItem.set(item);
				startWatcher(uri, item);
			}
		}
		catch (final Exception e) {
			System.err.println("Failed to start playback: " + ErrorHelper.getCauseTrace(e));
		}
	}

	private void startWatcher (final String uri, final PlayItem item) {
		final WatcherTask oldWatcher = this.watcher.getAndSet(null);
		if (oldWatcher != null) oldWatcher.cancel();

		final WatcherTask task = WatcherTask.schedule(this.scheduledExecutor, uri, this.currentUri, this.avTransport,
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
		final PlayItem queueItem = this.queue.takeFromQueue();
		if (queueItem != null) return queueItem;

		final PlayItem lastItem = getCurrentItem();
		if (lastItem == null || lastItem.list == null) return null;

		final IMediaTrack nextTrack = OrderHelper.getNextTrack(lastItem.list, lastItem.item, this.playbackOrder.get());
		if (nextTrack != null) return new PlayItem(lastItem.list, nextTrack);

		return null;
	}

	@Override
	public PlayState getPlayState () {
		checkAlive();
		final TransportInfo ti = this.avTransport.getTransportInfo();
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

	@Override
	public PlayItem getCurrentItem () {
		return this.currentItem.get();
	}

	@Override
	public IMediaTrackList<? extends IMediaTrack> getCurrentList () {
		final PlayItem item = this.currentItem.get();
		return item == null ? null : item.list;
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
	public PlaybackOrder getPlaybackOrder () {
		return this.playbackOrder.get();
	}

	@Override
	public void setPlaybackOrder (final PlaybackOrder order) {
		this.playbackOrder.set(order);
	}

	@Override
	public List<PlayItem> getHistory () {
		return Collections.emptyList();
	}

	@Override
	public PlayerQueue getQueue () {
		return this.queue;
	}

	@Override
	public Map<Integer, String> getMonitors () {
		return Collections.emptyMap();
	}

	@Override
	public void goFullscreen (final int monitor) {
		// Should never be called as getMontors() always returns nothing.
	}

}
