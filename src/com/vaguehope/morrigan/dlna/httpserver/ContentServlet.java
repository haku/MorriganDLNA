package com.vaguehope.morrigan.dlna.httpserver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

public final class ContentServlet extends DefaultServlet {

	private static final long serialVersionUID = -4819786280597656455L;
	private final Map<String, File> files;

	public ContentServlet (final Map<String, File> files) {
		this.files = files;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.doGet(req, resp);
		}
		finally {
			final String ranges = join(req.getHeaders(HttpHeaders.RANGE), ",");
			if (ranges != null) {
				System.err.println("request: " + /* resp.getStatus() + " " + */ req.getRequestURI() + " (r:" + ranges + ")");
			}
			else {
				System.err.println("request: " + /* resp.getStatus() + " " + */ req.getRequestURI());
			}
		}
	}

	@Override
	public Resource getResource (final String pathInContext) {
		try {
			final String id = URLDecoder.decode(pathInContext.replaceFirst("/", ""), "UTF-8");
			final File file = this.files.get(id);
			if (file != null) return Resource.newResource(file.toURI());
		}
		catch (final MalformedURLException e) {
			System.err.println("Failed to map resource '" + pathInContext + "': " + e.getMessage());
		}
		catch (final IOException e) {
			System.err.println("Failed to serve resource '" + pathInContext + "': " + e.getMessage());
		}
		return null;
	}

	private static String join (final Enumeration<String> en, final String join) {
		if (en == null || !en.hasMoreElements()) return null;
		final StringBuilder s = new StringBuilder(en.nextElement());
		while(en.hasMoreElements()) {
			s.append(join).append(en.nextElement());
		}
		return s.toString();
	}

}