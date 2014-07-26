package com.vaguehope.morrigan.dlna;

import java.util.ArrayList;
import java.util.List;

import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerQueue;

public class PlayerState {

	private final PlaybackOrder playbackOrder;
	private final PlayItem currentItem;
	private final List<PlayItem> queueItems;

	public PlayerState (final PlaybackOrder playbackOrder, final PlayItem currentItem, final PlayerQueue queue) {
		this.playbackOrder = playbackOrder;
		this.currentItem = currentItem;
		this.queueItems = new ArrayList<PlayItem>(queue.getQueueList());
	}

	public PlaybackOrder getPlaybackOrder () {
		return this.playbackOrder;
	}

	public PlayItem getCurrentItem () {
		return this.currentItem;
	}

	public void addItemsToQueue (final PlayerQueue queue) {
		for (final PlayItem item : this.queueItems) {
			queue.addToQueue(new PlayItem(item.getList(), item.getTrack())); // Clone to have not set ID.
		}
	}

	@Override
	public String toString () {
		return String.format("PlayerState{po=%s ci=%s q=%s}", this.playbackOrder, this.currentItem, this.queueItems.size());
	}

}