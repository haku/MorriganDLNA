package com.vaguehope.morrigan.dlna;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.support.avtransport.callback.GetPositionInfo;
import org.teleal.cling.support.avtransport.callback.GetTransportInfo;
import org.teleal.cling.support.avtransport.callback.Pause;
import org.teleal.cling.support.avtransport.callback.Play;
import org.teleal.cling.support.avtransport.callback.SetAVTransportURI;
import org.teleal.cling.support.avtransport.callback.Stop;
import org.teleal.cling.support.model.PositionInfo;
import org.teleal.cling.support.model.TransportInfo;
import org.teleal.cling.support.model.TransportStatus;

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

	private static final int ACTION_TIMEOUT_SECONDS = 30;

	private final int playerId;
	private final RemoteService avTransport;
	private final Register<Player> register;
	private final ControlPoint controlPoint;
	private final MediaServer mediaServer;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final AtomicReference<PlaybackOrder> playbackOrder = new AtomicReference<PlaybackOrder>(PlaybackOrder.SEQUENTIAL);
	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();

	public DlnaPlayer (final int id, final RemoteService avTransport, final PlayerRegister register, final ControlPoint controlPoint, final MediaServer mediaServer) {
		this.playerId = id;
		this.avTransport = avTransport;
		this.register = register;
		this.controlPoint = controlPoint;
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
	public void dispose () {
		if (this.alive.compareAndSet(true, false)) {
			this.register.unregister(this);
			System.err.println("Disposed player: " + toString());
		}
	}

	@Override
	public String getName () {
		return this.avTransport.getDevice().getDetails().getFriendlyName();
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

			final String uri = this.mediaServer.uriForFile(file);
			final CountDownLatch uriSet = new CountDownLatch(1);
			this.controlPoint.execute(new SetAVTransportURI(this.avTransport, uri) {
				@Override
				public void success (final ActionInvocation invocation) {
					System.err.println("Set av transport uri: " + uri);
					uriSet.countDown();
				}

				@Override
				public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
					System.err.println("Failed to set av transport uri: " + defaultMsg);
					uriSet.countDown();
				}
			});
			await(uriSet, "Failed to set URI '" + uri + "' on transport '" + this.avTransport + "'.");
			play();

			this.currentItem.set(item);
		}
		catch (final Exception e) {
			System.err.println("Failed to start playback: " + ErrorHelper.getCauseTrace(e));
		}
	}

	private void play () {
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Play(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				System.err.println("Playing desu~!");
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				System.err.println("Failed to play: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "Failed to play on transport '" + this.avTransport + "'.");
	}

	@Override
	public void pausePlaying () {
		checkAlive();
		if (getPlayState() == PlayState.PAUSED) {
			play();
		}
		else {
			final CountDownLatch cdl = new CountDownLatch(1);
			this.controlPoint.execute(new Pause(this.avTransport) {
				@Override
				public void success (final ActionInvocation invocation) {
					System.err.println("Paused desu~");
					cdl.countDown();
				}

				@Override
				public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
					System.err.println("Failed to pause: " + defaultMsg);
					cdl.countDown();
				}
			});
			await(cdl, "Failed to pause playback on transport '" + this.avTransport + "'.");
		}
	}

	@Override
	public void stopPlaying () {
		checkAlive();
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Stop(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				System.err.println("Stopped desu~");
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				System.err.println("Failed to stop: " + defaultMsg);
				cdl.countDown();
			}
		});
		try {
			await(cdl, "Failed to stop playback on transport '" + this.avTransport + "'.");
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
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<TransportInfo> ref = new AtomicReference<TransportInfo>();
		this.controlPoint.execute(new GetTransportInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final TransportInfo transportInfo) {
				ref.set(transportInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				System.err.println("Failed get transport info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "Failed to get playback state for transport '" + this.avTransport + "'.");

		final TransportInfo ti = ref.get();
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
		final PositionInfo pi = getPositionInfo();
		if (pi == null) return 0;
		return pi.getTrackElapsedSeconds();
	}

	@Override
	public int getCurrentTrackDuration () {
		checkAlive();
		final PositionInfo pi = getPositionInfo();
		if (pi == null) return 0;
		return (int) pi.getTrackDurationSeconds();
	}

	private PositionInfo getPositionInfo () {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<PositionInfo> ref = new AtomicReference<PositionInfo>();
		this.controlPoint.execute(new GetPositionInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final PositionInfo positionInfo) {
				ref.set(positionInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				System.err.println("Failed get position info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "Failed to get position info for transport '" + this.avTransport + "'.");
		return ref.get();
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
		System.err.println("TODO: todo go full screen?");
	}

	private static void await (final CountDownLatch cdl, final String errMsg) {
		try {
			cdl.await(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e) {
			throw new IllegalStateException(errMsg, e);
		}
	}

}
