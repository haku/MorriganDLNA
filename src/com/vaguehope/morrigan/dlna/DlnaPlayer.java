package com.vaguehope.morrigan.dlna;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import com.vaguehope.morrigan.model.media.DurationData;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.OrderHelper;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.util.ErrorHelper;

public class DlnaPlayer implements Player {

	private final int playerId;
	private final String playerName;
	private final AvTransport avTransport;
	private final Register<Player> register;
	private final MediaServer mediaServer;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final AtomicReference<PlaybackOrder> playbackOrder = new AtomicReference<PlaybackOrder>(PlaybackOrder.SEQUENTIAL);
	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();

	public DlnaPlayer (final int id, final RemoteService avTransportSvc, final PlayerRegister register, final ControlPoint controlPoint, final MediaServer mediaServer) {
		this.playerId = id;
		this.playerName = avTransportSvc.getDevice().getDetails().getFriendlyName();
		this.register = register;
		this.avTransport = new AvTransport(controlPoint, avTransportSvc);
		this.mediaServer = mediaServer;
	}

	private void checkAlive () {
		if (!this.alive.get()) throw new IllegalStateException("Player is disposed: " + toString());
	}

	@Override
	public String toString () {
		return new StringBuilder("DlnaPlayer{")
				.append("id=").append(getId())
				.append(" name=").append(getName())
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
		if (track == null) throw new NullPointerException();
		loadAndStartPlaying(new PlayItem(list, track));
	}

	@Override
	public void loadAndStartPlaying (final PlayItem item) {
		checkAlive();
		try {
			final File file = new File(item.item.getFilepath());
			if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
			System.err.println("Loading item: " + file.getAbsolutePath());
			stopPlaying();
			this.avTransport.setUri(this.mediaServer.uriForFile(file));
			this.avTransport.play();
			this.currentItem.set(item);
		}
		catch (final Exception e) {
			System.err.println("Failed to start playback: " + ErrorHelper.getCauseTrace(e));
		}
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
		System.err.println("TODO: next");
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
		checkAlive();
		return this.currentItem.get();
	}

	@Override
	public IMediaTrackList<? extends IMediaTrack> getCurrentList () {
		checkAlive();
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
		System.err.println("TODO: seek: " + d);
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
	public void addToQueue (final PlayItem item) {
		System.err.println("TODO: add to queue: " + item);
	}

	@Override
	public void addToQueue (final List<PlayItem> items) {
		System.err.println("TODO: add to queue: " + items);
	}

	@Override
	public void removeFromQueue (final PlayItem item) {
		System.err.println("TODO: remove from queue: " + item);
	}

	@Override
	public void clearQueue () {
		System.err.println("TODO: clear queue");
	}

	@Override
	public void moveInQueue (final List<PlayItem> items, final boolean moveDown) {
		System.err.println("TODO: move in queue");
	}

	@Override
	public void moveInQueueEnd (final List<PlayItem> items, final boolean toBottom) {
		System.err.println("TODO: move to end of queue");
	}

	@Override
	public List<PlayItem> getQueueList () {
		return Collections.emptyList();
	}

	@Override
	public void setQueueList (final List<PlayItem> items) {
		System.err.println("TODO: set queue list");
	}

	@Override
	public void shuffleQueue () {
		System.err.println("TODO: shuffle queue");
	}

	@Override
	public DurationData getQueueTotalDuration () {
		return new DurationData() {
			@Override
			public boolean isComplete () {
				return true;
			}

			@Override
			public long getDuration () {
				return 0;
			}
		};
	}

	@Override
	public PlayItem getQueueItemById (final int id) {
		return null;
	}

	@Override
	public Map<Integer, String> getMonitors () {
		return Collections.emptyMap();
	}

	@Override
	public void goFullscreen (final int monitor) {
		System.err.println("TODO: Go full screen on monitor " + monitor + ".");
	}

}
