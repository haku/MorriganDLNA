package com.vaguehope.morrigan.dlna.httpserver;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaServer {

	private static final int HTTP_START_PORT = 29085;
	private static final Logger LOG = LoggerFactory.getLogger(MediaServer.class);

	private final Server server;
	private final String bindAddress;

	public MediaServer (final FileLocator fileLocator, final InetAddress bindAddress) {
		if (bindAddress == null) throw new IllegalArgumentException("bindAddress must not be null.");
		this.bindAddress = bindAddress.getHostAddress();
		this.server = makeContentServer(fileLocator, this.bindAddress);
	}

	public void start () {
		try {
			IOException bindFail = null;
			for (int i = 0; i < 10; i++) {
				try {
					this.server.getConnectors()[0].setPort(HTTP_START_PORT + i);
					this.server.start();
					bindFail = null;
					break;
				}
				catch (final BindException e) {
					LOG.warn("Failed to bind to port {} ({}), trying a higher port...", this.server.getConnectors()[0].getPort(), e.toString());
					bindFail = e;
					this.server.stop();
				}
			}
			if (bindFail != null) {
				LOG.error("Abandonded search for port to bind to.");
				throw bindFail;
			}
			LOG.info("External URL: {}", getExternalHttpUrl());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void dispose () {
		try {
			this.server.stop();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public String uriForId (final String id) {
		return String.format("%s/%s", getExternalHttpUrl(), id);
	}

	private String getExternalHttpUrl () {
		return "http://" + this.bindAddress + ":" + this.server.getConnectors()[0].getLocalPort();
	}

	private static Server makeContentServer (final FileLocator fileLocator, final String bindAddress) {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new ContentServlet(fileLocator)), "/");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });

		final Server server = new Server();
		server.setHandler(handler);
		server.addConnector(createHttpConnector(bindAddress, 0));
		return server;
	}

	private static SelectChannelConnector createHttpConnector (final String hostAddress, final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setHost(hostAddress);
		connector.setPort(port);
		return connector;
	}

}
