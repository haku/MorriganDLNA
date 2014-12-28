package com.vaguehope.morrigan.dlna.content;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.model.ModelUtil;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.WriteStatus;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.AudioItem;
import org.teleal.cling.support.model.item.ImageItem;
import org.teleal.cling.support.model.item.Item;
import org.teleal.cling.support.model.item.VideoItem;
import org.teleal.common.util.MimeType;

import com.vaguehope.morrigan.dlna.ContentGroup;
import com.vaguehope.morrigan.dlna.MediaFormat;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.util.HashHelper;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.ILocalMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.IMixedMediaItem.MediaType;
import com.vaguehope.morrigan.model.media.IMixedMediaItemStorageLayer;
import com.vaguehope.morrigan.model.media.MediaFactory;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.model.media.MediaTag;
import com.vaguehope.sqlitewrapper.DbException;

public class ContentAdaptor {

	private static final int MAX_TAGS = 250;
	private static final int MAX_TAG_ITEMS = 500;

	private static final Logger LOG = LoggerFactory.getLogger(ContentAdaptor.class);

	private final MediaFactory mediaFactory;
	private final MediaServer mediaServer;

	private final Map<String, MediaListReference> objectIdToMediaListReference = new ConcurrentHashMap<String, MediaListReference>();
	private final Map<String, MlrAnd<MediaTag>> objectIdToTag = new ConcurrentHashMap<String, MlrAnd<MediaTag>>();
	private final Map<String, MlrAnd<IMixedMediaItem>> objectIdToMediaItem = new ConcurrentHashMap<String, MlrAnd<IMixedMediaItem>>();

	public ContentAdaptor (final MediaFactory mediaFactory, final MediaServer mediaServer) {
		this.mediaFactory = mediaFactory;
		this.mediaServer = mediaServer;
	}

	/**
	 * Returns null if unknown objectId.
	 */
	public ContentNode getNode (final String objectId) throws DbException, MorriganException {
		if (ContentGroup.ROOT.getId().equals(objectId)) {
			return makeRootNode();
		}

		// TODO cache results?
		// TODO make more efficient by checking id prefixes?

		{
			final MediaListReference mlr = this.objectIdToMediaListReference.get(objectId);
			if (mlr != null) {
				return makeMediaListNode(objectId, mlr);
			}
		}

		{
			final MlrAnd<MediaTag> mlrAndTag = this.objectIdToTag.get(objectId);
			if (mlrAndTag != null) {
				return makeTagNode(objectId, mlrAndTag.getMlr(), mlrAndTag.getObj());
			}
		}

		{
			final MlrAnd<IMixedMediaItem> mlrAndItem = this.objectIdToMediaItem.get(objectId);
			if (mlrAndItem != null) {
				return makeItemNode(objectId, mlrAndItem.getMlr(), mlrAndItem.getObj());
			}
		}

		return null;
	}

	private ContentNode makeRootNode () {
		final Container c = new Container();
		c.setClazz(new DIDLObject.Class("object.container"));
		c.setId(ContentGroup.ROOT.getId());
		c.setParentID("-1");
		c.setTitle(ContentGroup.ROOT.getHumanName());
		c.setCreator(MediaServerDeviceFactory.METADATA_MODEL_NAME);
		c.setRestricted(true);
		c.setSearchable(false); // Root is not searchable.
		c.setWriteStatus(WriteStatus.NOT_WRITABLE);

		for (final MediaListReference mlr : this.mediaFactory.getAllLocalMixedMediaDbs()) {
			final Container mlc = makeContainer(ContentGroup.ROOT.getId(), localMmdbObjectId(mlr), mlr.getTitle());
			mlc.setSearchable(true); // Each DB is searchable.
			c.addContainer(mlc);
		}
		updateContainer(c);

		return new ContentNode(c);
	}

	private ContentNode makeMediaListNode (final String objectId, final MediaListReference mlr) throws DbException, MorriganException {
		if (mlr.getType() == MediaListReference.MediaListType.LOCALMMDB) {
			final ILocalMixedMediaDb db = this.mediaFactory.getLocalMixedMediaDb(mlr.getIdentifier());
			db.read();
			return makeDbNode(objectId, mlr, db);
		}
		throw new IllegalArgumentException("Unknown DB type: " + mlr);
	}

	private ContentNode makeDbNode (final String objectId, final MediaListReference mlr, final IMixedMediaDb db) throws MorriganException {
		// TODO virtual level: tags, all items, etc.
		// For now, tags only.

		final Container c = makeContainer(ContentGroup.ROOT.getId(), objectId, mlr.getTitle());

		for (final MediaTag tag : db.getTopTags(MAX_TAGS)) {
			c.addContainer(makeContainer(objectId, tagObjectId(mlr, tag), tag.getTag()));
		}
		updateContainer(c);

		return new ContentNode(c);
	}

	private ContentNode makeTagNode (final String objectId, final MediaListReference mlr, final MediaTag tag) throws DbException, MorriganException {
		if (mlr.getType() == MediaListReference.MediaListType.LOCALMMDB) {
			final ILocalMixedMediaDb db = this.mediaFactory.getLocalMixedMediaDb(mlr.getIdentifier());
			db.read();
			return makeDbTagNode(objectId, mlr, db, tag);
		}
		throw new IllegalArgumentException("Unknown DB type: " + mlr);
	}

	private ContentNode makeDbTagNode (final String objectId, final MediaListReference mlr, final ILocalMixedMediaDb db, final MediaTag tag) throws DbException {
		final Container c = makeContainer(localMmdbObjectId(mlr), objectId, mlr.getTitle());

		final List<IMixedMediaItem> results = db.simpleSearchMedia(
				MediaType.TRACK, String.format("t=%s", tag.getTag()), MAX_TAG_ITEMS,
				new IDbColumn[] {
						IMixedMediaItemStorageLayer.SQL_TBL_MEDIAFILES_COL_ENDCNT,
						IMixedMediaItemStorageLayer.SQL_TBL_MEDIAFILES_COL_DADDED,
						IMixedMediaItemStorageLayer.SQL_TBL_MEDIAFILES_COL_FILE
				},
				new SortDirection[] { SortDirection.DESC, SortDirection.ASC, SortDirection.ASC });

		for (final IMixedMediaItem item : results) {
			final Item i = makeItem(c, mediaItemObjectId(mlr, item), item);
			if (i != null) c.addItem(i);
		}
		updateContainer(c);

		return new ContentNode(c);
	}

	private ContentNode makeItemNode (final String objectId, final MediaListReference mlr, final IMixedMediaItem mediaItem) {
		final Container parentContainer = makeContainer(localMmdbObjectId(mlr), objectId, mlr.getTitle());
		final Item i = makeItem(parentContainer, objectId, mediaItem);
		if (i == null) return null;
		return new ContentNode(i);
	}

	private Item makeItem (final Container parentContainer, final String objectId, final IMixedMediaItem mediaItem) {
		final File file = new File(mediaItem.getFilepath());
		final MediaFormat format = MediaFormat.identify(file);
		if (format == null) {
			LOG.warn("Unknown media format: {}", file.getAbsolutePath());
			return null;
		}

		final String uri = this.mediaServer.uriForFile(objectId, file);
		final Res res = new Res(formatToMime(format), Long.valueOf(file.length()), uri);
		res.setSize(file.length());

		final int durationSeconds = mediaItem.getDuration();
		if (durationSeconds > 0) res.setDuration(ModelUtil.toTimeString(durationSeconds));

		final Item item;
		switch (format.getContentGroup()) {
			case VIDEO:
				//res.setResolution(resolutionXbyY);
				item = new VideoItem(objectId, parentContainer, mediaItem.getTitle(), "", res);
//				findSubtitles(file, format, item); // TODO
				break;
			case IMAGE:
				//res.setResolution(resolutionXbyY);
				item = new ImageItem(objectId, parentContainer, mediaItem.getTitle(), "", res);
				break;
			case AUDIO:
				item = new AudioItem(objectId, parentContainer, mediaItem.getTitle(), "", res);
				break;
			default:
				throw new IllegalArgumentException();
		}

		final Res artRes = findArtRes(mediaItem);
		if (artRes != null) {
			item.addResource(artRes);
		}

		return item;
	}

	private Res findArtRes (final IMixedMediaItem mediaItem) {
		final File artFile = mediaItem.findCoverArt();
		if (artFile == null) return null;

		final MediaFormat artFormat = MediaFormat.identify(artFile);
		if (artFormat == null) {
			LOG.warn("Ignoring art file of unsupported type: {}", artFile);
			return null;
		}
		final MimeType artMimeType = formatToMime(artFormat);

		final String artUri = this.mediaServer.uriForFile(artFile);
		return new Res(artMimeType, Long.valueOf(artFile.length()), artUri);
	}

	private String localMmdbObjectId (final MediaListReference mlr) {
		final String id = makeLocalMmdbObjectId(mlr);
		this.objectIdToMediaListReference.put(id, mlr);
		return id;
	}

	private String tagObjectId (final MediaListReference mlr, final MediaTag tag) {
		final String id = makeTagObjectId(mlr, tag);
		this.objectIdToTag.put(id, new MlrAnd<MediaTag>(mlr, tag));
		return id;
	}

	private String mediaItemObjectId (final MediaListReference mlr, final IMixedMediaItem item) {
		final String id = makeMediaItemObjectId(mlr, item);
		this.objectIdToMediaItem.put(id, new MlrAnd<IMixedMediaItem>(mlr, item));
		return id;
	}

	private static String safeName (final String s) {
		return s.replaceAll("[^a-zA-Z0-9]", "_");
	}

	private static String makeLocalMmdbObjectId (final MediaListReference mlr) {
		return String.format("ldb-%s-%s", safeName(mlr.getIdentifier()), HashHelper.sha1(mlr.getIdentifier()));
	}

	private static String makeTagObjectId (final MediaListReference mlr, final MediaTag tag) {
		return String.format("tag-%s-%s", safeName(tag.getTag()),
				HashHelper.sha1(String.format("%s-%s-%s", mlr.getIdentifier(), tag.getClassification(), tag.getTag())));
	}

	private static String makeMediaItemObjectId (final MediaListReference mlr, final IMixedMediaItem item) {
		return String.format("item-%s-%s", safeName(item.getTitle()),
				HashHelper.sha1(String.format("%s-%s", mlr.getIdentifier(), item.getFilepath()))); // TODO could use hashcode so same when file moved?
	}

	private static MimeType formatToMime (final MediaFormat format) {
		final String mime = format.getMime();
		return new MimeType(mime.substring(0, mime.indexOf('/')), mime.substring(mime.indexOf('/') + 1));
	}

	private static Container makeContainer (final String parentContainerId, final String id, final String title) {
		final Container c = new Container();
		c.setClazz(new DIDLObject.Class("object.container"));
		c.setId(id);
		c.setParentID(parentContainerId);
		c.setTitle(title);
		c.setRestricted(true);
		c.setWriteStatus(WriteStatus.NOT_WRITABLE);
		updateContainer(c);
		return c;
	}

	private static void updateContainer (final Container container) {
		container.setChildCount(Integer.valueOf(container.getContainers().size() + container.getItems().size()));
	}

}
