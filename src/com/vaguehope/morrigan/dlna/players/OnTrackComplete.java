package com.vaguehope.morrigan.dlna.players;

import com.vaguehope.morrigan.player.PlayItem;

final class OnTrackComplete implements Runnable {

	private final DlnaPlayer dlnaPlayer;
	private final PlayItem item;

	public OnTrackComplete (final DlnaPlayer dlnaPlayer, final PlayItem item) {
		this.dlnaPlayer = dlnaPlayer;
		this.item = item;
	}

	@Override
	public void run () {
		this.dlnaPlayer.recordTrackCompleted(this.item);
		this.dlnaPlayer.nextTrack();
	}

}
