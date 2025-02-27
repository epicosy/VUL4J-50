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
package net.bull.javamelody.swing.table;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPopupMenu;

import net.bull.javamelody.swing.MMenuItem;
import net.bull.javamelody.swing.print.MCsvLocalWriter;
import net.bull.javamelody.swing.print.MHtmlWriter;
import net.bull.javamelody.swing.print.MJavaPrinter;
import net.bull.javamelody.swing.print.MJsonWriter;
import net.bull.javamelody.swing.print.MPdfWriter;
import net.bull.javamelody.swing.print.MPrinter;
import net.bull.javamelody.swing.print.MRtfWriter;
import net.bull.javamelody.swing.print.MXmlWriter;
import net.bull.javamelody.swing.util.MSwingUtilities;

import org.apache.log4j.Logger;

/**
 * Popup menu des tables.
 *
 * @author Emeric Vernat
 */
class TablePopupMenu extends JPopupMenu {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructeur.
	 * @param table MBasicTable
	 */
	TablePopupMenu(final MBasicTable table) {
		super();

		final List<MPrinter> printers = getPrinters();
		for (final MPrinter printer : printers) {
			final MMenuItem menuItem = new MMenuItem(printer.getName(), printer.getIcon());
			add(menuItem);
			menuItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent event) {
					try {
						printer.print(table);
					} catch (final IOException e) {
						MSwingUtilities.showException(e);
					}
				}
			});
		}
	}

	/**
	 * Retourne la liste des objets d'export / impression.
	 *
	 * @return List
	 */
	private List<MPrinter> getPrinters() {
		// ne sont pas inclus le printer "Clipboard" qui est utilisé directement avec Ctrl+C, les printers PDF/RTF paysages et le printer CSV US
		final List<MPrinter> printers = new ArrayList<>();
		printers.add(new MCsvLocalWriter());
		try {
			Class.forName("com.lowagie.text.Document");
			printers.add(new MPdfWriter());
		} catch (final ClassNotFoundException e) {
			// l'export PDF ne sera pas disponible dans cette application
			Logger.getLogger(TablePopupMenu.class).debug("Export PDF non disponible sans iText");
		}
		try {
			Class.forName("com.lowagie.text.rtf.RtfWriter2");
			printers.add(new MRtfWriter());
		} catch (final ClassNotFoundException e) {
			// l'export RTF ne sera pas disponible dans cette application
			Logger.getLogger(TablePopupMenu.class)
					.debug("Export RTF non disponible sans iText-RTF");
		}
		printers.add(new MHtmlWriter());
		try {
			Class.forName("com.thoughtworks.xstream.XStream");
			printers.add(new MXmlWriter());
			printers.add(new MJsonWriter());
		} catch (final ClassNotFoundException e) {
			// l'export XML et JSON ne seront pas disponibles dans cette application
			Logger.getLogger(TablePopupMenu.class).debug(
					"Exports XML et JSON non disponibles sans XStream et XPP3");
		}
		printers.add(new MJavaPrinter());
		return printers;
	}
}
