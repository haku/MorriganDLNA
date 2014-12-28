package com.vaguehope.morrigan.dlna.content;

import java.util.Collections;
import java.util.List;

import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.model.item.Item;

import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.sqlitewrapper.DbException;

public class SearchEngine {

	private final ContentAdaptor contentAdaptor;

	public SearchEngine (final ContentAdaptor contentAdaptor) {
		this.contentAdaptor = contentAdaptor;
	}

	public List<Item> search (final String containerId, final String searchCriteria) throws ContentDirectoryException, DbException, MorriganException {
		if (searchCriteria == null) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);

		IMixedMediaDb db = this.contentAdaptor.objectIdToDb(containerId);
		if (db == null) return null;

		// TODO parse searchCriteria.

		return Collections.emptyList();
	}

}
