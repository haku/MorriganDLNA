package com.vaguehope.morrigan.dlna.content;

import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 * Either a Container or an Item.
 */
public class ContentNode {

	private final Container container;
	private final Item item;

	public ContentNode (final Container container) {
		this.container = container;
		this.item = null;
	}

	public ContentNode (final Item item) {
		this.container = null;
		this.item = item;
	}

	public boolean isItem () {
		return this.item != null;
	}

	public Item getItem () {
		return this.item;
	}

	public Container getContainer () {
		return this.container;
	}

}
