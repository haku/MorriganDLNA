package com.vaguehope.morrigan.dlna;

import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.util.ErrorHelper;

final class RecordTrackCompleted implements Runnable {

	private final PlayItem item;

	public RecordTrackCompleted (final PlayItem item) {
		this.item = item;
	}

	@Override
	public void run () {
		try {
			this.item.list.incTrackEndCnt(this.item.item);
		}
		catch (final Exception e) { // NOSONAR no other way to report errors.
			System.err.println("Failed to increment track end count: " + ErrorHelper.getCauseTrace(e));
		}
	}

}
