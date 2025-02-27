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
package net.bull.javamelody.swing;

import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.swing.SwingConstants;
import javax.swing.text.Document;

/**
 * Champs de saisie d'une date (type java.util.Date).
 * @author Emeric Vernat
 */
public class MDateField extends MTextField<Date> { // NOPMD
	private static final long serialVersionUID = 1L;

	/**
	 * Constructeur.
	 * @see #createDefaultModel
	 */
	public MDateField() {
		super();
		setHorizontalAlignment(SwingConstants.RIGHT);
	}

	@Override
	protected Document createDefaultModel() {
		return new MDateDocument();
	}

	/** {@inheritDoc} */
	@Override
	public Date getValue() {
		final String text = super.getText();
		if (text == null || text.length() == 0) {
			return null;
		}
		// note : L'année 0 (et 000) sur un seul chiffre est interprétée par java comme 0001 et non comme 2000.
		// (Si dateFormat était lenient false, il y aurait même une erreur).
		final Calendar calendar = Calendar.getInstance();
		try {
			calendar.setTime(getDateFormat().parse(text));
		} catch (final ParseException e) {
			try {
				calendar.setTime(MDateDocument.getAlternateDateFormat().parse(text));
			} catch (final ParseException e2) {
				beep();
				return null;
			}
		}

		// l'heure est soit 0h soit 23h59 selon endOfDay
		// (on ne garde pas l'heure courante mais seulement le jour)
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();
	}

	/** {@inheritDoc} */
	@Override
	public void setValue(final Date newDate) {
		if (newDate != null) {
			super.setText(MDateDocument.getDisplayDateFormat().format(newDate));
		} else {
			super.setText(null);
		}
	}

	/**
	 * Définit la valeur de la propriété date avec la date du jour.
	 * @see #setValue
	 */
	public void setNow() {
		setValue(new Date());
	}

	/**
	 * Retourne la valeur de la propriété dateFormat interne.
	 * @return SimpleDateFormat
	 * @see #setDateFormat
	 */
	protected static SimpleDateFormat getDateFormat() {
		return MDateDocument.getDateFormat();
	}

	/**
	 * Définit la valeur de la propriété dateFormat interne.
	 * @param newDateFormat
	 *           SimpleDateFormat
	 * @see #getDateFormat
	 */
	protected static void setDateFormat(final SimpleDateFormat newDateFormat) {
		MDateDocument.setDateFormat(newDateFormat);
	}

	// CHECKSTYLE:OFF
	@Override
	// CHECKSTYLE:ON
	protected void keyEvent(final KeyEvent event) { // NOPMD
		// Ici, la touche Entrée valide la saisie
		// ou remplit le champ avec la date du jour si il est vide.
		// Et la flèche Haut incrémente la valeur de 1 jour,
		// et la flèche Bas la décrémente.
		final int keyCode = event.getKeyCode();
		if (isEditable() && event.getID() == KeyEvent.KEY_PRESSED && keyCode == KeyEvent.VK_ENTER
				&& (super.getText() == null || super.getText().length() == 0)) {
			setNow();
			event.consume();
		} else if (isEditable() && event.getID() == KeyEvent.KEY_PRESSED
				&& (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN)
				&& super.getText() != null && super.getText().length() != 0) {
			final Date date = getValue();
			if (date != null) {
				final Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				calendar.add(Calendar.DAY_OF_YEAR, keyCode == KeyEvent.VK_UP ? 1 : -1);
				setValue(calendar.getTime());
				event.consume();
			}
		} else {
			super.keyEvent(event);
		}
	}
}
