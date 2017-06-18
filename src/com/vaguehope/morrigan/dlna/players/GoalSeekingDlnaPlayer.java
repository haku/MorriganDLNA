package com.vaguehope.morrigan.dlna.players;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.DlnaException;
import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.util.Quietly;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.util.ErrorHelper;
import com.vaguehope.morrigan.util.Objs;

public class GoalSeekingDlnaPlayer extends AbstractDlnaPlayer {

	private static final int SLOW_RETRIES_AFTER_SECONDS = 60;
	private static final int SLOW_RETRIE_DELAY_SECONDS = 10;

	private static final int MIN_POSITION_TO_RECORD_STARTED_SECONDS = 5;
	private static final int MIN_POSITION_TO_RESTORE_SECONDS = 10;
	private static final int LOP_WITHIN_END_TO_RECORD_END_SECONDS = 5;

	private static final Logger LOG = LoggerFactory.getLogger(GoalSeekingDlnaPlayer.class);

	private final ScheduledFuture<?> schdFuture;

	public GoalSeekingDlnaPlayer (
			final PlayerRegister register,
			final ControlPoint controlPoint, final RemoteService avTransportSvc,
			final MediaServer mediaServer,
			final MediaFileLocator mediaFileLocator,
			final ScheduledExecutorService scheduledExecutor) {
		super(register, controlPoint, avTransportSvc, mediaServer, mediaFileLocator, scheduledExecutor);
		controlPoint.execute(new AvSubscriber(this, this.avEventListener, avTransportSvc, 600));
		this.schdFuture = scheduledExecutor.scheduleWithFixedDelay(this.schdRunner, 1, 1, TimeUnit.SECONDS);
	}

	@Override
	protected void onDispose () {
		this.schdFuture.cancel(true);
		super.onDispose();
	}

	private final Runnable schdRunner = new Runnable() {
		@Override
		public void run () {
			runAndDoNotThrow();
		}
	};

	private final AvEventListener avEventListener = new AvEventListener() {
		@Override
		public void onTransportState (final TransportState transportState) {
			GoalSeekingDlnaPlayer.this.eventQueue.add(transportState);
		}
	};

	private volatile long lastSuccessNanos = System.nanoTime();

	private void markLastSuccess () {
		this.lastSuccessNanos = System.nanoTime();
	}

	private long secondsSinceLastSuccess () {
		return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - this.lastSuccessNanos);
	}

	private void runAndDoNotThrow () {
		try {
			readEventQueue();

			final PlayState cState = readStateAndSeekGoal();
			setCurrentState(cState);

			markLastSuccess();
		}
		catch (final DlnaException e) {
			LOG.warn("DLNA call failed: {}", ErrorHelper.oneLineCauseTrace(e));
		}
		catch (final Exception e) {
			LOG.warn("Unhandled error in event thread.", e);
		}

		if (secondsSinceLastSuccess() > SLOW_RETRIES_AFTER_SECONDS) {
			Quietly.sleep(SLOW_RETRIE_DELAY_SECONDS, TimeUnit.SECONDS); // Rate limit errors.
		}
	}

	private volatile PlayState currentState = null;
	private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<Object>();
	/*
	 * These fields must only be written from the event thread.
	 */
	private volatile DlnaToPlay goalToPlay = null;
	private volatile PlayState goalState = null;
	private volatile Long goalSeekToSeconds = null;
	private volatile long lastObservedPositionSeconds = 0;
	/**
	 * Specifically goToPlay has finished playing.
	 */
	private volatile boolean unprocessedPlaybackOfGoalStoppedEvent = false;

	private void readEventQueue () {
		final DlnaToPlay prevToPlay = this.goalToPlay;
		boolean stopEvent = false;

		Object obj;
		while ((obj = this.eventQueue.poll()) != null) {
			if (obj instanceof PlayState) {
				final PlayState newState = (PlayState) obj;
				if (newState == PlayState.LOADING) throw new IllegalStateException("Loading is not a valid target state.");
				this.goalState = newState;
			}
			else if (obj instanceof Long) {
				this.goalSeekToSeconds = (Long) obj;
			}
			else if (obj instanceof DlnaToPlay) {
				this.goalToPlay = (DlnaToPlay) obj;
				this.lastObservedPositionSeconds = 0;
			}
			else if (obj instanceof TransportState) {
				final TransportState transportState = (TransportState) obj;
				if (transportState == TransportState.STOPPED || transportState == TransportState.NO_MEDIA_PRESENT) {
					stopEvent = true;
				}
			}
			else {
				LOG.warn("Unexpected {} type on event queue: {}", obj.getClass(), obj);
			}
		}

		if (this.goalToPlay != null && this.goalToPlay == prevToPlay && stopEvent) {
			LOG.info("Track finished playing event for: {}", this.goalToPlay.getId());
			this.unprocessedPlaybackOfGoalStoppedEvent = true;
		}
	}

	/**
	 * Returns the state that should be shown externally in UIs, etc.
	 */
	private PlayState readStateAndSeekGoal () throws DlnaException {
		// Capture state.
		final DlnaToPlay goToPlay = this.goalToPlay;
		final PlayState goState = this.goalState;
		final long lopSeconds = this.lastObservedPositionSeconds;

		// If no goal state, do not do anything.
		if (goToPlay == null) return PlayState.STOPPED;
		if (goState == null) return PlayState.STOPPED;

		// Read renderer state.
		final MediaInfo renMi = this.avTransport.getMediaInfo();
		final TransportInfo renTi = this.avTransport.getTransportInfo();
		final PositionInfo renPi = this.avTransport.getPositionInfo();

		// Get things ready to compare.
		final TransportState renState = renTi.getCurrentTransportState();
		final String renUri = renMi != null ? renMi.getCurrentURI() : null;

		// Has the track finished playing?
		final boolean lopAtEnd = lopSeconds >= goToPlay.getDurationSeconds() - LOP_WITHIN_END_TO_RECORD_END_SECONDS;
		final boolean trackNotPlaying = renUri == null || renState == TransportState.STOPPED || renState == TransportState.NO_MEDIA_PRESENT;
		final boolean rendererStoppedPlaying = this.unprocessedPlaybackOfGoalStoppedEvent || (trackNotPlaying && lopAtEnd);
		this.unprocessedPlaybackOfGoalStoppedEvent = false;

		// Did the render stop on its own?
		if (rendererStoppedPlaying) {
			LOG.info("Assuming playback stopped: {}", goToPlay.getId());

			// Track ended event.
			if (lopAtEnd) {
				LOG.info("Assuming track was played to end: {} ({}s of {}s)", goToPlay.getId(), lopSeconds, goToPlay.getDurationSeconds());
				this.goalToPlay.recordEndOfTrack();

				// Make the UI show that the end of the track was reached exactly.
				getListeners().positionChanged(goToPlay.getDurationSeconds(), goToPlay.getDurationSeconds());

				// Clear goal state.
				this.goalToPlay = null;
				this.goalSeekToSeconds = null;
				this.lastObservedPositionSeconds = 0;

				return PlayState.STOPPED; // Made a change, so return.
			}

			LOG.info("But track did not play to end, going to try again from {}s...", lopSeconds);
		}

		// If renderer is between states or a strange state, wait.
		if (renState != null) {
			switch (renState) {
				case CUSTOM:
				case TRANSITIONING:
					LOG.info("Waiting for renderer to leave state: {}", renState);
					return PlayState.LOADING;
				default:
			}
		}

		// Should stop?
		if (goState == PlayState.STOPPED) {
			if (renState != null) {
				switch (renState) {
					case PAUSED_PLAYBACK:
					case PAUSED_RECORDING:
					case PLAYING:
					case RECORDING:
						this.avTransport.stop();
						LOG.info("Stopped.");
						return PlayState.STOPPED; // Made a change, so return.
					default:
				}
			}
			this.goalToPlay = null;
			this.goalSeekToSeconds = null;
			LOG.info("Cleared goal state.");
			return PlayState.STOPPED; // Target state reached.  Stop.
		}

		// Renderer got the right URI?  If not, start playing right URL.
		if (!Objs.equals(renUri, goToPlay.getUri())) {
			if (goState == PlayState.PAUSED) return PlayState.PAUSED; // We would load, but will wait until not paused before doing so.

			LOG.info("loading: {}", goToPlay);
			this.avTransport.setUri(
					goToPlay.getId(),
					goToPlay.getUri(),
					goToPlay.getItem().getTrack().getTitle(),
					goToPlay.getMimeType(), goToPlay.getFileSize(),
					goToPlay.getCoverArtUri(),
					goToPlay.getItem().getTrack().getDuration());
			this.avTransport.play();
			LOG.info("Loaded {}.", goToPlay.getId());
			scheduleRestorePosition(lopSeconds);
			return PlayState.LOADING; // Made a change, so return.
		}

		// Should resume / pause?
		if (goState == PlayState.PAUSED) {
			if (renState != null) {
				switch (renState) {
					case PLAYING:
					case RECORDING:
						this.avTransport.pause();
						LOG.info("Paused.");
						return PlayState.PAUSED; // Made a change, so return.
					default:
				}
			}
		}
		else if (goState == PlayState.PLAYING) {
			if (renState != null) {
				switch (renState) {
					case STOPPED:
					case NO_MEDIA_PRESENT:
						this.avTransport.play();
						LOG.info("Started playback.");
						scheduleRestorePosition(lopSeconds);
						return PlayState.PLAYING; // Made a change, so return.

					case PAUSED_PLAYBACK:
					case PAUSED_RECORDING:
						this.avTransport.play();
						LOG.info("Resumed.");
						return PlayState.PLAYING; // Made a change, so return.

					default:
				}
			}
		}

		// Check playback progress.
		final long renElapsedSeconds;
		final long renDurationSeconds;
		if (renPi == null) {
			renElapsedSeconds = -1;
			renDurationSeconds = -1;
		}
		else {
			renElapsedSeconds = renPi.getTrackElapsedSeconds();
			renDurationSeconds = renPi.getTrackDurationSeconds();
		}

		// Stash current play back progress.
		if (renElapsedSeconds > 0) this.lastObservedPositionSeconds = renElapsedSeconds;

		// Notify event listeners.
		getListeners().positionChanged(renElapsedSeconds, (int) renDurationSeconds);

		// track started event.  recordStartOfTrack() expects ignore multiple invocations.
		if (renElapsedSeconds > MIN_POSITION_TO_RECORD_STARTED_SECONDS) {
			this.goalToPlay.recordStartOfTrack();
		}

		// External state can now reflect renderer state.
		final PlayState renPlayState = transportIntoToPlayState(renTi);

		// Need to seek to position?
		// Check and set should be safe as only our thread should be updating it.
		if (renElapsedSeconds > 0 && this.goalSeekToSeconds != null && this.goalSeekToSeconds >= 0) {
			this.avTransport.seek(this.goalSeekToSeconds);
			LOG.info("Set position to {}s.", this.goalSeekToSeconds);
			this.goalSeekToSeconds = null;
			return renPlayState; // Made a change, so return.
		}

		return renPlayState;
	}

	private void scheduleRestorePosition (final long lopSeconds) {
		if (lopSeconds > MIN_POSITION_TO_RESTORE_SECONDS) {
			this.eventQueue.add(Long.valueOf(lopSeconds));
			LOG.info("Scheduled restore position: {}s", lopSeconds);
		}
		else {
			this.lastObservedPositionSeconds = 0; // In case something left over.
		}
	}

	private void setCurrentState (final PlayState state) {
		this.currentState = state;
		getListeners().playStateChanged(this.currentState);
	}

	@Override
	public PlayState getEnginePlayState () {
		final PlayState ps = this.currentState;
		if (ps != null) return ps;
		return PlayState.STOPPED;
	}

	@Override
	protected boolean shouldBePlaying () {
		// goal state can not be LOADING.
		return this.goalState == PlayState.PLAYING;
	}

	@Override
	public void pausePlaying () {
		final PlayState playState = getPlayState();
		if (playState == PlayState.PAUSED) {
			this.eventQueue.add(PlayState.PLAYING);
		}
		else if (playState == PlayState.PLAYING || playState == PlayState.LOADING) {
			this.eventQueue.add(PlayState.PAUSED);
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
		this.eventQueue.add(PlayState.STOPPED);
	}

	@Override
	public void seekTo (final double seekToProportion) {
		final PlayItem item = getCurrentItem();
		final IMediaTrack track = item != null ? item.getTrack() : null;
		final int durationSeconds = track != null ? track.getDuration() : getCurrentTrackDuration();

		if (durationSeconds > 0) {
			final long seekToSeconds = (long) (durationSeconds * seekToProportion);
			this.eventQueue.add(Long.valueOf(seekToSeconds));
		}
	}

	@Override
	protected void dlnaPlay (final PlayItem item, final String id, final String uri, final MimeType mimeType, final long fileSize, final String coverArtUri) throws DlnaException {
		setCurrentItem(item);
		saveState();

		this.eventQueue.add(new DlnaToPlay(item, id, uri, mimeType, fileSize, coverArtUri, this));
		this.eventQueue.add(PlayState.PLAYING);
		setCurrentState(PlayState.LOADING);

		// Only restore position if for same item.
		final PlayerState rps = getRestorePositionState();
		if (rps != null && rps.getCurrentItem() != null && rps.getCurrentItem().hasTrack()) {
			if (Objs.equals(item.getTrack(), rps.getCurrentItem().getTrack())) {
				this.eventQueue.add(Long.valueOf(rps.getPosition()));
				LOG.info("Scheduled restore of position: {}s", rps.getPosition());
			}
			else {
				LOG.info("Not restoring position for {} as track is {}.",
						rps.getCurrentItem().getTrack(), item.getTrack());
			}
		}
		clearRestorePositionState();

		LOG.info("Playback scheduled: {}", id);
	}

}
