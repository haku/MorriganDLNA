package com.vaguehope.morrigan.dlna.players;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.UserPrefs;
import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.AbstractPlayer;
import com.vaguehope.morrigan.player.OrderHelper;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.util.ErrorHelper;

public class DlnaPlayer extends AbstractPlayer {

	private static final String PREF_PLAYBACK_ORDER = "playorder";

	private static final Logger LOG = LoggerFactory.getLogger(DlnaPlayer.class);

	private final AvTransport avTransport;
	private final MediaServer mediaServer;
	private final MediaFileLocator mediaFileLocator;
	private final ScheduledExecutorService scheduledExecutor;
	private final String uid;

	private final PlayerEventCache playerEventCache = new PlayerEventCache();

	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();
	private final AtomicReference<String> currentUri = new AtomicReference<String>();
	private final AtomicReference<WatcherTask> watcher = new AtomicReference<WatcherTask>(null);

	public DlnaPlayer (
			final PlayerRegister register,
			final ControlPoint controlPoint, final RemoteService avTransportSvc,
			final MediaServer mediaServer,
			final MediaFileLocator mediaFileLocator,
			final ScheduledExecutorService scheduledExecutor) {
		super(idFromRemoteService(avTransportSvc), avTransportSvc.getDevice().getDetails().getFriendlyName(), register);
		this.avTransport = new AvTransport(controlPoint, avTransportSvc);
		this.mediaServer = mediaServer;
		this.mediaFileLocator = mediaFileLocator;
		this.scheduledExecutor = scheduledExecutor;
		this.uid = remoteServiceUid(avTransportSvc);
		addEventListener(this.playerEventCache);
	}

	public String getUid () {
		return this.uid;
	}

	@Override
	protected void onDispose () {
		LOG.info("Disposed {}: {}.", this.uid, toString());
	}

	@Override
	public boolean isPlaybackEngineReady () {
		return !isDisposed();
	}

	@Override
	protected void loadAndStartPlaying (final PlayItem item, final File file) throws Exception {
		final String id = this.mediaFileLocator.fileId(file);
		final String uri = this.mediaServer.uriForId(id);
		final File coverArt = item.getTrack().findCoverArt();
		final String coverArtUri = coverArt != null ? this.mediaServer.uriForId(this.mediaFileLocator.fileId(coverArt)) : null;
		LOG.info("loading: " + id);
		stopPlaying();
		this.avTransport.setUri(id, uri, item.getTrack().getTitle(), file, coverArtUri);
		this.currentUri.set(uri);
		this.avTransport.play();
		this.currentItem.set(item);
		startWatcher(uri, item);
		saveState();
	}

	private void startWatcher (final String uri, final PlayItem item) {
		final WatcherTask oldWatcher = this.watcher.getAndSet(null);
		if (oldWatcher != null) oldWatcher.cancel();

		final WatcherTask task = WatcherTask.schedule(this.scheduledExecutor, uri, this.currentUri, this.avTransport,
				getListeners(),
				new OnTrackStarted(this, item), new OnTrackComplete(this, item));
		if (!this.watcher.compareAndSet(null, task)) {
			task.cancel();
			LOG.info("Failed to configure watcher as another got there first.");
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
		final PlayState playState = getPlayState();
		if (playState == PlayState.PAUSED) {
			this.avTransport.play();
		}
		else if (playState == PlayState.PLAYING || playState == PlayState.LOADING) {
			this.avTransport.pause();
		}
		else if (playState == PlayState.STOPPED) {
			final PlayItem ci = getCurrentItem();
			if (ci != null) loadAndStartPlaying(ci);
		}
		else {
			LOG.warn("Asked to pause when state is {}, do not know what to do.", playState);
		}
	}

	@Override
	public void stopPlaying () {
		checkAlive();
		this.avTransport.stop();
		getListeners().playStateChanged(PlayState.STOPPED);
	}

	@Override
	public void nextTrack () {
		checkAlive();
		final PlayItem nextItemToPlay = getNextItemToPlay();
		if (nextItemToPlay != null) {
			loadAndStartPlaying(nextItemToPlay);
		}
		else {
			stopPlaying();
		}
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
		final PlayState ps = this.playerEventCache.getPlayState();
		if (ps != null) return ps;
		return PlayState.STOPPED;
	}

	@Override
	public void setCurrentItem (final PlayItem item) {
		this.currentItem.set(item);
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
		return this.playerEventCache.getPosition();
	}

	@Override
	public int getCurrentTrackDuration () {
		return this.playerEventCache.getDuration();
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

	@Override
	public void setPlaybackOrder (final PlaybackOrder order) {
		super.setPlaybackOrder(order);
		try {
			UserPrefs.INSTANCE.putValue(PREF_PLAYBACK_ORDER, getUid(), order.name());
		}
		catch (final IOException e) {
			LOG.error("Failed to persist playback order.", ErrorHelper.getCauseTrace(e));
		}
	}

	void restoreBackedUpState (final PlayerState state) {
		if (state == null) return;
		setPlaybackOrder(state.getPlaybackOrder());
		setCurrentItem(state.getCurrentItem());
		state.addItemsToQueue(getQueue());
		LOG.info("Restored {}: {}.", this.uid, state);
	}

	public PlayerState backupState () {
		return new PlayerState(getPlaybackOrder(), getCurrentItem(), getQueue());
	}

	public static String idFromRemoteService (final RemoteService rs) {
		return String.format("%s-%s",
				rs.getDevice().getIdentity().getUdn().getIdentifierString(),
				rs.getServiceId().getId())
				.replaceAll("[^a-zA-Z0-9-]", "_");
	}

	public static String remoteServiceUid (final RemoteService rs) {
		return String.format("%s/%s", rs.getDevice().getIdentity().getUdn(), rs.getServiceId().getId());
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