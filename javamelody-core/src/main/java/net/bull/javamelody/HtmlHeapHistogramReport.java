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
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import net.bull.javamelody.HeapHistogram.ClassInfo;

/**
 * Partie du rapport html pour l'histogramme mémoire.
 * @author Emeric Vernat
 */
class HtmlHeapHistogramReport extends HtmlAbstractReport {
	private final HeapHistogram heapHistogram;
	private final DecimalFormat integerFormat = I18N.createIntegerFormat();

	HtmlHeapHistogramReport(HeapHistogram heapHistogram, Writer writer) {
		super(writer);
		assert heapHistogram != null;
		this.heapHistogram = heapHistogram;
	}

	@Override
	void toHtml() throws IOException {
		writeLinks();
		writeln("<br/>");

		final String title = getFormattedString("heap_histo_du", I18N.createDateAndTimeFormat()
				.format(heapHistogram.getTime()));
		writeTitle("memory.png", title);
		writeln("<br/><b>#Heap#</b>");
		final String separator = "&nbsp;&nbsp;&nbsp;";
		writeln(separator);
		final List<ClassInfo> heap = heapHistogram.getHeapHistogram();
		final long totalHeapInstances = heapHistogram.getTotalHeapInstances();
		final long totalHeapBytes = heapHistogram.getTotalHeapBytes();
		writeln("#Classes#: " + integerFormat.format(heap.size()) + ',');
		writeln(separator);
		writeln("#Instances#: " + integerFormat.format(totalHeapInstances) + ',');
		writeln(separator);
		writeln("#Kilo-Octets#: " + integerFormat.format(totalHeapBytes / 1024));
		writeClassInfoSummaryAndDetails(heap, totalHeapInstances, totalHeapBytes, true,
				heapHistogram.isSourceDisplayed(), heapHistogram.isDeltaDisplayed());
		final List<ClassInfo> permGen = heapHistogram.getPermGenHistogram();
		if (!permGen.isEmpty()) {
			// avec jrockit, permGen est vide
			writeln("<br/><br/><b>#PermGen#</b>");
			writeln(separator);
			final long totalPermGenInstances = heapHistogram.getTotalPermGenInstances();
			final long totalPermGenBytes = heapHistogram.getTotalPermGenBytes();
			writeln("#Classes#: " + integerFormat.format(permGen.size()) + ',');
			writeln(separator);
			writeln("#Instances#: " + integerFormat.format(totalPermGenInstances) + ',');
			writeln(separator);
			writeln("#Kilo-Octets#: " + integerFormat.format(totalPermGenBytes / 1024));
			writeClassInfoSummaryAndDetails(permGen, totalPermGenInstances, totalPermGenBytes,
					false, false, false);
		}
	}

	private void writeClassInfoSummaryAndDetails(List<ClassInfo> classHistogram,
			long totalInstances, long totalBytes, boolean heap, boolean sourceDisplayed,
			boolean deltaDisplayed) throws IOException {
		final List<ClassInfo> summaryClassHistogram = new ArrayList<ClassInfo>();
		for (final ClassInfo classInfo : classHistogram) {
			if (classInfo.getBytes() * 100 / totalBytes == 0) {
				break;
			}
			summaryClassHistogram.add(classInfo);
		}
		writeClassInfo(summaryClassHistogram, totalInstances, totalBytes, heap, sourceDisplayed,
				deltaDisplayed);

		writeln("<div align='right'>");
		final String id;
		if (heap) {
			id = "detailsHeap";
		} else {
			id = "detailsPermGen";
		}
		writeShowHideLink(id, "#Details#");
		writeln("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
		writeln("</div><div id='" + id + "' style='display:none;'>");
		final List<ClassInfo> detailsClassHistogram = new ArrayList<ClassInfo>();
		for (final ClassInfo classInfo : classHistogram) {
			if (classInfo.getBytes() * 100 / totalBytes == 0) {
				detailsClassHistogram.add(classInfo);
			}
		}
		writeClassInfo(detailsClassHistogram, totalInstances, totalBytes, heap, sourceDisplayed,
				deltaDisplayed);
		writeln("</div>");
	}

	private void writeClassInfo(List<ClassInfo> classHistogram, long totalInstances,
			long totalBytes, boolean heap, boolean sourceDisplayed, boolean deltaDisplayed)
			throws IOException {
		final HtmlTable table = new HtmlTable();
		table.beginTable(getString("histogramme"));
		write("<th>#Classe#</th><th>#Taille#</th><th>#pct_taille#</th>"
				+ (deltaDisplayed ? "<th>#Delta#</th>" : "")
				+ "<th>#Instances#</th><th>#pct_instances#</th>"
				+ (sourceDisplayed ? "<th>#Source#</th>" : ""));
		for (final ClassInfo classInfo : classHistogram) {
			table.nextRow();
			writeClassInfoRow(classInfo, totalInstances, totalBytes, heap, sourceDisplayed,
					deltaDisplayed);
		}
		table.endTable();
	}

	private void writeClassInfoRow(ClassInfo classInfo, long totalInstances, long totalBytes,
			boolean heap, boolean sourceDisplayed, boolean deltaDisplayed) throws IOException {
		write("<td>");
		if (heap) {
			write(classInfo.getName());
		} else {
			// encodage nécessaire dans PermGen pour "<methodKlass>" par exemple
			write(classInfo.getName().replaceAll("[<]", "&lt;").replaceAll("[>]", "&gt;"));
		}
		final String nextColumnAlignRight = "</td><td align='right'>";
		write(nextColumnAlignRight);
		final long bytes = classInfo.getBytes();
		final long instancesCount = classInfo.getInstancesCount();
		write(integerFormat.format(bytes / 1024));
		write(nextColumnAlignRight);
		write(integerFormat.format(bytes * 100 / totalBytes));
		if (deltaDisplayed) {
			write(nextColumnAlignRight);
			if (classInfo.getBytesDelta() > 0) {
				write("+");
			}
			write(integerFormat.format(classInfo.getBytesDelta() / 1024));
		}
		write(nextColumnAlignRight);
		write(integerFormat.format(instancesCount));
		write(nextColumnAlignRight);
		write(integerFormat.format(instancesCount * 100 / totalInstances));
		write("</td>");
		if (sourceDisplayed) {
			write("<td>");
			final String source = classInfo.getSource();
			if (source == null) {
				write("&nbsp;");
			} else {
				write(source);
			}
			write("</td>");
		}
	}

	private void writeLinks() throws IOException {
		writeln("<div class='noPrint'>");
		writeln("<a href='javascript:history.back()'><img src='?resource=action_back.png' alt='#Retour#'/> #Retour#</a>");
		final String separator = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		writeln(separator);
		writeln("<a href='?part=heaphisto'><img src='?resource=action_refresh.png' alt='#Actualiser#'/> #Actualiser#</a>");
		if (isPdfEnabled()) {
			writeln(separator);
			write("<a href='?part=heaphisto&amp;format=pdf' title='#afficher_PDF#'>");
			write("<img src='?resource=pdf.png' alt='#PDF#'/> #PDF#</a>");
		}
		writeln(separator);
		if (Action.GC_ENABLED) {
			writeln("<a href='?part=heaphisto&amp;action=gc' onclick=\"javascript:return confirm('"
					+ getStringForJavascript("confirm_ramasse_miette") + "');\">");
			writeln("<img src='?resource=broom.png' width='16' height='16' alt='#ramasse_miette#' /> #ramasse_miette#</a>");
			writeln(separator);
		} else {
			writeln("<a href='' onclick=\"javascript:alert('"
					+ getStringForJavascript("ramasse_miette_desactive") + "');return false;\">");
			writeln("<img src='?resource=broom.png' width='16' height='16' alt='#ramasse_miette#' /> #ramasse_miette#</a>");
			writeln(separator);
		}
		if (Action.HEAP_DUMP_ENABLED) {
			// si serveur de collecte, on suppose que si la version de java est la bonne
			// sur le serveur de collecte, ce sera la bonne aussi sur les serveurs
			// des webapps monitorées
			writeln("<a href='?part=heaphisto&amp;action=heap_dump' onclick=\"javascript:return confirm('"
					+ getStringForJavascript("confirm_heap_dump") + "');\">");
			writeln("<img src='?resource=heapdump.png' width='16' height='16' alt='#heap_dump#' /> #heap_dump#</a>");
			writeln(separator);
		}
		writeln("</div>");
	}
}
