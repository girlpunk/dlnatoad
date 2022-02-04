package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteDeviceIdentity;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;

public class UpnpServlet extends HttpServlet {

	private static final long serialVersionUID = 8519141492915099699L;

	private final ServletCommon servletCommon;
	private final UpnpService upnpService;

	public UpnpServlet(final ServletCommon servletCommon, final UpnpService upnpService) {
		this.servletCommon = servletCommon;
		this.upnpService = upnpService;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		ServletCommon.setHtmlContentType(resp);
		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, "UPNP");
		this.servletCommon.printLinkRow(req, w);

		w.println("<h3>Local Devices</h3>");
		printDevices(w, this.upnpService.getRegistry().getLocalDevices());
		w.println("<h3>Remote Devices</h3>");
		printDevices(w, this.upnpService.getRegistry().getRemoteDevices());

		this.servletCommon.endBody(w);
	}

	private static void printDevices(final PrintWriter w, final Collection<? extends Device<?, ?, ?>> devices) {
		final List<Device<?, ?, ?>> sorted = new ArrayList<>(devices);
		Collections.sort(sorted, DeviceSorter.INSTANCE);

		w.println("<ul>");
		for (final Device<?, ?, ?> d : devices) {
			printDevice(w, d);
		}
		w.println("</ul>");
	}

	private static void printDevice(final PrintWriter w, final Device<?, ?, ?> d) {
		final RemoteDevice rd = d instanceof RemoteDevice ? (RemoteDevice) d : null;

		String title = d.getDetails().getFriendlyName();
		if (StringUtils.isBlank(title)) title = d.getDisplayString();
		w.println("<li>" + title);
		w.println("<br>UDN: " + d.getIdentity().getUdn());
		w.println("<br>Max Age: " + d.getIdentity().getMaxAgeSeconds() + " seconds");
		w.println("<br>Type: " + d.getType());
		w.println("<br>Version: " + d.getVersion().getMajor() + "." + d.getVersion().getMinor());

		final DeviceIdentity identity = d.getIdentity();
		if (identity instanceof RemoteDeviceIdentity) {
			final RemoteDeviceIdentity rdi = (RemoteDeviceIdentity) identity;
			w.print("<br>Descriptor URL: ");
			printUrl(w, rdi.getDescriptorURL());
			w.println("<br>Discovered On: " + rdi.getDiscoveredOnLocalAddress());
		}

		if (d.getDetails().getPresentationURI() != null) {
			w.print("<br>Presentation URI: ");
			printUri(w, d.getDetails().getPresentationURI());
		}
		if (d.getDetails().getBaseURL() != null) {
			w.print("<br>Base URL: ");
			printUrl(w, d.getDetails().getBaseURL());
		}
		if (d.getDetails().getDlnaCaps() != null) {
			w.println("<br>Dlna Caps: " + d.getDetails().getDlnaCaps());
		}

		final Service<?, ?>[] services = d.getServices();
		if (services != null && services.length > 0) {
			w.println("<br>Services:<ul>");
			for (final Service<?, ?> s : services) {
				w.println("<li>" + s.getServiceId());
				w.println("<br>Type: " + s.getServiceType());
				w.println("<br>Actions: ");
				for (final Action<?> a : s.getActions()) {
					w.print(a.getName());
					w.print(" ");
				}
				w.println("</li>");

				if (s instanceof RemoteService) {
					final RemoteService rs = (RemoteService) s;
					w.print("Descriptor URI: ");
					printMaybeNormalizedUri(w, rd, rs.getDescriptorURI());
					w.print("<br>Control URI: ");
					printMaybeNormalizedUri(w, rd, rs.getControlURI());
					w.print("<br>Event Subscription URI: ");
					printMaybeNormalizedUri(w, rd, rs.getEventSubscriptionURI());
				}
			}
			w.println("</ul>");
		}

		final Device<?, ?, ?>[] eDev = d.getEmbeddedDevices();
		if (eDev != null && eDev.length > 0) {
			for (final Device<?, ?, ?> ed : eDev) {
				w.println("Embedded Devices:<ul>");
				printDevice(w, ed);
				w.println("</ul>");
			}
		}

		w.println("</li>");
	}

	private static void printMaybeNormalizedUri(final PrintWriter w, final RemoteDevice rd, final URI uri) {
		if (rd != null) {
			printUrl(w, rd.normalizeURI(uri));
		}
		else {
			printUri(w, uri);
		}
	}

	private static void printUri(final PrintWriter w, final URI u) {
		final boolean link = StringUtils.isNotBlank(u.getScheme());
		if (link) {
			w.println("<a href=\"" + u + "\">");
		}
		w.println(u);
		if (link) {
			w.println("</a>");
		}
	}

	private static void printUrl(final PrintWriter w, final URL u) {
		final boolean link = StringUtils.isNotBlank(u.getProtocol());
		if (link) {
			w.println("<a href=\"" + u + "\">");
		}
		w.println(u);
		if (link) {
			w.println("</a>");
		}
	}

	private enum DeviceSorter implements Comparator<Device<?, ?, ?>> {
		INSTANCE;

		@Override
		public int compare(final Device<?, ?, ?> o1, final Device<?, ?, ?> o2) {
			final String s1 = o1.getIdentity().getUdn().getIdentifierString();
			final String s2 = o2.getIdentity().getUdn().getIdentifierString();
			return s1.compareTo(s2);
		}
	}

}
