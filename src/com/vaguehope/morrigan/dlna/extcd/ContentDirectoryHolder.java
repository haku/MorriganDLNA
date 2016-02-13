package com.vaguehope.morrigan.dlna.extcd;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;

import com.vaguehope.morrigan.model.media.MediaFactory;

public class ContentDirectoryHolder {

	private final ControlPoint controlPoint;
	private final MediaFactory mediaFactory;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final Map<String, RemoteService> contentDirectories = new ConcurrentHashMap<String, RemoteService>();

	public ContentDirectoryHolder (final ControlPoint controlPoint, final MediaFactory mediaFactory) {
		this.controlPoint = controlPoint;
		this.mediaFactory = mediaFactory;
	}

	private void checkAlive () {
		if (!this.alive.get()) throw new IllegalStateException();
	}

	public void dispose () {
		if (this.alive.compareAndSet(true, false)) {
			for (final String id : this.contentDirectories.keySet()) {
				this.mediaFactory.removeExternalDb(id);
			}
			this.contentDirectories.clear();
		}
	}

	public void addContentDirectory (final RemoteDevice device, final RemoteService contentDirectory) {
		checkAlive();
		final String id = idForDevice(device);
		this.contentDirectories.put(id, contentDirectory);
		this.mediaFactory.addExternalDb(new ContentDirectoryDb(id, this.controlPoint, device, contentDirectory));
	}

	public void removeContentDirectory (final RemoteDevice device) {
		checkAlive();
		final String id = idForDevice(device);
		this.contentDirectories.remove(id);
		this.mediaFactory.removeExternalDb(id);
	}

	private static String idForDevice (final RemoteDevice device) {
		return device.getIdentity().getUdn().getIdentifierString();
	}

}
