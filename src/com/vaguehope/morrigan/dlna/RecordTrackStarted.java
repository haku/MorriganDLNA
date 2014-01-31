package com.vaguehope.morrigan.dlna;

import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.util.ErrorHelper;

final class RecordTrackStarted implements Runnable {

	private final PlayItem item;

	public RecordTrackStarted (final PlayItem item) {
		this.item = item;
	}

	@Override
	public void run () {
		try {
			this.item.getList().incTrackStartCnt(this.item.getTrack());
		}
		catch (final Exception e) { // NOSONAR no other way to report errors.
			System.err.println("Failed to increment track start count: " + ErrorHelper.getCauseTrace(e));
		}
	}

}
