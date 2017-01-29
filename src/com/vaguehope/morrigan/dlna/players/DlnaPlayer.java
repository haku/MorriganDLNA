package com.vaguehope.morrigan.dlna.players;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportStatus;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.DlnaException;
import com.vaguehope.morrigan.dlna.MediaFormat;
import com.vaguehope.morrigan.dlna.UpnpHelper;
import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.util.StringHelper;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.AbstractPlayer;
import com.vaguehope.morrigan.player.OrderHelper;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.util.Objs;

public class DlnaPlayer extends AbstractPlayer {

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

	private volatile PlayerState restorePositionState;

	public DlnaPlayer (
			final PlayerRegister register,
			final ControlPoint controlPoint, final RemoteService avTransportSvc,
			final MediaServer mediaServer,
			final MediaFileLocator mediaFileLocator,
			final ScheduledExecutorService scheduledExecutor) {
		super(UpnpHelper.idFromRemoteService(avTransportSvc), avTransportSvc.getDevice().getDetails().getFriendlyName(), register);
		this.avTransport = new AvTransport(controlPoint, avTransportSvc);
		this.mediaServer = mediaServer;
		this.mediaFileLocator = mediaFileLocator;
		this.scheduledExecutor = scheduledExecutor;
		this.uid = UpnpHelper.remoteServiceUid(avTransportSvc);
		addEventListener(this.playerEventCache);
	}

	public String getUid () {
		return this.uid;
	}

	@Override
	protected void onDispose () {
		final WatcherTask watcher = this.watcher.getAndSet(null);
		if (watcher != null) watcher.cancel();

		LOG.info("Disposed {}: {}.", this.uid, toString());
	}

	@Override
	public boolean isPlaybackEngineReady () {
		return !isDisposed();
	}

	@Override
	protected void loadAndPlay (final PlayItem item, final File altFile) throws DlnaException {
		final String id;
		if (altFile != null) {
			id = this.mediaFileLocator.fileId(altFile);
		}
		else if (StringHelper.notBlank(item.getTrack().getRemoteId())) {
			id = item.getTrack().getRemoteId();
		}
		else {
			id = this.mediaFileLocator.fileId(new File(item.getTrack().getFilepath()));
		}

		final String uri;
		final MimeType mimeType;
		final long fileSize;
		if (altFile != null) {
			uri = this.mediaServer.uriForId(id);
			mimeType = MediaFormat.identify(altFile).toMimeType();
			fileSize = altFile.length();
		}
		else if (StringHelper.notBlank(item.getTrack().getRemoteLocation())) {
			uri = item.getTrack().getRemoteLocation();
			mimeType = MimeType.valueOf(item.getTrack().getMimeType());
			fileSize = item.getTrack().getFileSize();
		}
		else {
			uri = this.mediaServer.uriForId(id);
			final File file = new File(item.getTrack().getFilepath());
			mimeType = MediaFormat.identify(file).toMimeType();
			fileSize = file.length();
		}

		final String coverArtUri;
		if (StringHelper.notBlank(item.getTrack().getCoverArtRemoteLocation())) {
			coverArtUri = item.getTrack().getCoverArtRemoteLocation();
		}
		else {
			final File coverArt = item.getTrack().findCoverArt();
			coverArtUri = coverArt != null ? this.mediaServer.uriForId(this.mediaFileLocator.fileId(coverArt)) : null;
		}

		LOG.info("loading: {}", id);
		stopPlaying();

		// Set these fist so if something goes wrong user can try again.
		this.currentUri.set(uri);
		this.currentItem.set(item);

		this.avTransport.setUri(id, uri, item.getTrack().getTitle(), mimeType, fileSize, coverArtUri, item.getTrack().getDuration());
		this.avTransport.play();

		startWatcher(uri, item);
		saveState();

		// Only restore position if for same item.
		final PlayerState rps = this.restorePositionState;
		if (rps != null && rps.getCurrentItem() != null && rps.getCurrentItem().hasTrack()) {
			if (Objs.equals(item.getTrack(), rps.getCurrentItem().getTrack())) {
				final WatcherTask w = this.watcher.get();
				if (w != null) {
					w.requestSeekAfterPlaybackStarts(rps.getPosition());
					LOG.info("Scheduled restore of position: {}s", rps.getPosition());
				}
			}
			else {
				LOG.info("Not restoreing position for {} as track is {}.",
						rps.getCurrentItem().getTrack(), item.getTrack());
			}
		}
		this.restorePositionState = null;
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
		try {
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
		catch (final DlnaException e) {
			getListeners().onException(e);
		}
	}

	@Override
	public void stopPlaying () {
		checkAlive();
		try {
			this.avTransport.stop();
			getListeners().playStateChanged(PlayState.STOPPED);
		}
		catch (final DlnaException e) {
			getListeners().onException(e);
		}
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
	public PlayState getEnginePlayState () {
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
		try {
			this.avTransport.seek((long) (getCurrentTrackDuration() * d));
		}
		catch (final DlnaException e) {
			getListeners().onException(e);
		}
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
		saveState();
	}

	void restoreBackedUpState (final PlayerState state) {
		if (state == null) return;
		setPlaybackOrder(state.getPlaybackOrder());
		setCurrentItem(state.getCurrentItem());
		this.restorePositionState = state;
		state.addItemsToQueue(getQueue());

		markStateRestoreAttempted();
		LOG.info("Restored {}: {}.", this.uid, state);
	}

	public PlayerState backupState () {
		return new PlayerState(getPlaybackOrder(), getCurrentItem(), getCurrentPosition(), getQueue());
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
