package com.vaguehope.morrigan.dlna;

import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player.PlayerEventListener;

public class PlayerEventCache implements PlayerEventListener {

	private volatile PlaybackOrder playbackOrder;
	private volatile PlayItem currentItem;
	private volatile PlayState playState;
	private volatile long position = 0;
	private volatile int duration = 0;

	@Override
	public void playOrderChanged (final PlaybackOrder newPlaybackOrder) {
		this.playbackOrder = newPlaybackOrder;
	}

	@Override
	public void currentItemChanged (final PlayItem newItem) {
		this.currentItem = newItem;
	}

	@Override
	public void playStateChanged (final PlayState newPlayState) {
		this.playState = newPlayState;
	}

	@Override
	public void positionChanged (final long newPosition, final int newDuration) {
		this.position = newPosition;
		this.duration = newDuration;
	}

	@Override
	public void onException (final Exception e) {/* Unused. */}

	public PlaybackOrder getPlaybackOrder () {
		return this.playbackOrder;
	}

	public PlayItem getCurrentItem () {
		return this.currentItem;
	}

	public PlayState getPlayState () {
		return this.playState;
	}

	public long getPosition () {
		return this.position;
	}

	public int getDuration () {
		return this.duration;
	}

}
