package com.vaguehope.morrigan.dlna.players;

import com.vaguehope.morrigan.player.PlayItem;

public class OnTrackStarted implements Runnable {

	private final DlnaPlayer dlnaPlayer;
	private final PlayItem item;

	public OnTrackStarted (final DlnaPlayer dlnaPlayer, final PlayItem item) {
		this.dlnaPlayer = dlnaPlayer;
		this.item = item;
	}

	@Override
	public void run () {
		this.dlnaPlayer.recordTrackStarted(this.item);
	}

}
