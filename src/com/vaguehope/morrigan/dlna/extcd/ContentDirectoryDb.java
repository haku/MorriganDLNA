package com.vaguehope.morrigan.dlna.extcd;

import java.util.List;
import java.util.UUID;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;

import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.IMixedMediaItem.MediaType;
import com.vaguehope.morrigan.model.media.MediaListReference.MediaListType;
import com.vaguehope.sqlitewrapper.DbException;

public class ContentDirectoryDb extends EphemeralMixedMediaDb {

	private static final int MAX_TRIES = 2;

	private final String listId;
	private final RemoteDevice device;
	private final ContentDirectory contentDirectory;

	public ContentDirectoryDb (final String listId, final ControlPoint controlPoint, final RemoteDevice device, final RemoteService contentDirectory) {
		this.listId = listId;
		this.device = device;
		this.contentDirectory = new ContentDirectory(controlPoint, contentDirectory);
	}

	@Override
	public String getListId () {
		return this.listId;
	}

	@Override
	public UUID getUuid () {
		return UUID.fromString(this.listId);
	}

	@Override
	public String getListName () {
		return this.device.getDetails().getFriendlyName();
	}

	@Override
	public String getType () {
		return MediaListType.EXTMMDB.toString();
	}

	@Override
	public boolean hasFile (final String remoteId) throws MorriganException, DbException {
		return this.contentDirectory.fetchItemByIdWithRetry(remoteId, MAX_TRIES) != null;
	}

	@Override
	public IMixedMediaItem getByFile (final String remoteId) throws DbException {
		final IMixedMediaItem item = this.contentDirectory.fetchItemByIdWithRetry(remoteId, MAX_TRIES);
		if (item == null) throw new IllegalArgumentException("File with ID '" + remoteId + "' not found.");
		return item;
	}

	@Override
	public List<IMixedMediaItem> simpleSearchMedia (final MediaType mediaType, final String term, final int maxResults, final IDbColumn[] sortColumns, final SortDirection[] sortDirections, final boolean includeDisabled) throws DbException {
		return this.contentDirectory.searchWithRetry(term, maxResults, MAX_TRIES);
	}

}
