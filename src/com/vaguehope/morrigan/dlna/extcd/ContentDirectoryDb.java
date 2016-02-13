package com.vaguehope.morrigan.dlna.extcd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.contentdirectory.callback.Search;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.util.Cache;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.IMixedMediaItem.MediaType;
import com.vaguehope.morrigan.model.media.MediaListReference.MediaListType;
import com.vaguehope.sqlitewrapper.DbException;

public class ContentDirectoryDb extends EphemeralMixedMediaDb {

	private static final String ROOT_CONTENT_ID = "0"; // Root id of '0' is in the spec.
	private static final int ACTION_TIMEOUT_SECONDS = 10;
	private static final Logger LOG = LoggerFactory.getLogger(ContentDirectoryDb.class);

	private final String listId;
	private final ControlPoint controlPoint;
	private final RemoteDevice device;
	private final RemoteService contentDirectory;

	private final Cache<String, IMixedMediaItem> itemCache = new Cache<String, IMixedMediaItem>(100);

	public ContentDirectoryDb (final String listId, final ControlPoint controlPoint, final RemoteDevice device, final RemoteService contentDirectory) {
		this.listId = listId;
		this.controlPoint = controlPoint;
		this.device = device;
		this.contentDirectory = contentDirectory;
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
		return fetchItemById(remoteId) != null;
	}

	@Override
	public IMixedMediaItem getByFile (final String remoteId) throws DbException {
		final IMixedMediaItem item = fetchItemById(remoteId);
		if (item == null) throw new IllegalArgumentException("File with ID '" + remoteId + "' not found.");
		return item;
	}

	private IMixedMediaItem fetchItemById (final String remoteId) throws DbException {
		IMixedMediaItem cached = this.itemCache.getFresh(remoteId, 1, TimeUnit.MINUTES);
		if (cached != null) return cached;

		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<DIDLContent> ref = new AtomicReference<DIDLContent>();
		final AtomicReference<String> err = new AtomicReference<String>();

		this.controlPoint.execute(new Browse(this.contentDirectory, remoteId, BrowseFlag.METADATA, Browse.CAPS_WILDCARD, 0, 2L) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				final String msg = "Failed to fetch item " + remoteId + ": " + defaultMsg;
				LOG.info(msg);
				err.set(msg);
				cdl.countDown();
			}

			@Override
			public void received (final ActionInvocation actionInvocation, final DIDLContent didl) {
				ref.set(didl);
				cdl.countDown();
			}

			@Override
			public void updateStatus (final Status status) {
				// Unused.
			}
		});
		await(cdl, "Fetch '%s' from content directory '%s'.", remoteId, this.contentDirectory);
		if (ref.get() == null) throw new DbException(err.get());

		final List<Item> items = ref.get().getItems();
		if (items.size() < 1) return null;
		if (items.size() > 1) LOG.warn("Fetching item {} returned more than 1 result.", remoteId);
		final IMixedMediaItem item = didlItemToMnItem(items.get(0));
		this.itemCache.put(remoteId, item);
		return item;
	}

	@Override
	public List<IMixedMediaItem> simpleSearchMedia (final MediaType mediaType, final String term, final int maxResults, final IDbColumn[] sortColumns, final SortDirection[] sortDirections, final boolean includeDisabled) throws DbException {
		final String searchCriteria = String.format("((upnp:class derivedfrom \"object.item.videoItem\" or upnp:class derivedfrom \"object.item.audioItem\") and dc:title contains \"%s\")", term);

		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<DIDLContent> ref = new AtomicReference<DIDLContent>();
		final AtomicReference<String> err = new AtomicReference<String>();

		this.controlPoint.execute(new Search(this.contentDirectory, ROOT_CONTENT_ID, searchCriteria, Search.CAPS_WILDCARD, 0, (long) maxResults) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				final String msg = "Failed to search content directory: " + defaultMsg;
				LOG.info(msg);
				err.set(msg);
				cdl.countDown();
			}

			@Override
			public void received (final ActionInvocation invocation, final DIDLContent didl) {
				ref.set(didl);
				cdl.countDown();
			}

			@Override
			public void updateStatus (final Status status) {
				// Unused.
			}
		});
		await(cdl, "Search '%s' on content directory '%s'.", term, this.contentDirectory);
		if (ref.get() == null) throw new DbException(err.get());
		return didlItemsToMnItems(ref.get().getItems());
	}

	private static List<IMixedMediaItem> didlItemsToMnItems (final List<Item> items) {
		final List<IMixedMediaItem> ret = new ArrayList<IMixedMediaItem>();
		for (final Item item : items) {
			ret.add(didlItemToMnItem(item));
		}
		return ret;
	}

	private static IMixedMediaItem didlItemToMnItem (final Item item) {
		Res primaryRes = null;
		MediaType mediaType = MediaType.UNKNOWN;
		Res artRes = null;
		for (final Res res : item.getResources()) {
			final String type = res.getProtocolInfo().getContentFormatMimeType().getType();
			if ("video".equalsIgnoreCase(type) || "audio".equalsIgnoreCase(type)) {
				if (primaryRes == null) {
					primaryRes = res;
					mediaType = MediaType.TRACK;
				}
			}
			else if ("image".equalsIgnoreCase(type)) {
				if (artRes == null) {
					artRes = res;
				}
			}
		}

		if (primaryRes == null && artRes != null) {
			primaryRes = artRes;
			mediaType = MediaType.PICTURE;
			artRes = null;
		}

		if (primaryRes == null) {
			final StringBuilder sb = new StringBuilder()
					.append("id=").append(item.getId())
					.append(" title=").append(item.getTitle());
			for (final Res res : item.getResources()) {
				sb.append(" res{").append(res.getValue())
						.append(", ").append(res.getProtocolInfo().getContentFormat())
						.append("}");
			}
			throw new IllegalArgumentException("No media res found for item: " + sb.toString());
		}

		return new DidlItem(item, primaryRes, mediaType, artRes);
	}

	private static void await (final CountDownLatch cdl, final String msgFormat, final Object... msgArgs) {
		try {
			if (cdl.await(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return;
			throw new IllegalStateException("Timed out while trying to " + String.format(msgFormat, msgArgs));
		}
		catch (final InterruptedException e) {
			throw new IllegalStateException("Interupted while trying to " + String.format(msgFormat, msgArgs), e);
		}
	}

}
