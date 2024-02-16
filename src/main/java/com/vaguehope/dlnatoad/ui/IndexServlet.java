package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServlet;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.StringHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class IndexServlet extends HttpServlet {

	private static final ThreadSafeDateFormatter RFC1123_DATE = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	private static final long serialVersionUID = -8907271726001369264L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final DbCache dbCache;
	private final ContentServlet contentServlet;

	public IndexServlet (final ServletCommon servletCommon, final ContentTree contentTree, final DbCache dbCache, final ContentServlet contentServlet) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.dbCache = dbCache;
		this.contentServlet = contentServlet;
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if ("PROPFIND".equals(req.getMethod())) {
			doPropfind(req, resp);
			return;
		}
		super.service(req, resp);
	}

	@Override
	protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setHeader("Allow", "GET,HEAD,PROPFIND");
		resp.setHeader("DAV", "1");
	}

	private static String idFromPath(final HttpServletRequest req) {
		return ServletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode contentNode = this.contentTree.getNode(id);
		// ContentServlet does extra parsing and Index only handles directories anyway.
		if (contentNode == null) {
			this.contentServlet.service(req, resp);
			return;
		}

		final String username = ReqAttr.USERNAME.get(req);
		if (!contentNode.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		printDir(req, resp, contentNode, username);
	}

	@SuppressWarnings("resource")
	private void printDir (final HttpServletRequest req, final HttpServletResponse resp, final ContentNode contentNode, final String username) throws IOException {
		ServletCommon.setHtmlContentType(resp);
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, contentNode.getTitle());
		this.servletCommon.printLinkRow(req, w);

		final List<ContentNode> nodesUserHasAuth = contentNode.nodesUserHasAuth(username);
		printTitle(contentNode, w, nodesUserHasAuth);
		printLinksRow(contentNode, w);

		final String sortRaw = ServletCommon.readParamWithDefault(req, resp, "sort", "");
		if (sortRaw == null) return;
		final Order sort;
		if ("modified".equalsIgnoreCase(sortRaw)) {
			sort = ContentItem.Order.MODIFIED_DESC;
		}
		else {
			sort = null;
		}

		this.servletCommon.printNodeSubNodesAndItems(w, contentNode, nodesUserHasAuth, sort);

		printTopTags(w, contentNode, username);
		this.servletCommon.appendDebugFooter(req, w, "");
		this.servletCommon.endBody(w);
	}

	private static void printTitle(final ContentNode contentNode, final PrintWriter w, final List<ContentNode> nodesUserHasAuth) {
		w.print("<h3>");
		w.print(StringEscapeUtils.escapeHtml4(contentNode.getTitle()));

		final int nodeCount = nodesUserHasAuth.size();
		final int itemCount = contentNode.getItemCount();
		w.print(" (");
		if (nodeCount > 0) {
			w.print(nodeCount);
			w.print(" dirs");
		}
		if (itemCount > 0) {
			if (nodeCount > 0) w.print(", ");
			w.print(itemCount);
			w.print(" items");
		}
		w.print(")");

		if (contentNode.getParentId() != null && !ContentGroup.ROOT.getId().equals(contentNode.getId())) {
			w.print(" <a id=\"up\" href=\"");
			w.print(contentNode.getParentId());
			w.print("\">up</a>");
		}
		w.print("</h3>");
	}

	private static void printLinksRow(final ContentNode node, final PrintWriter w) {
		w.println("<div class=\"list_link_row\">");
		w.println("<span><a href=\"?sort=modified\">Sort by Modified</a></span>");

		if (node.getItemCount() > 0) {
			final long[] size = { 0 };
			node.withEachItem(i -> {size[0] += i.getFileLength();});

			w.print("<span><a href=\"");
			w.print(C.DIR_PATH_PREFIX);
			w.print(node.getId());
			w.print(".zip\" download=\"");
			w.print(node.getFile() != null ? node.getFile().getName() : node.getTitle());
			w.print(".zip\">As .zip (");
			w.print(FileHelper.readableFileSize(size[0]));
			w.println(")</a></span>");
		}

		w.println("</div>");
	}

	private void printTopTags(final PrintWriter w, final ContentNode contentNode, final String username) throws IOException {
		if (this.dbCache == null) return;

		final File dir = contentNode.getFile();
		final String pathPrefix = dir != null ? dir.getAbsolutePath() : null;
		if (pathPrefix == null && !ContentGroup.ROOT.getId().equals(contentNode.getId())) return;

		final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
		try {
			final List<TagFrequency> topTags = this.dbCache.getTopTags(authIds, pathPrefix);
			if (topTags.size() < 1) return;
			w.println("<h3>Tags</h3>");
			this.servletCommon.printRowOfTags(w, "", topTags);
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	// http://www.webdav.org/specs/rfc4918.html
	protected void doPropfind(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode node = this.contentTree.getNode(id);
		final ContentItem item = node != null ? null : this.contentTree.getItem(id);
		final String username = ReqAttr.USERNAME.get(req);

		if (node == null && item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}

		if (node != null && !node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		final String depth = req.getHeader("Depth");
		if (depth == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing depth header.");
			return;
		}
		if (!"0".equals(depth) && !"1".equals(depth)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unsupported depth: " + depth);
			return;
		}

		resp.setStatus(207);
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/xml");

		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		w.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
		w.println("<D:multistatus xmlns:D=\"DAV:\">");

		if (node != null) {
			appendPropfindNode(req, username, w, node, false);
			if ("1".equals(depth)) {
				node.withEachNode(n -> appendPropfindNode(req, username, w, n, true));
				node.withEachItem(i -> appendPropfindItem(req, w, i, true));
			}
		}
		else {
			appendPropfindItem(req, w, item, false);
		}
		w.println("</D:multistatus>");
	}

	private static void appendPropfindNode(final HttpServletRequest req, final String username, final PrintWriter w, final ContentNode node, final boolean appendIdToPath) {
		if (!node.isUserAuth(username)) return;

		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + node.getId();
		}

		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype><D:collection/></D:resourcetype>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(node.getTitle()));
		w.println("</D:displayname>");

		final long lastModified = node.getLastModified();
		if (lastModified > 0) {
			w.print("<D:getlastmodified>");
			w.print(RFC1123_DATE.get().format(new Date(lastModified)));
			w.print("</D:getlastmodified>");
		}

		w.println("</D:prop>");
		w.println("<D:status>HTTP/1.1 200 OK</D:status>");
		w.println("</D:propstat>");
		w.println("</D:response>");
	}

	private static void appendPropfindItem(final HttpServletRequest req, final PrintWriter w, final ContentItem item, final boolean appendIdToPath) {
		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + item.getId();
		}

		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype/>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(item.getTitle()));
		w.println("</D:displayname>");

		if (item.getFormat() != null) {
			w.print("<D:getcontenttype>");
			w.print(item.getFormat().getMime());
			w.println("</D:getcontenttype>");
		}

		final long fileLength = item.getFileLength();
		if (fileLength > 0) {
			w.print("<D:getcontentlength>");
			w.print(fileLength);
			w.println("</D:getcontentlength>");
		}

		final long lastModified = item.getLastModified();
		if (lastModified > 0) {
			w.print("<D:getlastmodified>");
			w.print(RFC1123_DATE.get().format(new Date(lastModified)));
			w.print("</D:getlastmodified>");
		}

		w.println("</D:prop>");
		w.println("<D:status>HTTP/1.1 200 OK</D:status>");
		w.println("</D:propstat>");
		w.println("</D:response>");
	}

}
