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
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayerRegister;

public abstract class AbstractDlnaPlayer extends AbstractPlayer {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractDlnaPlayer.class);

	protected final AvTransport avTransport;
	protected final ScheduledExecutorService scheduledExecutor;
	private final MediaServer mediaServer;
	private final MediaFileLocator mediaFileLocator;

	private final String uid;

	protected final PlayerEventCache playerEventCache = new PlayerEventCache();

	private final AtomicReference<PlayItem> currentItem = new AtomicReference<PlayItem>();

	private volatile PlayerState restorePositionState;

	public AbstractDlnaPlayer (
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
		LOG.info("Disposed {}: {}.", this.uid, toString());
	}

	@Override
	public boolean isPlaybackEngineReady () {
		return !isDisposed();
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
	public IMediaTrackList<? extends IMediaTrack> getCurrentList () {
		final PlayItem item = getCurrentItem();
		return item == null ? null : item.getList();
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
	public long getCurrentPosition () {
		return this.playerEventCache.getPosition();
	}

	@Override
	public int getCurrentTrackDuration () {
		return this.playerEventCache.getDuration();
	}

	@Override
	public void setPlaybackOrder (final PlaybackOrder order) {
		super.setPlaybackOrder(order);
		saveState();
	}

	@Override
	public void nextTrack () {
		checkAlive();
		final PlayItem nextItemToPlay = findNextItemToPlay();
		if (nextItemToPlay != null) {
			loadAndStartPlaying(nextItemToPlay);
		}
		else {
			stopPlaying();
		}
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

		dlnaPlay(item, id, uri, mimeType, fileSize, coverArtUri);
	}

	protected abstract void dlnaPlay (PlayItem item, String id, String uri, MimeType mimeType, long fileSize, String coverArtUri) throws DlnaException;

	public PlayerState backupState () {
		// TODO include if actually playing?

		return new PlayerState(getPlaybackOrder(), getCurrentItem(), getCurrentPosition(), getQueue());
	}

	void restoreBackedUpState (final PlayerState state) {
		if (state == null) return;
		setPlaybackOrder(state.getPlaybackOrder());
		setCurrentItem(state.getCurrentItem());
		this.restorePositionState = state;
		state.addItemsToQueue(getQueue());

		// TODO if was playing before, resume playback.

		markStateRestoreAttempted();
		LOG.info("Restored {}: {}.", getUid(), state);
	}

	protected PlayerState getRestorePositionState () {
		return this.restorePositionState;
	}

	protected void clearRestorePositionState () {
		this.restorePositionState = null;
	}

	protected PlayItem findNextItemToPlay () {
		final PlayItem queueItem = this.getQueue().takeFromQueue();
		if (queueItem != null) return queueItem;

		final PlayItem lastItem = getCurrentItem();
		if (lastItem == null || !lastItem.hasList()) return null;

		final IMediaTrack nextTrack = getOrderResolver().getNextTrack(lastItem.getList(), lastItem.getTrack(), getPlaybackOrder());
		if (nextTrack != null) return new PlayItem(lastItem.getList(), nextTrack);

		return null;
	}

	protected void recordTrackStarted (final PlayItem item) {
		this.scheduledExecutor.execute(new RecordTrackStarted(item));
	}

	protected void recordTrackCompleted (final PlayItem item) {
		this.scheduledExecutor.execute(new RecordTrackCompleted(item));
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
