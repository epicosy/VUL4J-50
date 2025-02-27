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

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Properties;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;

/**
 * Enumération des fontes pour les documents pdf.
 * @author Emeric Vernat
 */
enum PdfFonts {
	NORMAL(getFont(8f, Font.NORMAL)),
	BOLD(getFont(8f, Font.BOLD)),
	PARAGRAPH_TITLE(getFont(10f, Font.BOLD)),
	TABLE_CELL(getFont(5.5f, Font.NORMAL)),
	BOLD_CELL(getFont(5.5f, Font.BOLD)),
	BLUE(getFont(5.5f, Font.NORMAL)),
	INFO_CELL(getFont(5.5f, Font.NORMAL)),
	WARNING_CELL(getFont(5.5f, Font.BOLD)),
	SEVERE_CELL(getFont(5.5f, Font.BOLD)),
	TABLE_HEADER(getFont(5.5f, Font.BOLD));

	static {
		BLUE.font.setColor(Color.BLUE);
		INFO_CELL.font.setColor(Color.GREEN);
		WARNING_CELL.font.setColor(Color.ORANGE);
		SEVERE_CELL.font.setColor(Color.RED);
	}

	private final transient Font font;
	private transient Font chineseFont;

	PdfFonts(Font font) {
		this.font = font;
	}

	Font getFont() {
		if (Locale.CHINESE.getLanguage().equals(I18N.getResourceBundle().getLocale().getLanguage())) {
			return getChineseFont();
		}
		return font;
	}

	private static Font getFont(float size, int style) {
		return FontFactory.getFont(FontFactory.HELVETICA, size, style);
	}

	private Font getChineseFont() {
		if (chineseFont == null) {
			try {
				BaseFont bfChinese;
				try {
					bfChinese = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H",
							BaseFont.NOT_EMBEDDED);
				} catch (final DocumentException e) {
					// now CJKFont.propertiesLoaded==true, load properties renamed (cf issue 258)
					loadCJKFonts();
					bfChinese = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H",
							BaseFont.NOT_EMBEDDED);
				}
				chineseFont = new Font(bfChinese, font.getSize(), font.getStyle());
			} catch (final DocumentException e) {
				throw new IllegalStateException(e);
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return chineseFont;
	}

	/**
	 * Chargement des cjkfonts et cjkencodings. <br/>
	 * Les fichiers cjkfonts.properties et cjkencoding.properties ont été renommés <br/>
	 * pour que FlyingSaucer ne croit pas qu'iTextAsian.jar est présent (issue 258). <br/>
	 * Cette méthode se charge de charger quand même les fichiers renommés pour la fonte en langue chinoise.
	 */
	private static void loadCJKFonts() {
		try {
			final Class<?> cjkFontClass = Class.forName("com.lowagie.text.pdf.CJKFont");
			final Field cjkFontsField = cjkFontClass.getDeclaredField("cjkFonts");
			final Field cjkEncodingsField = cjkFontClass.getDeclaredField("cjkEncodings");
			cjkFontsField.setAccessible(true);
			cjkEncodingsField.setAccessible(true);
			final Properties cjkFonts = (Properties) cjkFontsField.get(null);
			final Properties cjkEncodings = (Properties) cjkEncodingsField.get(null);

			if (cjkFonts.isEmpty()) {
				final InputStream is = BaseFont.getResourceStream(BaseFont.RESOURCE_PATH
						+ "cjkfonts.properties.renamedForIssue258");
				try {
					cjkFonts.load(is);
				} finally {
					is.close();
				}
			}
			if (cjkEncodings.isEmpty()) {
				final InputStream is = BaseFont.getResourceStream(BaseFont.RESOURCE_PATH
						+ "cjkencodings.properties.renamedForIssue258");
				try {
					cjkEncodings.load(is);
				} finally {
					is.close();
				}
			}
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
