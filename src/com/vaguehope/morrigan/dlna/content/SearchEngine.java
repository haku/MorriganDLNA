package com.vaguehope.morrigan.dlna.content;

import java.util.Collections;
import java.util.List;

import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.model.item.Item;

public class SearchEngine {

	public SearchEngine () {}

	public List<Item> search (final ContentNode contentNode, final String searchCriteria) throws ContentDirectoryException {
		if (searchCriteria == null) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		// TODO implement me.
		return Collections.emptyList();
	}

}
