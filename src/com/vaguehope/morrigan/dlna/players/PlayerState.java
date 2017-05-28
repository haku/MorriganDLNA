package com.vaguehope.morrigan.dlna.players;

import java.util.ArrayList;
import java.util.List;

import com.vaguehope.morrigan.player.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.PlayerQueue;

public class PlayerState {

	private final PlaybackOrder playbackOrder;
	private final PlayItem currentItem;
	private final long position;
	private final List<PlayItem> queueItems;

	public PlayerState (final PlaybackOrder playbackOrder, final PlayItem currentItem, final long position, final PlayerQueue queue) {
		this.playbackOrder = playbackOrder;
		this.currentItem = currentItem;
		this.position = position;
		this.queueItems = new ArrayList<PlayItem>(queue.getQueueList());
	}

	public PlaybackOrder getPlaybackOrder () {
		return this.playbackOrder;
	}

	public PlayItem getCurrentItem () {
		return this.currentItem;
	}

	public long getPosition () {
		return this.position;
	}

	public void addItemsToQueue (final PlayerQueue queue) {
		for (final PlayItem item : this.queueItems) {
			queue.addToQueue(item.withoutId());
		}
	}

	@Override
	public String toString () {
		return String.format("PlayerState{po=%s ci=%s p=%ss q=%s}", this.playbackOrder, this.currentItem, this.position, this.queueItems.size());
	}

}
