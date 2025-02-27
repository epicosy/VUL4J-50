/*
 * Copyright 2008-2012 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;

/**
 * Rapport pdf pour les sessions http.
 * @author Emeric Vernat
 */
class PdfSessionInformationsReport extends PdfAbstractTableReport {
	private final List<SessionInformations> sessionsInformations;
	private final boolean displayUser;
	private final DecimalFormat integerFormat = I18N.createIntegerFormat();
	private final DateFormat durationFormat = I18N.createDurationFormat();
	private final DateFormat expiryFormat = I18N.createDateAndTimeFormat();
	private final Font cellFont = PdfFonts.TABLE_CELL.getFont();
	private final Font severeCellFont = PdfFonts.SEVERE_CELL.getFont();
	private final Map<String, Image> imagesByCountry = new HashMap<String, Image>();

	PdfSessionInformationsReport(List<SessionInformations> sessionsInformations, Document document) {
		super(document);
		assert sessionsInformations != null;
		this.sessionsInformations = sessionsInformations;

		this.displayUser = isDisplayUser();
	}

	private boolean isDisplayUser() {
		for (final SessionInformations sessionInformations : sessionsInformations) {
			if (sessionInformations.getRemoteUser() != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	void toPdf() throws IOException, DocumentException {

		if (sessionsInformations.isEmpty()) {
			addToDocument(new Phrase(getString("Aucune_session"), cellFont));
			return;
		}

		writeHeader();
		writeSessions();

		long totalSerializedSize = 0;
		int nbSerializableSessions = 0;
		for (final SessionInformations sessionInformations : sessionsInformations) {
			final int size = sessionInformations.getSerializedSize();
			if (size >= 0) {
				totalSerializedSize += size;
				nbSerializableSessions++;
			}
		}
		final long meanSerializedSize;
		if (nbSerializableSessions > 0) {
			meanSerializedSize = totalSerializedSize / nbSerializableSessions;
		} else {
			meanSerializedSize = -1;
		}
		final Paragraph paragraph = new Paragraph("", cellFont);
		paragraph.add(getFormattedString("nb_sessions", sessionsInformations.size()) + "\n\n"
				+ getFormattedString("taille_moyenne_sessions", meanSerializedSize));
		paragraph.setAlignment(Element.ALIGN_RIGHT);
		addToDocument(paragraph);
	}

	private void writeHeader() throws DocumentException {
		final List<String> headers = createHeaders();
		final int[] relativeWidths = new int[headers.size()];
		Arrays.fill(relativeWidths, 0, headers.size(), 1);
		relativeWidths[0] = 3; // sessionId

		initTable(headers, relativeWidths);
	}

	private List<String> createHeaders() {
		final List<String> headers = new ArrayList<String>();
		headers.add(getString("Session_id"));
		headers.add(getString("Dernier_acces"));
		headers.add(getString("Age"));
		headers.add(getString("Expiration"));
		headers.add(getString("Nb_attributs"));
		headers.add(getString("Serialisable"));
		headers.add(getString("Taille_serialisee"));
		headers.add(getString("Adresse_IP"));
		headers.add(getString("Pays"));
		if (displayUser) {
			headers.add(getString("Utilisateur"));
		}
		return headers;
	}

	private void writeSessions() throws IOException, DocumentException {
		for (final SessionInformations session : sessionsInformations) {
			nextRow();
			writeSession(session);
		}
		addTableToDocument();
	}

	private void writeSession(SessionInformations session) throws IOException, BadElementException {
		final PdfPCell defaultCell = getDefaultCell();
		defaultCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		addCell(session.getId());
		defaultCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		addCell(durationFormat.format(session.getLastAccess()));
		addCell(durationFormat.format(session.getAge()));
		addCell(expiryFormat.format(session.getExpirationDate()));
		addCell(integerFormat.format(session.getAttributeCount()));
		defaultCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		if (session.isSerializable()) {
			addCell(getString("oui"));
		} else {
			final Phrase non = new Phrase(getString("non"), severeCellFont);
			addCell(non);
		}
		defaultCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		addCell(integerFormat.format(session.getSerializedSize()));
		defaultCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		final String remoteAddr = session.getRemoteAddr();
		if (remoteAddr == null) {
			addCell("");
		} else {
			addCell(remoteAddr);
		}
		defaultCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		writeCountry(session);
		if (displayUser) {
			defaultCell.setHorizontalAlignment(Element.ALIGN_LEFT);
			final String remoteUser = session.getRemoteUser();
			if (remoteUser == null) {
				addCell("");
			} else {
				addCell(remoteUser);
			}
		}
	}

	private void writeCountry(SessionInformations session) throws IOException, BadElementException {
		final String country = session.getCountry();
		if (country == null) {
			addCell("");
		} else {
			final Image image = getCountryImage(country);
			if (image == null) {
				addCell(country);
			} else {
				addCell(new Phrase(new Chunk(image, 0, 0)));
			}
		}
	}

	private Image getCountryImage(String country) throws BadElementException, IOException {
		assert country != null;
		Image image = imagesByCountry.get(country);
		if (image == null) {
			final String fileName = "flags/" + country + ".gif";
			if (getClass().getResource(Parameters.getResourcePath(fileName)) == null) {
				return null;
			}
			image = PdfDocumentFactory.getImage(fileName);
			image.scalePercent(40);
			imagesByCountry.put(country, image);
		}
		return image;
	}
}
